package edu.campusconnect.config;

import edu.campusconnect.presence.Presence;
import edu.campusconnect.presence.PresenceService;
import edu.campusconnect.student.Proficiency;
import edu.campusconnect.student.ProfileService;
import edu.campusconnect.student.Student;
import edu.campusconnect.student.StudentRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Generates a realistic synthetic campus for demos, load tests and evaluation: {@code app.seed.fake-students}
 * verified students (default 0 = off) with Indian-style names, 2-5 course subjects, 2-6 skills, and live zone
 * check-ins. Subject popularity is skewed (a few popular courses, a long tail) like a real campus. Runs only in the
 * {@code dev} profile and is reproducible through {@code app.seed.random-seed}. All accounts use the password
 * {@value DevDataSeeder#PASSWORD}; the credentials are written to {@code app.seed.accounts-file}.
 */
@Component
@Profile("dev")
@Order(2)
class FakeDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FakeDataSeeder.class);

    private static final String[] FIRST = {"Aarav", "Aditi", "Aditya", "Ananya", "Anika", "Arjun", "Aryan", "Bhavya",
            "Chaitanya", "Deepak", "Devansh", "Diya", "Divya", "Eshaan", "Gauri", "Harsh", "Hrithik", "Isha", "Ishaan",
            "Jaya", "Karan", "Kavya", "Kiran", "Kunal", "Lakshmi", "Manav", "Meera", "Mihir", "Mohit", "Nakul",
            "Neha", "Nikhil", "Nisha", "Om", "Pooja", "Pranav", "Priya", "Rahul", "Riya", "Rohan", "Sahil", "Sakshi",
            "Samar", "Sanya", "Shreya", "Siddharth", "Sneha", "Tanvi", "Tushar", "Uday", "Vaibhav", "Vedant",
            "Vikram", "Yash", "Zoya", "Aman", "Bhumi", "Chirag", "Dhruv", "Falguni"};
    private static final String[] LAST = {"Sharma", "Verma", "Gupta", "Singh", "Patel", "Shah", "Mehta", "Joshi",
            "Kulkarni", "Deshmukh", "Iyer", "Nair", "Reddy", "Rao", "Menon", "Chauhan", "Yadav", "Mishra", "Pandey",
            "Tiwari", "Agarwal", "Bansal", "Chopra", "Malhotra", "Kapoor", "Bhatt", "Desai", "Jain", "Saxena",
            "Srivastava", "Thakur", "Bose", "Das", "Ghosh", "Banerjee", "Chatterjee", "Naidu", "Pillai", "Hegde",
            "Shetty", "Punjabi", "Chaplot", "Israni", "Sarode", "Kamat", "Patil", "More", "Pawar", "Jadhav", "Shinde"};
    private static final String[] SUBJECTS = {"Data Structures", "Algorithms", "DBMS", "Operating Systems",
            "Computer Networks", "Machine Learning", "Discrete Mathematics", "Compiler Design",
            "Computer Architecture", "Software Engineering", "Web Technologies", "Cloud Computing", "Cyber Security",
            "Linear Algebra", "Probability and Statistics", "Theory of Computation", "Artificial Intelligence",
            "Data Mining", "Mobile Computing", "Embedded Systems", "Digital Logic", "Information Retrieval",
            "Computer Graphics", "Distributed Systems", "Big Data", "Natural Language Processing", "Deep Learning",
            "Blockchain", "Internet of Things", "Python Programming"};
    private static final String[] SKILLS = {"Java", "Spring", "React", "TypeScript", "JavaScript", "Node", "Python",
            "SQL", "PostgreSQL", "MongoDB", "Docker", "Kubernetes", "AWS", "Git", "Figma", "CSS", "HTML", "Go",
            "Rust", "C++", "C", "Flutter", "Kotlin", "ML", "Pandas", "TensorFlow", "PyTorch", "UX", "Linux", "Pitch"};
    private static final String[] NOTES = {"Revising for tomorrow's quiz on %s", "Need help with %s assignments",
            "Preparing for the %s viva", "Want to solve %s practice problems together",
            "Stuck on a %s lab problem", "Looking for a %s study buddy"};
    private static final Proficiency[] LEVELS = Proficiency.values();

    private final StudentRepository students;
    private final PasswordEncoder passwords;
    private final ProfileService profiles;
    private final PresenceService presence;
    private final int count;
    private final long randomSeed;
    private final double checkInRatio;
    private final String accountsFile;

    FakeDataSeeder(StudentRepository students, PasswordEncoder passwords, ProfileService profiles,
                   PresenceService presence,
                   @Value("${app.seed.fake-students:0}") int count,
                   @Value("${app.seed.random-seed:42}") long randomSeed,
                   @Value("${app.seed.checked-in-ratio:0.92}") double checkInRatio,
                   @Value("${app.seed.accounts-file:target/seed-accounts.csv}") String accountsFile) {
        this.students = students;
        this.passwords = passwords;
        this.profiles = profiles;
        this.presence = presence;
        this.count = count;
        this.randomSeed = randomSeed;
        this.checkInRatio = checkInRatio;
        this.accountsFile = accountsFile;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (count <= 0) {
            return;
        }
        long existing = students.count();
        if (existing >= count) {
            log.info("Fake data seeder skipped: {} students already exist", existing);
            return;
        }
        long start = System.nanoTime();
        Random random = new Random(randomSeed);
        String hash = passwords.encode(DevDataSeeder.PASSWORD);
        Map<String, Long> zoneIds = new HashMap<>();
        presence.zones().forEach(z -> zoneIds.put(z.name(), z.id()));
        List<Long> zones = new ArrayList<>(zoneIds.values());

        StringBuilder csv = new StringBuilder("email,name,password\n");
        Set<String> usedEmails = new HashSet<>();
        int checkedIn = 0;
        for (int i = 1; i <= count; i++) {
            String first = FIRST[random.nextInt(FIRST.length)];
            String last = LAST[random.nextInt(LAST.length)];
            String email = (first + "." + last + i).toLowerCase(Locale.ROOT) + "@college.edu";
            if (!usedEmails.add(email)) {
                continue;
            }
            Student student = new Student(first + " " + last, email, hash);
            student.setVerified(true);
            student.setBio(random.nextInt(4) == 0 ? "Year " + (1 + random.nextInt(4)) + " computer science student." : null);
            students.save(student);
            csv.append(email).append(',').append(first).append(' ').append(last).append(',')
                    .append(DevDataSeeder.PASSWORD).append('\n');

            List<String> mySubjects = pick(random, SUBJECTS, 2 + random.nextInt(4), true);
            for (String subject : mySubjects) {
                profiles.saveSubject(student, subject, level(random));
            }
            for (String skill : pick(random, SKILLS, 2 + random.nextInt(5), false)) {
                profiles.saveSkill(student, skill, level(random));
            }

            double roll = random.nextDouble();
            if (roll < checkInRatio) {
                Presence.Status status = random.nextInt(100) < 6 ? Presence.Status.BUSY : Presence.Status.AVAILABLE;
                Long zone = zones.get(random.nextInt(zones.size()));
                String note = random.nextInt(4) == 0
                        ? NOTES[random.nextInt(NOTES.length)].formatted(mySubjects.get(0)) : null;
                var profile = profiles.get(student);
                Set<Long> seeking = new HashSet<>();
                if (random.nextInt(5) < 2) {
                    seeking.add(profile.subjects().get(random.nextInt(profile.subjects().size())).id());
                }
                presence.checkIn(student.getId(), zone, status, note, seeking);
                checkedIn++;
            }
        }
        writeAccounts(csv.toString());
        log.info("Fake data seeder: {} students ({} checked in) in {} ms; credentials in {}", count, checkedIn,
                (System.nanoTime() - start) / 1_000_000, accountsFile);
    }

    /** Distinct picks; {@code skewed} makes low indexes (popular items) much more likely. */
    private static List<String> pick(Random random, String[] source, int n, boolean skewed) {
        List<String> chosen = new ArrayList<>();
        while (chosen.size() < n) {
            double r = random.nextDouble();
            int index = skewed ? (int) (source.length * r * r) : random.nextInt(source.length);
            String item = source[Math.min(index, source.length - 1)];
            if (!chosen.contains(item)) {
                chosen.add(item);
            }
        }
        Collections.shuffle(chosen, random);
        return chosen;
    }

    private static Proficiency level(Random random) {
        return LEVELS[random.nextInt(LEVELS.length)];
    }

    private void writeAccounts(String csv) {
        try {
            Path path = Path.of(accountsFile);
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, csv);
        } catch (IOException e) {
            log.warn("Could not write {}: {}", accountsFile, e.getMessage());
        }
    }
}
