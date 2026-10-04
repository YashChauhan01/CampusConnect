package edu.campusconnect.hackathon;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HackathonRepository extends JpaRepository<Hackathon, UUID> {

    List<Hackathon> findAllByOrderByCreatedAtDesc();

    /** Serialises mutations of one hackathon (register / synthesise / publish). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Hackathon h where h.id = :id")
    Optional<Hackathon> findForUpdate(@Param("id") UUID id);
}
