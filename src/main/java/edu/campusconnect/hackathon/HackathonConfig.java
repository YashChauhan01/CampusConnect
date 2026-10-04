package edu.campusconnect.hackathon;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HackathonConfig {

    @Bean
    RoleFitModel roleFitModel() {
        return new RoleFitModel();
    }

    @Bean
    TeamSynthesizer teamSynthesizer(RoleFitModel fitModel) {
        return new TeamSynthesizer(fitModel);
    }
}
