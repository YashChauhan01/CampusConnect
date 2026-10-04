package edu.campusconnect.presence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PresenceRepository extends JpaRepository<Presence, UUID> {

    @Query("""
            select p from Presence p join fetch p.student join fetch p.zone
            where p.expiresAt > :now and p.status = edu.campusconnect.presence.Presence.Status.AVAILABLE
            order by p.updatedAt desc""")
    List<Presence> findAvailable(@Param("now") Instant now);

    @Query("select p.studentId from Presence p where p.expiresAt <= :now")
    List<UUID> findExpiredIds(@Param("now") Instant now);

    @Query("select p.zone.id, count(p) from Presence p where p.expiresAt > :now group by p.zone.id")
    List<Object[]> countActiveByZone(@Param("now") Instant now);
}
