package edu.campusconnect.matching;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MatchRepository extends JpaRepository<Match, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Match m where m.id = :id")
    Optional<Match> findForUpdate(@Param("id") UUID id);

    @Query("""
            select m from Match m
            where (m.studentA = :studentId or m.studentB = :studentId)
              and m.status in (edu.campusconnect.matching.Match.Status.PROPOSED, edu.campusconnect.matching.Match.Status.ACCEPTED)""")
    Optional<Match> findLiveFor(@Param("studentId") UUID studentId);

    @Query("""
            select m from Match m
            where m.status in (edu.campusconnect.matching.Match.Status.PROPOSED, edu.campusconnect.matching.Match.Status.ACCEPTED)""")
    List<Match> findAllLive();

    @Query("""
            select m from Match m
            where (m.studentA = :studentId or m.studentB = :studentId)
            order by m.createdAt desc""")
    List<Match> findHistory(@Param("studentId") UUID studentId, org.springframework.data.domain.Pageable page);

    @Query("""
            select m from Match m
            where m.status = edu.campusconnect.matching.Match.Status.PROPOSED and m.expiresAt <= :now""")
    List<Match> findExpiredProposals(@Param("now") Instant now);

    @Query("""
            select m from Match m
            where m.status = edu.campusconnect.matching.Match.Status.DECLINED and m.decidedAt >= :since
              and (m.studentA in :students or m.studentB in :students)""")
    List<Match> findDeclinedSince(@Param("since") Instant since, @Param("students") Collection<UUID> students);
}
