package edu.campusconnect.repo;

import edu.campusconnect.model.Presence;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface PresenceRepository extends CrudRepository<Presence, UUID> {
    List<Presence> findByStatus(Presence.Status status);

    List<Presence> findByStatusAndZoneId(Presence.Status status, Long zoneId);
}
