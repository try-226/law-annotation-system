package com.law.annotation.auth;

import com.law.annotation.common.enums.Role;
import com.law.annotation.user.UserDocument;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class UserPrincipal implements UserDetails, CredentialsContainer, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String loginAccount;
    private final Role role;
    private final boolean enabled;
    private transient String passwordHash;

    private UserPrincipal(
            String id,
            String loginAccount,
            String passwordHash,
            Role role,
            boolean enabled) {
        this.id = id;
        this.loginAccount = loginAccount;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public static UserPrincipal from(UserDocument user) {
        return new UserPrincipal(
                user.getId(),
                user.getLoginAccount(),
                user.getPasswordHash(),
                user.getRole(),
                user.isEnabled());
    }

    public String id() {
        return id;
    }

    public Role role() {
        return role;
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
        return loginAccount;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
