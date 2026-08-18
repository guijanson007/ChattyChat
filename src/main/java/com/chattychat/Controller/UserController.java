package com.chattychat.Controller;

import com.chattychat.Services.UserService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.UpdateNameRequestDTO;
import com.chattychat.dto.UserDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/users")
@Tag(name = "User Management", description = "Endpoints for managing users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Get All Users", description = "Retrieve a list of all registered users.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved list of users",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserDTO.class)))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized access", content = @Content)
    })
    @GetMapping
    public ResponseEntity<List<UserDTO>> getUsers() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @Operation(summary = "Get User by ID", description = "Retrieve details of a specific user by UUID.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User found successfully",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format supplied", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @GetMapping("/{userId}")
    public ResponseEntity<UserDTO> getUserById(@PathVariable UUID userId) {
        return ResponseEntity.ok(userService.getUserById(userId));
    }

    @Operation(summary = "Get Current User", description = "Retrieve details of the currently authenticated user.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully fetched authenticated user profile",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))
            ),
            @ApiResponse(responseCode = "401", description = "Authentication token is missing or invalid", content = @Content)
    })
    @GetMapping("/me")
    public ResponseEntity<UserDTO> getCurrentUser(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(userService.getUserByProviderAndProviderId(
                authUser.getProvider(),
                authUser.getProviderId()
        ));
    }

    @Operation(summary = "Update User Display Name", description = "Update the display name of a user. Users can only update their own display name.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Display name updated successfully",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid payload or parameters", content = @Content),
            @ApiResponse(responseCode = "401", description = "User is not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden: Cannot update another user's profile", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @PatchMapping("/{userId}")
    @PreAuthorize("authUser.userId() == #userId")
    public ResponseEntity<UserDTO> updateUserDisplayName(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID userId,
            @RequestBody UpdateNameRequestDTO request) {
        UserDTO updatedUser = userService.updateUserDisplayName(userId, request.displayName());
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Delete User", description = "Delete a user by UUID. Users can only delete their own account.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "User deleted successfully",
                    content = @Content(schema = @Schema(implementation = UserDTO.class))
            ),
            @ApiResponse(responseCode = "400", description = "Invalid UUID format supplied", content = @Content),
            @ApiResponse(responseCode = "401", description = "User is not authenticated", content = @Content),
            @ApiResponse(responseCode = "403", description = "Forbidden: Cannot delete another user's account", content = @Content),
            @ApiResponse(responseCode = "404", description = "User not found", content = @Content)
    })
    @DeleteMapping("/{userId}")
    @PreAuthorize("authUser.userId() == #userId")
    public ResponseEntity<UserDTO> deleteUser(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable UUID userId) {
        return ResponseEntity.ok(userService.deleteUser(userId));
    }
}
