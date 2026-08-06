package com.chattychat.Repositories;

import com.chattychat.Entities.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findByRoomNameOrderBySentAtAsc(String roomName);
}
