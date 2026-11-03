package com.example.user.model;

/** The only fields clients may sort by (a whitelist), mapped to entity properties. */
public enum UserSortField {
    CREATED_AT("createdAt"),
    LAST_NAME("lastName"),
    EMAIL("email");

    private final String property;

    UserSortField(String property) {
        this.property = property;
    }

    public String getProperty() {
        return property;
    }
}
