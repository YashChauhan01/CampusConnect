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

public interface HackathonTeamRepository extends JpaRepository<HackathonTeam, UUID> {

    List<HackathonTeam> findByHackathonIdOrderByNumber(UUID hackathonId);

    @Modifying
    @Query("delete from HackathonTeam t where t.hackathonId = :hackathonId")
    void deleteByHackathon(@Param("hackathonId") UUID hackathonId);
}
