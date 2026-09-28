package edu.campusconnect.repo;
import edu.campusconnect.model.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface SubjectRepository extends JpaRepository<Subject,Long> {java.util.Optional<Subject> findByNameIgnoreCase(String name);}
