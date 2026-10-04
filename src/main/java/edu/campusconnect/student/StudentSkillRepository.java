package edu.campusconnect.student;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentSkillRepository extends JpaRepository<StudentSkill, StudentSkill.Id> {

    @Query("select ss from StudentSkill ss join fetch ss.skill where ss.id.studentId = :studentId order by ss.skill.name")
    List<StudentSkill> findByStudent(@Param("studentId") UUID studentId);

    @Query("select ss from StudentSkill ss join fetch ss.skill where ss.id.studentId in :studentIds")
    List<StudentSkill> findByStudents(@Param("studentIds") Collection<UUID> studentIds);
}
