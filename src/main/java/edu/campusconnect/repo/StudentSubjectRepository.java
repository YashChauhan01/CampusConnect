package edu.campusconnect.repo;
import edu.campusconnect.model.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface StudentSubjectRepository extends JpaRepository<StudentSubject,StudentSubject.Key> {java.util.List<StudentSubject> findByStudentId(java.util.UUID studentId); void deleteByStudentId(java.util.UUID studentId);}
