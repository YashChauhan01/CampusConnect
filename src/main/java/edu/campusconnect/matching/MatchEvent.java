package edu.campusconnect.matching;

import java.util.UUID;

/** Domain events raised by the matching module; consumed by notifications and real-time delivery. */
public sealed interface MatchEvent {

    UUID matchId();

    /** Both students were paired by a matching round and must now respond. */
    record Proposed(UUID matchId, UUID studentA, UUID studentB) implements MatchEvent {}

    /** One student accepted; the other still has to respond. */
    record PartnerAccepted(UUID matchId, UUID acceptedBy, UUID waitingStudent) implements MatchEvent {}

    /** Both accepted; the pairing is confirmed. */
    record Confirmed(UUID matchId, UUID studentA, UUID studentB) implements MatchEvent {}

    record Declined(UUID matchId, UUID declinedBy, UUID other) implements MatchEvent {}

    record Expired(UUID matchId, UUID studentA, UUID studentB) implements MatchEvent {}

    record Completed(UUID matchId, UUID studentA, UUID studentB) implements MatchEvent {}

    /** A student asked a recommended classmate to study together. */
    record Requested(UUID matchId, UUID requester, UUID target) implements MatchEvent {}

    /** The sender withdrew a direct request before it was answered. */
    record Cancelled(UUID matchId, UUID cancelledBy, UUID other) implements MatchEvent {}

    /** A chat message was sent in a confirmed match. */
    record MessageSent(UUID matchId, UUID sender, UUID recipient, String preview) implements MatchEvent {}
}
