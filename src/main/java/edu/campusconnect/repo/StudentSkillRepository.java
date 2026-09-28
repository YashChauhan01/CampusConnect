package edu.campusconnect.repo;
import edu.campusconnect.model.*;import org.springframework.data.jpa.repository.JpaRepository;
public interface StudentSkillRepository extends JpaRepository<StudentSkill,StudentSkill.Key> {java.util.List<StudentSkill> findByStudentId(java.util.UUID studentId); void deleteByStudentId(java.util.UUID studentId);}
