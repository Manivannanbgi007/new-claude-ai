package com.example.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * A user of the platform, table "users". All timestamps are UTC.
 * State changes go through methods (changeStatus, markDeleted) so the life-cycle
 * rules in UserStatus can never be bypassed with a plain setter.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "phone_number", length = 16)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private UserStatus status;

    /** Optimistic locking: +1 on every update; clients send it back with changes. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Required by JPA. */
    protected User() {
    }

    public User(String id, String email, String firstName, String lastName, String phoneNumber) {
        this.id = id;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
        this.status = UserStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }

    // ---------------- business methods ----------------

    public boolean isDeleted() {
        return status == UserStatus.DELETED;
    }

    /** Returns false if the transition is not allowed (see UserStatus.canChangeTo). */
    public boolean changeStatus(UserStatus target) {
        if (!status.canChangeTo(target)) {
            return false;
        }
        status = target;
        deletedAt = target == UserStatus.DELETED ? LocalDateTime.now(ZoneOffset.UTC) : null;
        return true;
    }

    public void updateProfile(String email, String firstName, String lastName, String phoneNumber) {
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phoneNumber = phoneNumber;
    }

    // ---------------- getters ----------------

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
