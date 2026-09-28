package edu.campusconnect.repo;
import edu.campusconnect.model.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillRepository extends JpaRepository<Skill,Long> {java.util.Optional<Skill> findByNameIgnoreCase(String name);}
