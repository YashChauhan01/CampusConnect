package edu.campusconnect.hackathon;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A student's registration for a hackathon together with their ordered role preferences. */
@Entity
@Table(name = "hackathon_participants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HackathonParticipant {

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Id implements Serializable {
        @Column(name = "hackathon_id")
        private UUID hackathonId;
        @Column(name = "student_id")
        private UUID studentId;
    }

    @EmbeddedId
    private Id id;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    /** Role ids, first choice first. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "hackathon_role_prefs", joinColumns = {
            @JoinColumn(name = "hackathon_id", referencedColumnName = "hackathon_id"),
            @JoinColumn(name = "student_id", referencedColumnName = "student_id")})
    @OrderColumn(name = "rank")
    @Column(name = "role_id", nullable = false)
    private List<Long> rolePreferences = new ArrayList<>();

    public HackathonParticipant(UUID hackathonId, UUID studentId, Instant registeredAt, List<Long> rolePreferences) {
        this.id = new Id(hackathonId, studentId);
        this.registeredAt = registeredAt;
        this.rolePreferences = new ArrayList<>(rolePreferences);
    }

    public UUID studentId() {
        return id.getStudentId();
    }

    public void replacePreferences(List<Long> preferences) {
        rolePreferences.clear();
        rolePreferences.addAll(preferences);
    }
}
