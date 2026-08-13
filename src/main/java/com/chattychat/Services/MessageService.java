package com.chattychat.Services;

import com.chattychat.Entities.ChatMessage;
import com.chattychat.Entities.Room;
import com.chattychat.Entities.User;
import com.chattychat.Repositories.MessageRepository;
import com.chattychat.Repositories.RoomRepository;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.InboundMessageDTO;
import com.chattychat.dto.OutboundMessageDTO;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RoomRepository roomRepository;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          RoomRepository roomRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
    }

    public OutboundMessageDTO save(String roomName, InboundMessageDTO incoming) {
        User sender = userRepository.findById(incoming.senderId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id: " + incoming.senderId()));
        Room room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown room: " + roomName));

        ChatMessage saved = messageRepository.save(
                new ChatMessage(sender, room, incoming.content(), LocalDateTime.now())
        );

        return new OutboundMessageDTO(saved.getId(), sender.getId(), sender.getFirstName(),
                room.getName(), saved.getContent(), saved.getSentAt());
    }

    public List<OutboundMessageDTO> history(String roomName) {
        return messageRepository.findByRoomNameOrderBySentAtAsc(roomName)
                .stream()
                .map(m -> new OutboundMessageDTO(
                        m.getId(), m.getSender().getId(),
                        m.getSender().getFirstName(), m.getRoom().getName(),
                        m.getContent(), m.getSentAt()))
                .toList();
    }
}
