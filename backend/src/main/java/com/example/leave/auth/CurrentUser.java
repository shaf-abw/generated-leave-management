package com.example.leave.auth;

import com.example.leave.employee.Role;
import java.io.Serial;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Authenticated principal stored in the HTTP session. Holds no password after authentication. */
public final class CurrentUser implements UserDetails, CredentialsContainer {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Long employeeId;
    private final String username;
    private final Role role;
    private final boolean enabled;
    private transient String passwordHash;

    public CurrentUser(Long employeeId, String username, Role role, boolean enabled, String passwordHash) {
        this.employeeId = employeeId;
        this.username = username;
        this.role = role;
        this.enabled = enabled;
        this.passwordHash = passwordHash;
    }

    public Long employeeId() {
        return employeeId;
    }

    public Role role() {
        return role;
    }

    public boolean isManager() {
        return role == Role.MANAGER;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }
}
