package edu.campusconnect.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    /** Single time source so that expiry logic is testable. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
