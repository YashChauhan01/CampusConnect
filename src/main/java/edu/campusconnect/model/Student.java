package edu.campusconnect.model;

import jakarta.persistence.*;

import java.util.*;
import java.time.*;

@Entity
@Table(name = "students")
public class Student {
    @Id
    public UUID id = UUID.randomUUID();
    @Column(name = "full_name", nullable = false)
    public String fullName;
    @Column(nullable = false, unique = true)
    public String email;
    @Column(name = "password_hash", nullable = false)
    public String passwordHash;
    public boolean verified = false;
    public String bio;
    @Column(name = "created_at")
    public Instant createdAt = Instant.now();

    protected Student() {
    }

    public Student(String name, String email, String hash) {
        this.fullName = name;
        this.email = email;
        this.passwordHash = hash;
    }
}
