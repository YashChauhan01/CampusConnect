package edu.campusconnect.matching;

import edu.campusconnect.common.ApiException;
import edu.campusconnect.common.RateLimiter;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat between the two students of a match, so they can agree where and when to meet. Messages can be sent while
 * the match is confirmed and read afterwards; nobody outside the pair can see them.
 */
@Service
public class ChatService {

    public static final int MAX_LENGTH = 1000;
    private static final int PREVIEW_LENGTH = 80;

    public record MessageView(UUID id, UUID senderId, boolean mine, String body, Instant createdAt) {}

    private final ChatMessageRepository messages;
    private final MatchRepository matches;
    private final RateLimiter limiter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ChatService(ChatMessageRepository messages, MatchRepository matches, RateLimiter limiter,
                       ApplicationEventPublisher events, Clock clock) {
        this.messages = messages;
        this.matches = matches;
        this.limiter = limiter;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MessageView> list(UUID studentId, UUID matchId) {
        Match match = participantMatch(studentId, matchId);
        if (match.getStatus() != Match.Status.ACCEPTED && match.getStatus() != Match.Status.COMPLETED) {
            throw ApiException.conflict("Chat opens once both of you have accepted");
        }
        return messages.findByMatchIdOrderByCreatedAtAsc(matchId).stream().map(m -> view(m, studentId)).toList();
    }

    @Transactional
    public MessageView send(UUID studentId, UUID matchId, String text) {
        String body = text == null ? "" : text.strip();
        if (body.isEmpty()) {
            throw ApiException.badRequest("Write a message first");
        }
        if (body.length() > MAX_LENGTH) {
            throw ApiException.badRequest("Messages can be at most " + MAX_LENGTH + " characters");
        }
        Match match = participantMatch(studentId, matchId);
        if (match.getStatus() != Match.Status.ACCEPTED) {
            throw ApiException.conflict("You can only chat while the match is active");
        }
        limiter.check("chat:" + studentId, 20, Duration.ofSeconds(30));
        ChatMessage saved = messages.save(new ChatMessage(matchId, studentId, body, clock.instant()));
        String preview = body.length() > PREVIEW_LENGTH ? body.substring(0, PREVIEW_LENGTH - 1) + "…" : body;
        events.publishEvent(new MatchEvent.MessageSent(matchId, studentId, match.partnerOf(studentId), preview));
        return view(saved, studentId);
    }

    private Match participantMatch(UUID studentId, UUID matchId) {
        return matches.findById(matchId).filter(m -> m.involves(studentId))
                .orElseThrow(() -> ApiException.notFound("Match not found"));
    }

    private static MessageView view(ChatMessage m, UUID viewer) {
        return new MessageView(m.getId(), m.getSenderId(), m.getSenderId().equals(viewer), m.getBody(), m.getCreatedAt());
    }
}
