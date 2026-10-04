package edu.campusconnect.hackathon;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hackathon_teams")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HackathonTeam {

    @Embeddable
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Member {
        @Column(name = "student_id", nullable = false)
        private UUID studentId;
        @Column(name = "role_id", nullable = false)
        private Long roleId;
        @Column(nullable = false)
        private double fit;
        @Column(nullable = false)
        private double strength;
    }

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "hackathon_id", nullable = false)
    private UUID hackathonId;

    @Column(nullable = false)
    private int number;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hackathon_team_members", joinColumns = @JoinColumn(name = "team_id"))
    private List<Member> members = new ArrayList<>();

    public HackathonTeam(UUID hackathonId, int number, List<Member> members) {
        this.hackathonId = hackathonId;
        this.number = number;
        this.members = new ArrayList<>(members);
    }
}
