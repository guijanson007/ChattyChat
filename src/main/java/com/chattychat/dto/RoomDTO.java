package com.chattychat.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record RoomDTO(UUID id, String name, LocalDateTime createdAt) {
}
