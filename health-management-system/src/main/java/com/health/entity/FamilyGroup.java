package com.health.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "family_groups", indexes = {
        @Index(name = "idx_family_group_creator", columnList = "creator_user_id"),
        @Index(name = "idx_family_group_status", columnList = "status")
})
public class FamilyGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "creator_user_id", nullable = false)
    private Long creatorUserId;

    @Column(name = "max_members", nullable = false)
    private Integer maxMembers = 5;

    @Column(nullable = false, length = 20)
    private String status = "active";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
