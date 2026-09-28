package edu.campusconnect.model;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;
import java.time.*;

@Entity
@Table(name = "email_tokens")
public class EmailToken {
    @Id
    public UUID id = UUID.randomUUID();
    @ManyToOne(optional = false)
    @JoinColumn(name = "student_id")
    public Student student;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", length = 64, nullable = false)
    private String tokenHash;
    @Column(name = "expires_at")
    public Instant expiresAt;
    @Column(name = "used_at")
    public Instant usedAt;

    protected EmailToken() {
    }

    public EmailToken(Student s, String hash, Instant expires) {
        student = s;
        tokenHash = hash;
        expiresAt = expires;
    }
}
