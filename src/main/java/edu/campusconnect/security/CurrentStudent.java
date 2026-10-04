package edu.campusconnect.security;

import edu.campusconnect.common.ApiException;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Access to the authenticated student of the current request. */
@Component
public class CurrentStudent {

    private final StudentRepository students;

    public CurrentStudent(StudentRepository students) {
        this.students = students;
    }

    /** Id taken from the verified token; does not touch the database. */
    public UUID id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID id)) {
            throw ApiException.unauthorized("Authentication required");
        }
        return id;
    }

    /** The persisted, still-existing and verified student. */
    public Student require() {
        return students.findById(id())
                .filter(Student::isVerified)
                .orElseThrow(() -> ApiException.unauthorized("Account not available"));
    }

    /** Like {@link #require()} but only for administrators. */
    public Student requireAdmin() {
        Student student = require();
        if (!student.isAdmin()) {
            throw ApiException.forbidden("Administrator access required");
        }
        return student;
    }
}
