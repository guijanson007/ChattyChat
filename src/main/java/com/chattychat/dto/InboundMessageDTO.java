package com.chattychat.dto;

import java.util.UUID;

public record InboundMessageDTO(UUID senderId, String content) { }