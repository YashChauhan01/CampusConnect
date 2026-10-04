package edu.campusconnect.student;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import edu.campusconnect.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class ProfileIntegrationTest extends IntegrationTest {

    private String token;

    @BeforeEach
    void signIn() throws Exception {
        token = signUp("asha@college.edu", "Asha Rao");
    }

    @Test
    void updatesNameAndBio() throws Exception {
        mvc.perform(put("/api/v1/students/me").header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("fullName", "  Asha R.  ", "bio", "Third year, loves graphs"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Asha R."))
                .andExpect(jsonPath("$.bio").value("Third year, loves graphs"));
    }

    @Test
    void addsSkillsAndSubjectsWithProficiency() throws Exception {
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "Java", "proficiency", "ADVANCED"))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.skills[0].proficiency").value("ADVANCED"));
        mvc.perform(postJson("/api/v1/students/me/subjects", Map.of("name", "DBMS", "proficiency", "BEGINNER"))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects[0].name").value("DBMS"))
                .andExpect(jsonPath("$.subjects[0].proficiency").value("BEGINNER"));
    }

    @Test
    void savingTheSameSkillAgainUpdatesTheLevelAndIgnoresCaseAndSpacing() throws Exception {
        mvc.perform(postJson("/api/v1/students/me/subjects", Map.of("name", "Data Structures", "proficiency", "BEGINNER"))
                .header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/students/me/subjects", Map.of("name", "  data   structures ", "proficiency", "ADVANCED"))
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subjects.length()").value(1))
                .andExpect(jsonPath("$.subjects[0].name").value("Data Structures"))
                .andExpect(jsonPath("$.subjects[0].proficiency").value("ADVANCED"));
    }

    @Test
    void removesSkill() throws Exception {
        String body = mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "Go", "proficiency", "BEGINNER"))
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).get("skills").get(0).get("id").asLong();

        mvc.perform(delete("/api/v1/students/me/skills/" + id).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(0));
    }

    @Test
    void rejectsInvalidSkillInput() throws Exception {
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "", "proficiency", "ADVANCED"))
                .header("Authorization", bearer(token))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "Java", "proficiency", "GURU"))
                .header("Authorization", bearer(token))).andExpect(status().isBadRequest());
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "<script>", "proficiency", "ADVANCED"))
                .header("Authorization", bearer(token))).andExpect(status().isBadRequest());
    }

    @Test
    void acceptsNamesWithPunctuation() throws Exception {
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "C++", "proficiency", "INTERMEDIATE"))
                .header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "C#", "proficiency", "INTERMEDIATE"))
                .header("Authorization", bearer(token))).andExpect(status().isOk());
    }

    @Test
    void profilesAreIsolatedBetweenStudents() throws Exception {
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "Java", "proficiency", "ADVANCED"))
                .header("Authorization", bearer(token))).andExpect(status().isOk());
        String other = signUp("ravi@college.edu", "Ravi Patel");

        mvc.perform(get("/api/v1/students/me").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills.length()").value(0));
    }

    @Test
    void suggestsExistingCatalogNames() throws Exception {
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "JavaScript", "proficiency", "ADVANCED"))
                .header("Authorization", bearer(token))).andExpect(status().isOk());
        mvc.perform(postJson("/api/v1/students/me/skills", Map.of("name", "Java", "proficiency", "ADVANCED"))
                .header("Authorization", bearer(token))).andExpect(status().isOk());

        mvc.perform(get("/api/v1/skills").param("q", "script").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0]").value("JavaScript"));
        mvc.perform(get("/api/v1/skills").param("q", "JAVA").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.length()").value(2));
    }
}
