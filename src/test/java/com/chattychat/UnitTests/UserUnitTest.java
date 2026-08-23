package com.chattychat.UnitTests;

import com.chattychat.Config.SecurityConfig;
import com.chattychat.Controller.UserController;
import com.chattychat.Exception.InvalidUserException;
import com.chattychat.Services.CustomOAuth2UserService;
import com.chattychat.Services.UserService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller-level tests for {@link UserController}, driven through MockMvc.
 * <p>
 * These deliberately go through the servlet/security stack rather than calling the controller
 * as a plain Java object: the 401s come from the {@code HttpStatusEntryPoint} in
 * {@link SecurityConfig}, the 403s from {@code @PreAuthorize} (which only exists on the
 * method-security proxy), and the 404s from the {@code @ControllerAdvice} that translates
 * {@link InvalidUserException}. None of that is in play for a direct method invocation, so a
 * plain Mockito unit test can only ever observe the 200 path.
 * <p>
 * {@link UserService} is mocked, so no database is required.
 */
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserUnitTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    // SecurityConfig wires this into oauth2Login(); the slice does not scan @Service beans.
    @MockitoBean
    private CustomOAuth2UserService customOAuth2UserService;

    private static UserDTO userDto(UUID id, String displayName) {
        return new UserDTO(id, "first", "last", "user@example.com", displayName);
    }

    private static AuthUser authUser(UUID userId) {
        return new AuthUser(userId, "google", "provider-id-" + userId, "name", Map.of("sub", "provider-id"));
    }

    // ---------------------------------------------------------------- GET /v1/users

    @Test
    void getUsers_ReturnsListOfUsers() throws Exception {
        UUID id = UUID.randomUUID();
        when(userService.getUsers()).thenReturn(List.of(userDto(id, "display")));

        mockMvc.perform(get("/v1/users").with(oauth2Login().oauth2User(authUser(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].displayName").value("display"));
    }

    @Test
    void getUsers_WhenUnauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/v1/users"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getUsers();
    }

    // ------------------------------------------------------------ GET /v1/users/{id}

    @Test
    void getUserById_WhenUserExists_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserById(userId)).thenReturn(userDto(userId, "display"));

        mockMvc.perform(get("/v1/users/{userId}", userId)
                        .with(oauth2Login().oauth2User(authUser(UUID.randomUUID()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @Test
    void getUserById_WhenUserDoesNotExist_Returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.getUserById(userId)).thenThrow(new InvalidUserException("User not found"));

        mockMvc.perform(get("/v1/users/{userId}", userId)
                        .with(oauth2Login().oauth2User(authUser(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------- GET /v1/users/me

    @Test
    void getCurrentUser_WhenUnauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/v1/users/me"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).getUserByProviderAndProviderId(any(), any());
    }

    /**
     * The session principal survives but its backing row is gone (deleted account, wiped DB).
     * {@code UserService} throws {@link InvalidUserException}, which the advice maps to 404.
     * See the note in the review: 401 would arguably be the more honest status for /me, but 404
     * is what production actually does today and the frontend treats any non-2xx the same way.
     */
    @Test
    void getCurrentUser_WhenUserNotFoundInDb_Returns404() throws Exception {
        when(userService.getUserByProviderAndProviderId(any(), any()))
                .thenThrow(new InvalidUserException("User not found"));

        mockMvc.perform(get("/v1/users/me").with(oauth2Login().oauth2User(authUser(UUID.randomUUID()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getCurrentUser_WhenValid_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        AuthUser principal = authUser(userId);
        when(userService.getUserByProviderAndProviderId(principal.getProvider(), principal.getProviderId()))
                .thenReturn(userDto(userId, "display"));

        mockMvc.perform(get("/v1/users/me").with(oauth2Login().oauth2User(principal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.displayName").value("display"));
    }

    // ------------------------------------------------------------- PATCH /v1/users/{id}

    @Test
    void updateUserDisplayName_WhenUnauthenticated_Returns401() throws Exception {
        mockMvc.perform(patch("/v1/users/{userId}", UUID.randomUUID())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"NewName\"}"))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).updateUserDisplayName(any(), any());
    }

    @Test
    void updateUserDisplayName_WhenIdsMismatch_Returns403() throws Exception {
        mockMvc.perform(patch("/v1/users/{userId}", UUID.randomUUID())
                        .with(csrf())
                        .with(oauth2Login().oauth2User(authUser(UUID.randomUUID())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"NewName\"}"))
                .andExpect(status().isForbidden());

        verify(userService, never()).updateUserDisplayName(any(), any());
    }

    @Test
    void updateUserDisplayName_WhenValid_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.updateUserDisplayName(userId, "NewName")).thenReturn(userDto(userId, "NewName"));

        mockMvc.perform(patch("/v1/users/{userId}", userId)
                        .with(csrf())
                        .with(oauth2Login().oauth2User(authUser(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"NewName\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("NewName"));
    }

    @Test
    void updateUserDisplayName_WhenUserNotFound_Returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.updateUserDisplayName(userId, "NewName"))
                .thenThrow(new InvalidUserException("User not found"));

        mockMvc.perform(patch("/v1/users/{userId}", userId)
                        .with(csrf())
                        .with(oauth2Login().oauth2User(authUser(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"NewName\"}"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------ DELETE /v1/users/{id}

    @Test
    void deleteUser_WhenUnauthenticated_Returns401() throws Exception {
        mockMvc.perform(delete("/v1/users/{userId}", UUID.randomUUID()).with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(userService, never()).deleteUser(any());
    }

    @Test
    void deleteUser_WhenIdsMismatch_Returns403() throws Exception {
        mockMvc.perform(delete("/v1/users/{userId}", UUID.randomUUID())
                        .with(csrf())
                        .with(oauth2Login().oauth2User(authUser(UUID.randomUUID()))))
                .andExpect(status().isForbidden());

        verify(userService, never()).deleteUser(any());
    }

    @Test
    void deleteUser_WhenUserNotFound_Returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.deleteUser(userId)).thenThrow(new InvalidUserException("User not found"));

        mockMvc.perform(delete("/v1/users/{userId}", userId)
                        .with(csrf())
                        .with(oauth2Login().oauth2User(authUser(userId))))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteUser_WhenValid_Returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.deleteUser(userId)).thenReturn(userDto(userId, "display"));

        mockMvc.perform(delete("/v1/users/{userId}", userId)
                        .with(csrf())
                        .with(oauth2Login().oauth2User(authUser(userId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()));
    }
}
