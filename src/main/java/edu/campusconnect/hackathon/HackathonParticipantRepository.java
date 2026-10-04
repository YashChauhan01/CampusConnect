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

public interface HackathonParticipantRepository extends JpaRepository<HackathonParticipant, HackathonParticipant.Id> {

    @Query("select p from HackathonParticipant p where p.id.hackathonId = :hackathonId order by p.registeredAt")
    List<HackathonParticipant> findByHackathon(@Param("hackathonId") UUID hackathonId);

    @Query("select p.id.hackathonId, count(p) from HackathonParticipant p where p.id.hackathonId in :ids group by p.id.hackathonId")
    List<Object[]> countByHackathons(@Param("ids") Collection<UUID> ids);

    @Query("select p.id.hackathonId from HackathonParticipant p where p.id.studentId = :studentId")
    List<UUID> findHackathonIdsOf(@Param("studentId") UUID studentId);
}
