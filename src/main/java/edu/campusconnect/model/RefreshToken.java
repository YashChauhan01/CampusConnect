package edu.campusconnect.model;
import jakarta.persistence.*;import java.util.*;import java.time.*;
@Entity @Table(name="refresh_tokens") public class RefreshToken {@Id public UUID id=UUID.randomUUID(); @ManyToOne(optional=false) @JoinColumn(name="student_id") public Student student; @Column(name="token_hash",nullable=false) public String tokenHash; @Column(name="expires_at") public Instant expiresAt; @Column(name="revoked_at") public Instant revokedAt; protected RefreshToken(){} public RefreshToken(Student s,String hash,Instant expires){student=s;tokenHash=hash;expiresAt=expires;}}
