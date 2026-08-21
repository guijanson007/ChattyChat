package com.chattychat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RoomInviteDTO(
        UUID id,
        String roomName,
        UserDTO inviter,
        UserDTO invitee,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
