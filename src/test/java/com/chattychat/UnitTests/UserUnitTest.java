package com.chattychat.UnitTests;

import com.chattychat.Controller.UserController;
import com.chattychat.Services.UserService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.UpdateNameRequestDTO;
import com.chattychat.dto.UserDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserUnitTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    @Test
    void getUsers_ReturnsListOfUsers() {
        List<UserDTO> expectedUsers = List.of(mock(UserDTO.class));
        when(userService.getUsers()).thenReturn(expectedUsers);

        ResponseEntity<List<UserDTO>> response = userController.getUsers();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedUsers);
    }

    @Test
    void getUserById_WhenUserExists_Returns200() {
        UUID userId = UUID.randomUUID();
        UserDTO expectedUser = mock(UserDTO.class);
        when(userService.getUserById(userId)).thenReturn(expectedUser);

        ResponseEntity<UserDTO> response = userController.getUserById(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedUser);
    }

    @Test
    void getUserById_WhenUserDoesNotExist_Returns404() {
        UUID userId = UUID.randomUUID();
        when(userService.getUserById(userId)).thenReturn(null);

        ResponseEntity<UserDTO> response = userController.getUserById(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getCurrentUser_WhenAuthUserNull_Returns401() {
        ResponseEntity<UserDTO> response = userController.getCurrentUser(null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getCurrentUser_WhenUserNotFoundInDb_Returns401() {
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getProvider()).thenReturn("google");
        when(authUser.getProviderId()).thenReturn("12345");
        when(userService.getUserByProviderAndProviderId("google", "12345")).thenReturn(null);

        ResponseEntity<UserDTO> response = userController.getCurrentUser(authUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void getCurrentUser_WhenValid_Returns200() {
        AuthUser authUser = mock(AuthUser.class);
        UserDTO expectedUser = mock(UserDTO.class);

        when(authUser.getProvider()).thenReturn("google");
        when(authUser.getProviderId()).thenReturn("12345");
        when(userService.getUserByProviderAndProviderId("google", "12345")).thenReturn(expectedUser);

        ResponseEntity<UserDTO> response = userController.getCurrentUser(authUser);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedUser);
    }

    @Test
    void updateUserDisplayName_WhenAuthUserNull_Returns401() {
        UUID userId = UUID.randomUUID();
        UpdateNameRequestDTO request = new UpdateNameRequestDTO("NewName");

        ResponseEntity<UserDTO> response = userController.updateUserDisplayName(null, userId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userService);
    }

    @Test
    void updateUserDisplayName_WhenIdsMismatch_Returns403() {
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getUserId()).thenReturn(UUID.randomUUID());

        UUID pathUserId = UUID.randomUUID();
        UpdateNameRequestDTO request = new UpdateNameRequestDTO("NewName");

        ResponseEntity<UserDTO> response = userController.updateUserDisplayName(authUser, pathUserId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(userService);
    }

    @Test
    void updateUserDisplayName_WhenValid_Returns200() {
        UUID userId = UUID.randomUUID();
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getUserId()).thenReturn(userId);

        UpdateNameRequestDTO request = new UpdateNameRequestDTO("NewName");
        UserDTO expectedUser = mock(UserDTO.class);

        when(userService.updateUserDisplayName(userId, "NewName")).thenReturn(expectedUser);

        ResponseEntity<UserDTO> response = userController.updateUserDisplayName(authUser, userId, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedUser);
    }

    @Test
    void deleteUser_WhenAuthUserNull_Returns401() {
        ResponseEntity<UserDTO> response = userController.deleteUser(null, UUID.randomUUID());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verifyNoInteractions(userService);
    }

    @Test
    void deleteUser_WhenIdsMismatch_Returns403() {
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getUserId()).thenReturn(UUID.randomUUID());

        UUID pathUserId = UUID.randomUUID();

        ResponseEntity<UserDTO> response = userController.deleteUser(authUser, pathUserId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verifyNoInteractions(userService);
    }

    @Test
    void deleteUser_WhenUserNotFound_Returns404() {
        UUID userId = UUID.randomUUID();
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getUserId()).thenReturn(userId);

        when(userService.deleteUser(userId)).thenReturn(null);

        ResponseEntity<UserDTO> response = userController.deleteUser(authUser, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deleteUser_WhenValid_Returns200() {
        UUID userId = UUID.randomUUID();
        AuthUser authUser = mock(AuthUser.class);
        when(authUser.getUserId()).thenReturn(userId);

        UserDTO expectedUser = mock(UserDTO.class);
        when(userService.deleteUser(userId)).thenReturn(expectedUser);

        ResponseEntity<UserDTO> response = userController.deleteUser(authUser, userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedUser);
    }
}
