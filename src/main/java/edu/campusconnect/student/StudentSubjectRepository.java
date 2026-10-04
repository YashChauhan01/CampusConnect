package edu.campusconnect.student;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentSubjectRepository extends JpaRepository<StudentSubject, StudentSubject.Id> {

    @Query("select ss from StudentSubject ss join fetch ss.subject where ss.id.studentId = :studentId order by ss.subject.name")
    List<StudentSubject> findByStudent(@Param("studentId") UUID studentId);

    @Query("select ss from StudentSubject ss join fetch ss.subject where ss.id.studentId in :studentIds")
    List<StudentSubject> findByStudents(@Param("studentIds") Collection<UUID> studentIds);
}
