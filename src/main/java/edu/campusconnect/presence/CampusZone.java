package edu.campusconnect.presence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "campus_zones")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CampusZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Planar position in metres; null when the zone has not been placed on the campus map. */
    @Column(name = "x_m")
    private Double x;

    @Column(name = "y_m")
    private Double y;

    public CampusZone(String name, Double x, Double y) {
        this.name = name;
        this.x = x;
        this.y = y;
    }

    public boolean hasPosition() {
        return x != null && y != null;
    }

    /** Straight-line distance in metres, or {@code null} if either zone is unplaced. */
    public Double distanceTo(CampusZone other) {
        if (!hasPosition() || !other.hasPosition()) {
            return null;
        }
        return Math.hypot(x - other.x, y - other.y);
    }
}
