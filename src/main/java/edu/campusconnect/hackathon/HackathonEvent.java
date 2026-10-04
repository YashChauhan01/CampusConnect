package edu.campusconnect.hackathon;

import java.util.List;
import java.util.UUID;

/** Domain events of the hackathon module. */
public sealed interface HackathonEvent {

    /** Teams were made final; every participant should be told which team they are in. */
    record TeamsPublished(UUID hackathonId, String hackathonName, List<UUID> participantIds) implements HackathonEvent {}
}
