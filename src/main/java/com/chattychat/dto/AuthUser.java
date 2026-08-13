package com.chattychat.dto;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public record AuthUser(UUID userId, Map<String, Object> attributes) implements OAuth2User {

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList(); // NO roles for now
    }

    @Override
    public String getName() {
        return attributes.get("given_name").toString();
    }

    public UUID getUserId() {
        return userId;
    }

    public String getProvider() {
        return (String) attributes.get("provider");
    }

    public String getProviderId() {
        return (String) attributes.get("sub");
    }
}
