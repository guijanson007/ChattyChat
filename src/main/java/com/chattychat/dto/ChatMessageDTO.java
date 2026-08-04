package com.chattychat.dto;

import java.time.LocalDateTime;

public record ChatMessageDTO(String from, String content, LocalDateTime timestamp) {

}
