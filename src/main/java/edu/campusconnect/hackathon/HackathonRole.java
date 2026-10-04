package edu.campusconnect.hackathon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A role every team should cover, with the skill keywords that indicate suitability. */
@Entity
@Table(name = "hackathon_roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HackathonRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hackathon_id", nullable = false)
    private UUID hackathonId;

    @Column(nullable = false, length = 60)
    private String name;

    /** Comma separated, lower case. */
    @Column(nullable = false, length = 300)
    private String keywords;

    @Column(nullable = false)
    private int position;

    public HackathonRole(UUID hackathonId, String name, List<String> keywords, int position) {
        this.hackathonId = hackathonId;
        this.name = name;
        this.keywords = String.join(",", keywords);
        this.position = position;
    }

    public List<String> keywordList() {
        return keywords.isBlank() ? List.of() : Arrays.asList(keywords.split(","));
    }
}
