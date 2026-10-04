package edu.campusconnect.student;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubjectRepository extends JpaRepository<Subject, Long> {

    Optional<Subject> findByNameIgnoreCase(String name);

    @Query("select s from Subject s where lower(s.name) like lower(concat('%', :q, '%')) order by s.name")
    List<Subject> search(@Param("q") String query, Pageable page);

    /** Race-safe create: concurrent callers inserting the same name cannot fail each other. */
    @Modifying
    @Query(value = "INSERT INTO subjects(name) VALUES (:name) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("name") String name);
}
