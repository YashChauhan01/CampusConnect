package edu.campusconnect.matching;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class MatchingConfig {

    @Bean
    CompatibilityScorer compatibilityScorer(MatchingProperties props) {
        return new CompatibilityScorer(props);
    }

    @Bean
    MatchingEngine matchingEngine(CompatibilityScorer scorer) {
        return new MatchingEngine(scorer);
    }
}
