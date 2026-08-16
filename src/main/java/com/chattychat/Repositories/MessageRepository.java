package com.chattychat.Repositories;

import com.chattychat.Entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<ChatMessage, UUID> {
    Optional<List<ChatMessage>> findByRoomNameOrderBySentAtAsc(String roomName);
}
