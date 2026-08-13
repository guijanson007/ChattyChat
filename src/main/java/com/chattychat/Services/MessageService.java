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
import java.util.UUID;

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

    public OutboundMessageDTO save(String roomName, UUID senderId, InboundMessageDTO incoming) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user id: " + senderId));
        Room room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown room: " + roomName));

        ChatMessage saved = messageRepository.save(
                new ChatMessage(sender, room, incoming.content(), LocalDateTime.now())
        );

        String fromName = sender.getDisplayName() != null ? sender.getDisplayName() : sender.getFirstName();

        return new OutboundMessageDTO(
                saved.getId(),
                sender.getId(),
                fromName,
                saved.getContent(),
                saved.getSentAt()
        );
    }

    public List<OutboundMessageDTO> history(String roomName) {
        return messageRepository.findByRoomNameOrderBySentAtAsc(roomName)
                .stream()
                .map(m -> {
                    User sender = m.getSender();
                    String fromName = sender.getDisplayName() != null ? sender.getDisplayName() : sender.getFirstName();
                    return new OutboundMessageDTO(
                            m.getId(),
                            sender.getId(),
                            fromName,
                            m.getContent(),
                            m.getSentAt()
                    );
                })
                .toList();
    }
}
