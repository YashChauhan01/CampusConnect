package edu.campusconnect.model;
import jakarta.persistence.*;
@Entity @Table(name="campus_zones") public class CampusZone {@Id public Long id;public String name;public boolean enabled;protected CampusZone(){}}
