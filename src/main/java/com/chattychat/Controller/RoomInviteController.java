package com.chattychat.Controller;

import com.chattychat.Services.RoomInviteService;
import com.chattychat.dto.AuthUser;
import com.chattychat.dto.CreateInviteRequestDTO;
import com.chattychat.dto.RoomInviteDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class RoomInviteController {
    private final RoomInviteService roomInviteService;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomInviteController(
            RoomInviteService roomInviteService,
            SimpMessagingTemplate messagingTemplate) {
        this.roomInviteService = roomInviteService;
        this.messagingTemplate = messagingTemplate;
    }

    @Operation(summary = "Create a room invite", description = "Creates an invite for a specific room. The authenticated user must have permission to create invites for the room.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invite created successfully"),
            @ApiResponse(responseCode = "403", description = "Invite is not allowed for the authenticated user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "Room or user not found")
    })
    @PostMapping("/v1/rooms/{room}/invites")
    public ResponseEntity<?> createInvite(
            @PathVariable String room,
            @AuthenticationPrincipal AuthUser authUser,
            @RequestBody CreateInviteRequestDTO inviteRequestDTO
    ) {
        RoomInviteDTO createdInvite = roomInviteService.createInvite(room, authUser.userId(), inviteRequestDTO.invitedUserId());

        // Push invite to the user's queue via the existing WS connection
        messagingTemplate.convertAndSendToUser(
                inviteRequestDTO.invitedUserId().toString(),
                "/queue/invites",
                createdInvite
        );
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Get room invites for the authenticated user", description = "Retrieves a list of room invites for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved invites",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = RoomInviteDTO.class)))
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @GetMapping("/v1/invites")
    public ResponseEntity<List<RoomInviteDTO>> getInvites(@AuthenticationPrincipal AuthUser authUser) {
        return ResponseEntity.ok(roomInviteService.getInvitesForUser(authUser.userId()));
    }

    @Operation(summary = "Accept a room invite", description = "Accepts a room invite for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invite accepted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid invite or user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "Invite not found")
    })
    @PostMapping("/v1/invites/{id}/accept")
    public ResponseEntity<?> acceptInvite(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        roomInviteService.acceptInvite(id, authUser.getUserId());
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Decline a room invite", description = "Declines a room invite for the authenticated user.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Invite declined successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid invite or user"),
            @ApiResponse(responseCode = "401", description = "Unauthorized access"),
            @ApiResponse(responseCode = "404", description = "Invite not found")
    })
    @PostMapping("/v1/invites/{id}/decline")
    public ResponseEntity<?> declineInvite(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthUser authUser) {
        roomInviteService.declineInvite(id, authUser.getUserId());
        return ResponseEntity.ok().build();
    }

}
