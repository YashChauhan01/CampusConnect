package edu.campusconnect.repo;
import edu.campusconnect.model.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface ZoneRepository extends JpaRepository<CampusZone,Long> {java.util.List<CampusZone> findByEnabledTrue();}
