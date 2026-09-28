package edu.campusconnect.model;

import jakarta.persistence.*;

@Entity
@Table(name = "skills")
public class Skill {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(unique = true, nullable = false)
    public String name;

    protected Skill() {
    }

    public Skill(String n) {
        name = n;
    }
}
