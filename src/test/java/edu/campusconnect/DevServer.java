package edu.campusconnect;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.boot.SpringApplication;

/**
 * Starts the whole backend against an embedded PostgreSQL (data kept in {@code target/dev-pg}) with the {@code dev}
 * profile — a Docker-free way to run the app locally:
 * {@code mvn -q test-compile exec:java -Dexec.mainClass=edu.campusconnect.DevServer -Dexec.classpathScope=test}.
 */
public final class DevServer {

    private DevServer() {}

    public static void main(String[] args) throws IOException {
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setPort(54329)
                .setDataDirectory(Path.of("target", "dev-pg"))
                .setCleanDataDirectory(false)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (IOException ignored) {
                // best effort on exit
            }
        }));
        SpringApplication.run(CampusConnectApplication.class,
                "--spring.profiles.active=dev",
                "--spring.datasource.url=" + pg.getJdbcUrl("postgres", "postgres"),
                "--spring.datasource.username=postgres",
                "--spring.datasource.password=postgres");
    }
}
