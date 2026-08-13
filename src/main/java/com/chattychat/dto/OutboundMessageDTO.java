package com.chattychat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboundMessageDTO(
        UUID id,
        UUID senderId,
        String from,
        String content,
        LocalDateTime sentAt
) {
}
