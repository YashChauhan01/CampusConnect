package edu.campusconnect.evaluation;

import edu.campusconnect.hackathon.RoleFitModel;
import edu.campusconnect.hackathon.TeamSynthesizer;
import edu.campusconnect.hackathon.TeamSynthesizer.Participant;
import edu.campusconnect.hackathon.TeamSynthesizer.Role;
import edu.campusconnect.hackathon.TeamSynthesizer.Synthesis;
import edu.campusconnect.matching.Candidate;
import edu.campusconnect.matching.CompatibilityScorer;
import edu.campusconnect.matching.MatchingEngine;
import edu.campusconnect.matching.MatchingProperties;
import edu.campusconnect.matching.algo.HungarianAlgorithm;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Generates the performance evaluation (docs/EVALUATION.md and CSV files) from real runs on synthetic campuses.
 * Skipped in normal builds; run with {@code mvn test -Dtest=EvaluationReport -Deval=true}.
 */
@EnabledIfSystemProperty(named = "eval", matches = "true")
class EvaluationReport {

    private static final int[] POOL_SIZES = {50, 100, 200, 400, 800, 1600};
    private static final int[] PARTICIPANT_COUNTS = {12, 24, 48, 96, 192, 384};
    private static final int REPEATS = 5;
    private static final int TRIALS = 30;
    private static final int SUBJECT_CATALOGUE = 30;

    private static final double[][] ZONES = {{0, 0}, {80, 40}, {200, -60}, {40, -30}};

    private final MatchingProperties props = new MatchingProperties(false, Duration.ofSeconds(20),
            Duration.ofMinutes(5), Duration.ofHours(3), Duration.ofDays(1), Duration.ofSeconds(5), 0.30, 120, 0.3, 5000,
            new MatchingProperties.Weights(0.45, 0.15, 0.15, 0.25));
    private final CompatibilityScorer scorer = new CompatibilityScorer(props);
    private final MatchingEngine engine = new MatchingEngine(scorer);

    @Test
    void generate() throws IOException {
        StringBuilder md = new StringBuilder();
        StringBuilder csvMatching = new StringBuilder(
                "pool,edges,optimal_ms,greedy_ms,optimal_weight,greedy_weight,weight_ratio,optimal_matched_pct,greedy_matched_pct\n");
        StringBuilder csvTeams = new StringBuilder(
                "participants,teams,synthesis_ms,fit_optimised,fit_random,pref_optimised,pref_random,stddev_optimised,stddev_random\n");
        StringBuilder csvHungarian = new StringBuilder("n,ms\n");

        md.append("# Performance evaluation\n\n");
        md.append("Generated ").append(LocalDate.now()).append(" on ").append(System.getProperty("os.name"))
                .append(", Java ").append(System.getProperty("java.version")).append(", ")
                .append(Runtime.getRuntime().availableProcessors()).append(" logical CPUs. ")
                .append("All data is synthetic: ").append(SUBJECT_CATALOGUE)
                .append(" subjects with skewed popularity, 2–5 subjects per student, three proficiency levels and four campus zones. ")
                .append("Timings are the median of ").append(REPEATS).append(" runs after warm-up; quality figures are means over ")
                .append(TRIALS).append(" random instances.\n\n");

        matchingSection(md, csvMatching);
        hackathonSection(md, csvTeams);
        hungarianSection(md, csvHungarian);

        Path dir = Path.of("docs");
        Files.createDirectories(dir.resolve("data"));
        Files.writeString(dir.resolve("EVALUATION.md"), md.toString());
        Files.writeString(dir.resolve("data/matching.csv"), csvMatching.toString());
        Files.writeString(dir.resolve("data/teams.csv"), csvTeams.toString());
        Files.writeString(dir.resolve("data/hungarian.csv"), csvHungarian.toString());
        System.out.println(md);
    }

    // ---- Mode 1 -------------------------------------------------------------------------------------------------

    private void matchingSection(StringBuilder md, StringBuilder csv) {
        md.append("## Mode 1 — peer matching (maximum-weight matching vs. greedy)\n\n");
        md.append("Each round builds the compatibility graph of all available students and pairs them. ")
                .append("\"Optimal\" is the exact blossom algorithm used in production; \"greedy\" repeatedly takes ")
                .append("the heaviest remaining edge. Weight ratio = greedy total weight ÷ optimal total weight.\n\n");
        md.append("| Pool | Edges | Optimal (ms) | Greedy (ms) | Optimal weight | Greedy weight | Greedy / optimal | Students matched (opt / greedy) |\n");
        md.append("|---:|---:|---:|---:|---:|---:|---:|---:|\n");

        Random random = new Random(20260101);
        for (int size : POOL_SIZES) {
            List<Candidate> pool = pool(random, size);
            engine.match(pool, null); // warm-up
            double[] optimalMs = new double[REPEATS];
            double[] greedyMs = new double[REPEATS];
            MatchingEngine.Result optimal = null;
            MatchingEngine.Result greedy = null;
            for (int i = 0; i < REPEATS; i++) {
                optimal = engine.match(pool, null);
                greedy = engine.greedy(pool, null);
                optimalMs[i] = optimal.nanos() / 1e6;
                greedyMs[i] = greedy.nanos() / 1e6;
            }
            double ratio = optimal.totalWeight() == 0 ? 1 : greedy.totalWeight() / optimal.totalWeight();
            double optPct = 200.0 * optimal.pairs().size() / size;
            double grPct = 200.0 * greedy.pairs().size() / size;
            md.append(String.format(Locale.ROOT, "| %d | %d | %.1f | %.1f | %.2f | %.2f | %.3f | %.1f%% / %.1f%% |%n",
                    size, optimal.edgeCount(), median(optimalMs), median(greedyMs), optimal.totalWeight(),
                    greedy.totalWeight(), ratio, optPct, grPct));
            csv.append(String.format(Locale.ROOT, "%d,%d,%.3f,%.3f,%.4f,%.4f,%.5f,%.2f,%.2f%n", size, optimal.edgeCount(),
                    median(optimalMs), median(greedyMs), optimal.totalWeight(), greedy.totalWeight(), ratio, optPct, grPct));
        }

        double minRatio = 1;
        double meanRatio = 0;
        int instances = 100;
        for (int i = 0; i < instances; i++) {
            List<Candidate> pool = pool(random, 100);
            double ratio = engine.greedy(pool, null).totalWeight() / engine.match(pool, null).totalWeight();
            minRatio = Math.min(minRatio, ratio);
            meanRatio += ratio / instances;
        }
        md.append(String.format(Locale.ROOT,
                "%nOver %d further random pools of 100 students, greedy achieved on average %.1f%% of the optimal total weight (worst case %.1f%%).%n%n",
                instances, 100 * meanRatio, 100 * minRatio));
    }

    // ---- Mode 2 -------------------------------------------------------------------------------------------------

    private void hackathonSection(StringBuilder md, StringBuilder csv) {
        md.append("## Mode 2 — hackathon team synthesis (Hungarian algorithm vs. random teams)\n\n");
        md.append("Five roles (backend, frontend, data/ML, design, product); participants rank up to three roles. ")
                .append("Fit is the mean role fit (higher is better), preference is the share of participants who got one of their top-3 roles, ")
                .append("and σ is the standard deviation of team strength (lower is more balanced).\n\n");
        md.append("| Participants | Teams | Synthesis (ms) | Fit: Hungarian / random | Preferences met: Hungarian / random | σ strength: Hungarian / random |\n");
        md.append("|---:|---:|---:|---:|---:|---:|\n");

        List<Role> roles = List.of(
                new Role(1L, "Backend", List.of("java", "spring", "sql", "node")),
                new Role(2L, "Frontend", List.of("react", "css", "typescript")),
                new Role(3L, "Data", List.of("python", "ml", "pandas")),
                new Role(4L, "Design", List.of("figma", "ux")),
                new Role(5L, "Product", List.of("pitch", "planning")));
        TeamSynthesizer synthesizer = new TeamSynthesizer(new RoleFitModel());
        Random random = new Random(7);

        for (int n : PARTICIPANT_COUNTS) {
            double fitOpt = 0, fitRnd = 0, prefOpt = 0, prefRnd = 0, sdOpt = 0, sdRnd = 0;
            double[] times = new double[TRIALS];
            int teams = 0;
            for (int trial = 0; trial < TRIALS; trial++) {
                List<Participant> people = participants(random, n, roles);
                long start = System.nanoTime();
                Synthesis optimised = synthesizer.synthesize(people, roles);
                times[trial] = (System.nanoTime() - start) / 1e6;
                Synthesis baseline = synthesizer.randomBaseline(people, roles, random);
                teams = optimised.teams().size();
                fitOpt += optimised.quality().averageFit() / TRIALS;
                fitRnd += baseline.quality().averageFit() / TRIALS;
                prefOpt += optimised.quality().preferenceSatisfaction() / TRIALS;
                prefRnd += baseline.quality().preferenceSatisfaction() / TRIALS;
                sdOpt += optimised.quality().strengthStdDev() / TRIALS;
                sdRnd += baseline.quality().strengthStdDev() / TRIALS;
            }
            md.append(String.format(Locale.ROOT, "| %d | %d | %.1f | %.3f / %.3f | %.1f%% / %.1f%% | %.4f / %.4f |%n",
                    n, teams, median(times), fitOpt, fitRnd, 100 * prefOpt, 100 * prefRnd, sdOpt, sdRnd));
            csv.append(String.format(Locale.ROOT, "%d,%d,%.3f,%.4f,%.4f,%.4f,%.4f,%.5f,%.5f%n",
                    n, teams, median(times), fitOpt, fitRnd, prefOpt, prefRnd, sdOpt, sdRnd));
        }
        md.append("\n");
    }

    // ---- Hungarian scaling ---------------------------------------------------------------------------------------

    private void hungarianSection(StringBuilder md, StringBuilder csv) {
        md.append("## Hungarian algorithm scaling (dense n × n cost matrix)\n\n| n | Time (ms) |\n|---:|---:|\n");
        Random random = new Random(3);
        for (int n : new int[]{50, 100, 200, 400, 800}) {
            long[][] cost = new long[n][n];
            for (long[] row : cost) {
                for (int j = 0; j < n; j++) {
                    row[j] = random.nextInt(1_000_000);
                }
            }
            HungarianAlgorithm.solve(cost); // warm-up
            double[] times = new double[REPEATS];
            for (int i = 0; i < REPEATS; i++) {
                long start = System.nanoTime();
                HungarianAlgorithm.solve(cost);
                times[i] = (System.nanoTime() - start) / 1e6;
            }
            md.append(String.format(Locale.ROOT, "| %d | %.1f |%n", n, median(times)));
            csv.append(String.format(Locale.ROOT, "%d,%.3f%n", n, median(times)));
        }
        md.append("\n");
    }

    // ---- synthetic data -----------------------------------------------------------------------------------------

    private static List<Candidate> pool(Random random, int size) {
        List<Candidate> pool = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Map<Long, Integer> levels = new HashMap<>();
            int count = 2 + random.nextInt(4);
            while (levels.size() < count) {
                // Skewed popularity: low ids are much more common.
                long subject = (long) Math.floor(SUBJECT_CATALOGUE * Math.pow(random.nextDouble(), 2));
                levels.put(subject, 1 + random.nextInt(3));
            }
            int zone = random.nextInt(ZONES.length);
            pool.add(new Candidate(UUID.randomUUID(), (long) zone, ZONES[zone][0], ZONES[zone][1], levels,
                    new HashSet<>(levels.keySet())));
        }
        return pool;
    }

    private static List<Participant> participants(Random random, int n, List<Role> roles) {
        String[] skills = {"java", "spring", "sql", "node", "react", "css", "typescript", "python", "ml", "pandas",
                "figma", "ux", "pitch", "planning", "go", "rust"};
        List<Participant> people = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            Map<String, Integer> own = new HashMap<>();
            int count = 1 + random.nextInt(5);
            for (int s = 0; s < count; s++) {
                own.put(skills[random.nextInt(skills.length)], 1 + random.nextInt(3));
            }
            List<Long> ids = new ArrayList<>(roles.stream().map(Role::id).toList());
            java.util.Collections.shuffle(ids, random);
            people.add(new Participant(UUID.randomUUID(), own, ids.subList(0, 1 + random.nextInt(3))));
        }
        return people;
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }
}
