package edu.campusconnect.config;

import edu.campusconnect.hackathon.HackathonService;
import edu.campusconnect.presence.Presence;
import edu.campusconnect.presence.PresenceService;
import edu.campusconnect.student.Proficiency;
import edu.campusconnect.student.ProfileService;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Local-development convenience: on an empty database creates a handful of verified students with skills, subjects
 * and live check-ins, plus an open hackathon, so every screen has something to show. Active only with the
 * {@code dev} profile. All demo accounts use the password {@value #PASSWORD}.
 */
@Component
@Profile("dev")
class DevDataSeeder implements ApplicationRunner {

    static final String PASSWORD = "password-123456";
    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private record Person(String name, String email, Map<String, Proficiency> subjects,
                          Map<String, Proficiency> skills, String zone) {}

    private final StudentRepository students;
    private final PasswordEncoder passwords;
    private final ProfileService profiles;
    private final PresenceService presence;
    private final HackathonService hackathons;

    DevDataSeeder(StudentRepository students, PasswordEncoder passwords, ProfileService profiles,
                  PresenceService presence, HackathonService hackathons) {
        this.students = students;
        this.passwords = passwords;
        this.profiles = profiles;
        this.presence = presence;
        this.hackathons = hackathons;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (students.count() > 0) {
            return;
        }
        List<Person> people = List.of(
                new Person("Asha Rao", "asha@college.edu",
                        Map.of("Data Structures", Proficiency.ADVANCED, "DBMS", Proficiency.BEGINNER),
                        Map.of("Java", Proficiency.ADVANCED, "Spring", Proficiency.INTERMEDIATE), "Library"),
                new Person("Ravi Patel", "ravi@college.edu",
                        Map.of("Data Structures", Proficiency.INTERMEDIATE, "DBMS", Proficiency.ADVANCED),
                        Map.of("SQL", Proficiency.ADVANCED, "Java", Proficiency.INTERMEDIATE), "Library"),
                new Person("Meera Shah", "meera@college.edu",
                        Map.of("Operating Systems", Proficiency.ADVANCED, "Networks", Proficiency.INTERMEDIATE),
                        Map.of("Python", Proficiency.ADVANCED, "ML", Proficiency.INTERMEDIATE), "Computer Lab"),
                new Person("Kabir Singh", "kabir@college.edu",
                        Map.of("Operating Systems", Proficiency.INTERMEDIATE, "Networks", Proficiency.ADVANCED),
                        Map.of("React", Proficiency.INTERMEDIATE, "CSS", Proficiency.ADVANCED), "Computer Lab"),
                new Person("Nisha Verma", "nisha@college.edu",
                        Map.of("Data Structures", Proficiency.BEGINNER, "Algorithms", Proficiency.INTERMEDIATE),
                        Map.of("Figma", Proficiency.ADVANCED, "CSS", Proficiency.INTERMEDIATE), "Study Hall"),
                new Person("Dev Malhotra", "dev@college.edu",
                        Map.of("Algorithms", Proficiency.ADVANCED, "Data Structures", Proficiency.ADVANCED),
                        Map.of("Go", Proficiency.INTERMEDIATE, "Python", Proficiency.INTERMEDIATE), "Study Hall"),
                new Person("Ishaan Gupta", "ishaan@college.edu",
                        Map.of("DBMS", Proficiency.INTERMEDIATE),
                        Map.of("TypeScript", Proficiency.ADVANCED, "React", Proficiency.ADVANCED), "Cafeteria"),
                new Person("Tara Nair", "tara@college.edu",
                        Map.of("Networks", Proficiency.BEGINNER, "Operating Systems", Proficiency.BEGINNER),
                        Map.of("Python", Proficiency.BEGINNER), "Cafeteria"));

        for (Person p : people) {
            Student student = new Student(p.name(), p.email(), passwords.encode(PASSWORD));
            student.setVerified(true);
            students.save(student);
            p.subjects().forEach((name, level) -> profiles.saveSubject(student, name, level));
            p.skills().forEach((name, level) -> profiles.saveSkill(student, name, level));
        }

        Map<String, Long> zones = new java.util.HashMap<>();
        presence.zones().forEach(z -> zones.put(z.name(), z.id()));
        // Everyone except the first two demo users is already on campus, so a fresh login can try matching.
        for (Person p : people.subList(2, people.size())) {
            Student student = students.findByEmail(p.email()).orElseThrow();
            presence.checkIn(student.getId(), zones.get(p.zone()), Presence.Status.AVAILABLE, null, null);
        }

        Student organizer = students.findByEmail("asha@college.edu").orElseThrow();
        hackathons.create(organizer, "Campus Hack 2026", "A 24-hour build sprint. Teams are formed for you.",
                List.of(new HackathonService.RoleSpec("Backend", List.of("java", "spring", "sql", "go")),
                        new HackathonService.RoleSpec("Frontend", List.of("react", "css", "typescript")),
                        new HackathonService.RoleSpec("Data / ML", List.of("python", "ml")),
                        new HackathonService.RoleSpec("Design", List.of("figma"))));
        log.info("Seeded {} demo students (password: {}). Log in as asha@college.edu or ravi@college.edu.",
                people.size(), PASSWORD);
    }
}
