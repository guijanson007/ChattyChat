package com.chattychat.Services;

import com.chattychat.Entities.User;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.AuthUser;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

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

        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setProvider(provider);
                    newUser.setProviderId(providerId);
                    newUser.setFirstName(oAuth2User.getAttribute("given_name"));
                    newUser.setLastName(oAuth2User.getAttribute("family_name"));
                    newUser.setEmail(oAuth2User.getAttribute("email"));
                    return userRepository.save(newUser);
                });

        return new AuthUser(user.getId(), oAuth2User.getAttributes());
    }
}
