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

public interface HackathonRoleRepository extends JpaRepository<HackathonRole, Long> {

    List<HackathonRole> findByHackathonIdOrderByPosition(UUID hackathonId);

    List<HackathonRole> findByHackathonIdIn(Collection<UUID> hackathonIds);
}
