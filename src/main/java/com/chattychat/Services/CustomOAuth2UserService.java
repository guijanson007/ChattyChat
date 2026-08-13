package com.chattychat.Services;

import com.chattychat.Entities.User;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.AuthUser;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String provider = userRequest.getClientRegistration().getRegistrationId();
        String providerId = oAuth2User.getAttribute("sub");

        String firstName = oAuth2User.getAttribute("given_name");
        String lastName = oAuth2User.getAttribute("family_name");
        String email = oAuth2User.getAttribute("email");

        String resolvedName = firstName != null ? firstName : (oAuth2User.getAttribute("name") != null ? oAuth2User.getAttribute("name") : "Unknown");

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setProvider(provider);
                    newUser.setProviderId(providerId);
                    newUser.setFirstName(firstName != null ? firstName : "Unknown");
                    newUser.setLastName(lastName != null ? lastName : "");
                    newUser.setEmail(email);
                    return userRepository.save(newUser);
                });

        // Pass clean primitives instead of the raw, non-serializable attribute map blocks
        return new AuthUser(user.getId(), provider, providerId, resolvedName, Collections.emptyMap());
    }
}
