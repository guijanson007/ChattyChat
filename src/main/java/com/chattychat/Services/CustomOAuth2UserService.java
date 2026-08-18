package com.chattychat.Services;

import com.chattychat.Entities.User;
import com.chattychat.Exception.OAuth2ProvisioningException;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.AuthUser;
import org.jspecify.annotations.NonNull;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Objects;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public @NonNull OAuth2User loadUser(@NonNull OAuth2UserRequest userRequest) throws OAuth2ProvisioningException {
        OAuth2User oAuth2User;
        try {
            oAuth2User = super.loadUser(userRequest);
        } catch (OAuth2AuthenticationException e) {
            throw new OAuth2ProvisioningException(new StringBuilder(
                    "Failed to load user from OAuth2 provider")
                    .append(": ")
                    .append(e.getError().getDescription()).toString()
            );
        }

        return switch (userRequest.getClientRegistration().getRegistrationId()) {
            case "github" -> githubRegistration(oAuth2User);
            case "google" -> googleRegistration(oAuth2User);
            default ->
                    throw new OAuth2ProvisioningException("Unsupported OAuth2 provider: " + userRequest.getClientRegistration().getRegistrationId());
        };

    }

    private @NonNull AuthUser googleRegistration(OAuth2User oAuth2User) {
        String provider = "google";
        String providerId = oAuth2User.getAttribute("sub");

        String firstName = oAuth2User.getAttribute("given_name");
        String lastName = oAuth2User.getAttribute("family_name");
        String email = oAuth2User.getAttribute("email");

        String resolvedName = firstName != null ? firstName :
                (oAuth2User.getAttribute("name") != null ?
                        oAuth2User.getAttribute("name") : "Unknown");

        User user = buildUser(provider, providerId, firstName, lastName != null ? lastName : "", email);

        return new AuthUser(user.getId(), provider, providerId, resolvedName, Collections.emptyMap());
    }

    private AuthUser githubRegistration(OAuth2User oAuth2User) {
        String provider = "github";
        String providerId = Objects.requireNonNull(oAuth2User.getAttribute("id")).toString();
        String name = oAuth2User.getAttribute("name"); //nullable
        String email = oAuth2User.getAttribute("email"); //nullable

        User user = buildUser(provider, providerId, name, "", email);

        return new AuthUser(user.getId(), provider, providerId, name != null ? name : "Unknown", Collections.emptyMap());
    }

    private @NonNull User buildUser(String provider, String providerId, String firstName, String lastName, String email) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setProvider(provider);
                    newUser.setProviderId(providerId);
                    newUser.setFirstName(firstName != null ? firstName : "Unknown");
                    newUser.setLastName(lastName);
                    newUser.setEmail(email);
                    return userRepository.save(newUser);
                });
    }
}
