package com.chattychat.Config;

import com.chattychat.Services.CustomOAuth2UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final CustomOAuth2UserService customOAuth2UserService;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService) {
        this.customOAuth2UserService = customOAuth2UserService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Use an attribute handler to resolve the token
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        // Set the name of the attribute the CsrfFilter expects
        requestHandler.setCsrfRequestAttributeName("_csrf");

        CookieCsrfTokenRepository tokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        tokenRepository.setCookieCustomizer(cookie -> cookie
                // TODO: Handle secure flag based on environment (dev vs prod)
                .secure(true)
                .sameSite("Lax")
        );

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(tokenRepository)
                        .csrfTokenRequestHandler(requestHandler)
                )
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                )
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v1/**").authenticated()
                        .requestMatchers("/ws/**").authenticated()
                        .anyRequest().permitAll()
                )
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(userInfo -> userInfo
                                .userService(customOAuth2UserService)
                        )
                        .successHandler(new SimpleUrlAuthenticationSuccessHandler("/"))
                );
        return http.build();
    }

    // Filter to force the deferred CSRF token to be rendered into the cookie
    private static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            // Render the token value to a cookie by causing the deferred token to be resolved
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}