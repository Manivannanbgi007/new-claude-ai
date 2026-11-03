package com.example.user.model;

/**
 * Life cycle of a user. The allowed transitions are a business rule, so they live
 * here, next to the states:
 *
 *   ACTIVE  <-->  SUSPENDED        (PUT /users/{id}/status)
 *     \             /
 *      +-> DELETED <-+              (DELETE /users/{id}) - final, never changes again
 */
public enum UserStatus {
    ACTIVE,
    SUSPENDED,
    DELETED;

    public boolean canChangeTo(UserStatus target) {
        return switch (this) {
            case ACTIVE -> target == SUSPENDED || target == DELETED;
            case SUSPENDED -> target == ACTIVE || target == DELETED;
            case DELETED -> false;
        };
    }
}
