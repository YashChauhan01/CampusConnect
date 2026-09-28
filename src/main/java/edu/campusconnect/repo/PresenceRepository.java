package edu.campusconnect.repo;
import edu.campusconnect.model.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface PresenceRepository extends JpaRepository<Presence,java.util.UUID> {java.util.List<Presence> findByExpiresAtAfterAndStatus(java.time.Instant now,Presence.Status status);}
