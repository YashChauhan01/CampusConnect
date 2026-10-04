package edu.campusconnect.assessment;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillAssessmentRepository extends JpaRepository<SkillAssessment, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SkillAssessment a where a.id = :id")
    Optional<SkillAssessment> findForUpdate(@Param("id") UUID id);

    @Query("""
            select a from SkillAssessment a
            where a.studentId = :studentId and a.kind = :kind and a.itemId = :itemId
              and a.status = edu.campusconnect.assessment.SkillAssessment.Status.PENDING""")
    Optional<SkillAssessment> findPending(@Param("studentId") UUID studentId,
                                          @Param("kind") SkillAssessment.Kind kind, @Param("itemId") Long itemId);

    @Query("""
            select a from SkillAssessment a
            where a.studentId = :studentId and a.kind = :kind and a.itemId = :itemId
              and a.status = edu.campusconnect.assessment.SkillAssessment.Status.GRADED
            order by a.gradedAt desc limit 1""")
    Optional<SkillAssessment> findLastGraded(@Param("studentId") UUID studentId,
                                             @Param("kind") SkillAssessment.Kind kind, @Param("itemId") Long itemId);
}
