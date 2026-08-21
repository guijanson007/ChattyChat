package com.chattychat.Controller;

import com.chattychat.Services.RoomInviteService;
import com.chattychat.dto.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

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
            @ApiResponse(responseCode = "401", description = "Unauthorized access")
    })
    @PostMapping("/v1/rooms/{room}/invites")
    public ResponseEntity<?> createInvite(
            @PathVariable String room,
            @AuthenticationPrincipal AuthUser authUser
    ) {
        roomInviteService.createInvite(room, authUser.userId());
        return ResponseEntity.ok().build();
    }
}
