package edu.campusconnect.presence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ZoneRepository extends JpaRepository<CampusZone, Long> {

    List<CampusZone> findByEnabledTrueOrderByName();
}
