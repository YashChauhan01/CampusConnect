package edu.campusconnect.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.campusconnect.common.ApiException;
import edu.campusconnect.student.Proficiency;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;

@Configuration
class AssessmentConfig {

    @Bean
    @ConditionalOnProperty(name = "app.ai.enabled", havingValue = "true")
    SkillAssessor llmSkillAssessor(AiProperties props, ObjectMapper mapper) {
        return new LlmSkillAssessor(props, mapper);
    }

    /** Used when the feature is switched off: the API reports it as unavailable instead of failing to start. */
    @Bean
    @ConditionalOnMissingBean(SkillAssessor.class)
    SkillAssessor disabledSkillAssessor() {
        return new SkillAssessor() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public List<String> generateQuestions(String topic, Proficiency claimed, int count) {
                throw disabled();
            }

            @Override
            public Grade grade(String topic, Proficiency claimed, List<String> questions, List<String> answers) {
                throw disabled();
            }

            private ApiException disabled() {
                return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_DISABLED",
                        "Skill verification is not enabled on this server");
            }
        };
    }
}
