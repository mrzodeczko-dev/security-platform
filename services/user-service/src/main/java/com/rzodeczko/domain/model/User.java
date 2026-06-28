package com.rzodeczko.domain.model;

import java.util.UUID;

public class User {
    private UUID id;

    private final Username username;
    private final Email email;

    private String password;

    private Role role;

    private boolean enabled;

    private String mfaSecret;
    private String mfaQrUrl;

    public User(Username username, Email email, String encodedPassword, Role role) {
        this.username = username;
        this.email = email;
        this.password = encodedPassword;
        this.role = role;
        this.enabled = false;
    }

    public User(
            UUID id,
            Username username,
            Email email,
            String password,
            Role role,
            boolean enabled,
            String mfaSecret,
            String mfaQrUrl
    ) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.password = password;
        this.role = role;
        this.enabled = enabled;
        this.mfaSecret = mfaSecret;
        this.mfaQrUrl = mfaQrUrl;
    }


    public void activate() {
        this.enabled = true;
    }


    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }


    public void enableMfa(String secret, String qrUrl) {
        this.mfaSecret = secret;
        this.mfaQrUrl = qrUrl;
    }

    public boolean hasMfaActive() {
        return this.mfaSecret != null;
    }

    public void changeRole(Role newRole) {
        this.role = newRole;
    }

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public UUID getId() {
        return id;
    }

    public Username getUsername() {
        return username;
    }

    public Email getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getMfaSecret() {
        return mfaSecret;
    }

    public String getMfaQrUrl() {
        return mfaQrUrl;
    }
}
