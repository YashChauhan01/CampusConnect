package edu.campusconnect.matching;

import edu.campusconnect.common.ApiException;
import edu.campusconnect.common.RateLimiter;
import edu.campusconnect.presence.Presence;
import edu.campusconnect.presence.PresenceRepository;
import edu.campusconnect.presence.PresenceService.PresenceChanged;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import edu.campusconnect.student.Subject;
import edu.campusconnect.student.SubjectRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Real-time peer matchmaking. A <em>round</em> collects everyone who is available on campus, builds the weighted
 * compatibility graph and pairs students with a maximum-weight matching. Pairings are proposals: both students
 * must accept before the match is confirmed.
 */
@Service
public class MatchService {

    private static final Logger log = LoggerFactory.getLogger(MatchService.class);
    private static final long ROUND_LOCK_ID = 7_270_001L;
    private static final int HISTORY_SIZE = 20;
    private static final int MAX_CANDIDATE_SUGGESTIONS = 20;

    public record RoundSummary(UUID roundId, int poolSize, int edgeCount, int pairs, double totalWeight, double durationMs) {}

    public record Partner(UUID id, String name, String zone, String bio, String email) {}

    public record Breakdown(double knowledge, double reciprocity, double breadth, double proximity) {}

    public record MatchView(UUID id, Match.Status status, int score, Breakdown breakdown, Partner partner,
                            List<String> sharedSubjects, boolean youAccepted, boolean partnerAccepted,
                            Instant createdAt, Instant expiresAt) {}

    public record Suggestion(UUID studentId, String name, String zone, int score, List<String> sharedSubjects,
                             Breakdown breakdown) {}

    private final MatchRepository matches;
    private final MatchRoundRepository rounds;
    private final PresenceRepository presences;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final CandidateLoader loader;
    private final MatchingEngine engine;
    private final CompatibilityScorer scorer;
    private final MatchingProperties props;
    private final RateLimiter limiter;
    private final ApplicationEventPublisher events;
    private final EntityManager em;
    private final Clock clock;

    public MatchService(MatchRepository matches, MatchRoundRepository rounds, PresenceRepository presences,
                        StudentRepository students, SubjectRepository subjects, CandidateLoader loader,
                        MatchingEngine engine, CompatibilityScorer scorer, MatchingProperties props,
                        RateLimiter limiter, ApplicationEventPublisher events, EntityManager em, Clock clock) {
        this.matches = matches;
        this.rounds = rounds;
        this.presences = presences;
        this.students = students;
        this.subjects = subjects;
        this.loader = loader;
        this.engine = engine;
        this.scorer = scorer;
        this.props = props;
        this.limiter = limiter;
        this.events = events;
        this.em = em;
        this.clock = clock;
    }

    // ---- matching rounds ------------------------------------------------------------------------------------------

    /**
     * Runs one matching round. Returns empty when another instance is already running a round (guarded by a
     * PostgreSQL advisory lock) or when fewer than two students are available.
     */
    @Transactional
    public Optional<RoundSummary> runRound() {
        if (!tryAcquireRoundLock()) {
            log.debug("Matching round skipped: another round is in progress");
            return Optional.empty();
        }
        Instant now = clock.instant();
        housekeeping(now);

        Set<UUID> busy = new HashSet<>();
        for (Match m : matches.findAllLive()) {
            busy.add(m.getStudentA());
            busy.add(m.getStudentB());
        }
        List<Presence> pool = presences.findAvailable(now).stream()
                .filter(p -> !busy.contains(p.getStudentId()))
                .limit(props.maxPoolSize())
                .toList();
        if (pool.size() < 2) {
            return Optional.empty();
        }

        Set<UUID> poolIds = pool.stream().map(Presence::getStudentId).collect(Collectors.toSet());
        Set<String> declined = matches.findDeclinedSince(now.minus(props.declineCooldown()), poolIds).stream()
                .map(m -> pairKey(m.getStudentA(), m.getStudentB()))
                .collect(Collectors.toSet());
        BiPredicate<UUID, UUID> excluded = (a, b) -> declined.contains(pairKey(a, b));

        MatchingEngine.Result result = engine.match(loader.load(pool), excluded);
        double durationMs = result.nanos() / 1_000_000.0;
        MatchRound round = rounds.save(new MatchRound(now, result.poolSize(), result.edgeCount(),
                result.pairs().size(), result.totalWeight(), durationMs));

        for (MatchingEngine.Pair pair : result.pairs()) {
            Match match = matches.save(new Match(round.getId(), pair.a().studentId(), pair.b().studentId(),
                    pair.score(), now, now.plus(props.proposalTtl())));
            events.publishEvent(new MatchEvent.Proposed(match.getId(), match.getStudentA(), match.getStudentB()));
        }
        log.info("Matching round: pool={} edges={} pairs={} weight={} in {} ms", result.poolSize(),
                result.edgeCount(), result.pairs().size(), String.format("%.3f", result.totalWeight()),
                String.format("%.1f", durationMs));
        return Optional.of(new RoundSummary(round.getId(), result.poolSize(), result.edgeCount(),
                result.pairs().size(), result.totalWeight(), durationMs));
    }

    /** Round requested by a student pressing "find me a partner"; throttled so it cannot be used to hammer the solver. */
    @Transactional
    public Optional<MatchView> requestRound(UUID studentId) {
        try {
            limiter.check("matching:round", 1, props.onDemandCooldown());
            runRound();
        } catch (ApiException throttled) {
            log.debug("On-demand round throttled");
        }
        return current(studentId);
    }

    // ---- student actions ------------------------------------------------------------------------------------------

    @Transactional
    public MatchView accept(UUID studentId, UUID matchId) {
        Match match = lockFor(studentId, matchId);
        Instant now = clock.instant();
        if (match.getStatus() != Match.Status.PROPOSED || !match.getExpiresAt().isAfter(now)) {
            throw ApiException.conflict("This match proposal is no longer open");
        }
        if (match.hasAccepted(studentId)) {
            return view(match, studentId);
        }
        match.markAccepted(studentId);
        UUID partner = match.partnerOf(studentId);
        if (match.isAcceptedA() && match.isAcceptedB()) {
            match.setStatus(Match.Status.ACCEPTED);
            match.setDecidedAt(now);
            match.setExpiresAt(now.plus(props.acceptedTtl()));
            events.publishEvent(new MatchEvent.Confirmed(match.getId(), match.getStudentA(), match.getStudentB()));
        } else {
            events.publishEvent(new MatchEvent.PartnerAccepted(match.getId(), studentId, partner));
        }
        return view(match, studentId);
    }

    /** Declining frees both students for the next round and keeps this pair apart for the cool-down period. */
    @Transactional
    public MatchView decline(UUID studentId, UUID matchId) {
        Match match = lockFor(studentId, matchId);
        if (match.getStatus() != Match.Status.PROPOSED) {
            throw ApiException.conflict("Only open proposals can be declined");
        }
        match.setStatus(Match.Status.DECLINED);
        match.setDeclinedBy(studentId);
        match.setDecidedAt(clock.instant());
        events.publishEvent(new MatchEvent.Declined(match.getId(), studentId, match.partnerOf(studentId)));
        return view(match, studentId);
    }

    /** Ends a confirmed match (e.g. the study session is over) so both students can be matched again. */
    @Transactional
    public MatchView complete(UUID studentId, UUID matchId) {
        Match match = lockFor(studentId, matchId);
        if (match.getStatus() != Match.Status.ACCEPTED) {
            throw ApiException.conflict("Only confirmed matches can be completed");
        }
        match.setStatus(Match.Status.COMPLETED);
        match.setDecidedAt(clock.instant());
        events.publishEvent(new MatchEvent.Completed(match.getId(), match.getStudentA(), match.getStudentB()));
        return view(match, studentId);
    }

    // ---- queries --------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<MatchView> current(UUID studentId) {
        return matches.findLiveFor(studentId).map(m -> view(m, studentId));
    }

    @Transactional(readOnly = true)
    public List<MatchView> history(UUID studentId) {
        List<Match> list = matches.findHistory(studentId, PageRequest.of(0, HISTORY_SIZE));
        return viewAll(list, studentId);
    }

    /** Who the student is most compatible with right now (same model as the matching graph, before pairing). */
    @Transactional(readOnly = true)
    public List<Suggestion> suggestions(UUID studentId) {
        Instant now = clock.instant();
        Presence mine = presences.findById(studentId).filter(p -> p.isActive(now)).orElse(null);
        if (mine == null) {
            return List.of();
        }
        List<Presence> others = presences.findAvailable(now).stream()
                .filter(p -> !p.getStudentId().equals(studentId)).toList();
        List<Presence> all = new ArrayList<>(others);
        all.add(mine);
        Map<UUID, Candidate> byId = loader.load(all).stream()
                .collect(Collectors.toMap(Candidate::studentId, c -> c));
        Candidate me = byId.get(studentId);
        Map<Long, String> names = subjectNames(others.stream().flatMap(o ->
                scorer.edge(me, byId.get(o.getStudentId())).stream().flatMap(s -> s.sharedSubjects().stream())).toList());

        List<Suggestion> result = new ArrayList<>();
        for (Presence other : others) {
            scorer.edge(me, byId.get(other.getStudentId())).ifPresent(s -> result.add(new Suggestion(
                    other.getStudentId(), other.getStudent().getFullName(), other.getZone().getName(),
                    percent(s.weight()), s.sharedSubjects().stream().map(names::get).toList(), breakdown(s))));
        }
        result.sort(Comparator.comparingInt(Suggestion::score).reversed());
        return result.stream().limit(MAX_CANDIDATE_SUGGESTIONS).toList();
    }

    // ---- maintenance ----------------------------------------------------------------------------------------------

    /** Ends live matches of a student who left campus (checked out or timed out). */
    @EventListener
    @Transactional
    public void onPresenceChanged(PresenceChanged event) {
        if (event.kind() == PresenceChanged.Kind.CHECKED_IN) {
            return;
        }
        matches.findLiveFor(event.studentId()).ifPresent(m -> end(m, clock.instant()));
    }

    /** Expires stale proposals and closes confirmed matches that ran out of time. */
    @Transactional
    public void housekeeping(Instant now) {
        matches.findExpiredProposals(now).forEach(m -> end(m, now));
        for (Match m : matches.findAllLive()) {
            if (m.getStatus() == Match.Status.ACCEPTED && !m.getExpiresAt().isAfter(now)) {
                end(m, now);
            }
        }
    }

    private void end(Match match, Instant now) {
        if (match.getStatus() == Match.Status.PROPOSED) {
            match.setStatus(Match.Status.EXPIRED);
            match.setDecidedAt(now);
            events.publishEvent(new MatchEvent.Expired(match.getId(), match.getStudentA(), match.getStudentB()));
        } else if (match.getStatus() == Match.Status.ACCEPTED) {
            match.setStatus(Match.Status.COMPLETED);
            match.setDecidedAt(now);
            events.publishEvent(new MatchEvent.Completed(match.getId(), match.getStudentA(), match.getStudentB()));
        }
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    private boolean tryAcquireRoundLock() {
        Object locked = em.createNativeQuery("select pg_try_advisory_xact_lock(:id)")
                .setParameter("id", ROUND_LOCK_ID).getSingleResult();
        return Boolean.TRUE.equals(locked);
    }

    private Match lockFor(UUID studentId, UUID matchId) {
        return matches.findForUpdate(matchId).filter(m -> m.involves(studentId))
                .orElseThrow(() -> ApiException.notFound("Match not found"));
    }

    private static String pairKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private static int percent(double weight) {
        return (int) Math.round(weight * 100);
    }

    private static Breakdown breakdown(CompatibilityScorer.Score s) {
        return new Breakdown(s.knowledge(), s.reciprocity(), s.breadth(), s.proximity());
    }

    private Map<Long, String> subjectNames(Collection<Long> ids) {
        return subjects.findAllById(new HashSet<>(ids)).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName));
    }

    private MatchView view(Match match, UUID viewer) {
        return viewAll(List.of(match), viewer).getFirst();
    }

    private List<MatchView> viewAll(List<Match> list, UUID viewer) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = subjectNames(list.stream().flatMap(m -> m.getSharedSubjectIds().stream()).toList());
        Map<UUID, Student> partners = students.findAllById(
                list.stream().map(m -> m.partnerOf(viewer)).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        Instant now = clock.instant();
        return list.stream().map(m -> {
            Student partner = partners.get(m.partnerOf(viewer));
            String zone = presences.findById(partner.getId()).filter(p -> p.isActive(now))
                    .map(p -> p.getZone().getName()).orElse(null);
            // Contact details are only shared once both students agreed to meet.
            String email = m.getStatus() == Match.Status.ACCEPTED ? partner.getEmail() : null;
            return new MatchView(m.getId(), m.getStatus(), percent(m.getWeight()),
                    new Breakdown(m.getKnowledge(), m.getReciprocity(), m.getBreadth(), m.getProximity()),
                    new Partner(partner.getId(), partner.getFullName(), zone, partner.getBio(), email),
                    m.getSharedSubjectIds().stream().map(names::get).filter(java.util.Objects::nonNull).sorted().toList(),
                    m.hasAccepted(viewer), m.hasAccepted(partner.getId()), m.getCreatedAt(), m.getExpiresAt());
        }).toList();
    }
}
