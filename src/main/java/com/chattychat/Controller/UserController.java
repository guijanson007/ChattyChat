package com.chattychat.Controller;

import com.chattychat.Services.UserService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.UpdateNameRequestDTO;
import com.chattychat.dto.UserDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserDTO>> getUsers() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(@AuthenticationPrincipal AuthUser authUser) {
        if (authUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserDTO userDTO = userService.getUserByProviderAndProviderId(
                authUser.getProvider(),
                authUser.getProviderId()
        );

        if (userDTO == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(userDTO);
    }

    @PatchMapping("/{userId}")
    public ResponseEntity<UserDTO> updateUserDisplayName(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID userId,
            @RequestBody UpdateNameRequestDTO request) {

        if (authUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (userId == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        if (!userId.equals(authUser.getUserId()))
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        UserDTO updatedUser = userService.updateUserDisplayName(userId, request.displayName());
        return ResponseEntity.ok(updatedUser);
    }


}
