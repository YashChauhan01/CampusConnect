package edu.campusconnect.model;
import jakarta.persistence.*;
@Entity @Table(name="subjects") public class Subject {@Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id; @Column(unique=true,nullable=false) public String name; protected Subject(){} public Subject(String n){name=n;}}
