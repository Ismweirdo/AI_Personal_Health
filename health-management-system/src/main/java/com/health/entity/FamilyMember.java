package com.health.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(
        name = "family_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_family_user", columnNames = {"family_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_family_member_family", columnList = "family_id"),
                @Index(name = "idx_family_member_user", columnList = "user_id"),
                @Index(name = "idx_family_member_role", columnList = "role"),
                @Index(name = "idx_family_member_status", columnList = "status")
        }
)
public class FamilyMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private Long familyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String role = "child";

    @Column(nullable = false, length = 20)
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
