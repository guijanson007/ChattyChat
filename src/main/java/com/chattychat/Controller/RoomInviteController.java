package com.chattychat.Controller;

import com.chattychat.Services.RoomInviteService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.RoomInviteDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.UUID;

@Controller
public class RoomInviteController {
    private final RoomInviteService roomInviteService;

    public RoomInviteController(RoomInviteService roomInviteService) {
        this.roomInviteService = roomInviteService;
    }

    @Operation(summary = "Create a room invite", description = "Creates an invite for a specific room. The authenticated user must have permission to create invites for the room.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invite created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid room or user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "User is not a member of the room")
    })
    @PostMapping("/v1/rooms/{room}/invites")
    public ResponseEntity<?> createInvite(
            @PathVariable String room,
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody UUID invitedUserId
    ) {
        roomInviteService.createInvite(room, authUser.userId(), invitedUserId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/v1/invites")
    public ResponseEntity<List<RoomInviteDTO>> getInvites(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(roomInviteService.getInvitesForUser(authUser.userId()));
    }
}
