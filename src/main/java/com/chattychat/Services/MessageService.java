package com.chattychat.Services;

import com.chattychat.Entities.ChatMessage;
import com.chattychat.Entities.Room;
import com.chattychat.Entities.RoomMemberId;
import com.chattychat.Entities.User;
import com.chattychat.Exception.InvalidRoomException;
import com.chattychat.Exception.InvalidUserException;
import com.chattychat.Repositories.MessageRepository;
import com.chattychat.Repositories.RoomMemberRepository;
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
    private final RoomMemberRepository roomMemberRepository;

    public MessageService(MessageRepository messageRepository,
                          UserRepository userRepository,
                          RoomRepository roomRepository, RoomMemberRepository roomMemberRepository) {
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
    }

    public OutboundMessageDTO save(String roomName, UUID senderId, InboundMessageDTO incoming) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new InvalidUserException("Unknown user id: " + senderId));
        Room room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new InvalidRoomException("Unknown room: " + roomName));

        // Check if user is a member of the room
        if (roomMemberRepository.findById(new RoomMemberId(senderId, room.getId())).isEmpty()) {
            throw new InvalidUserException("User is not a member of the room: " + roomName);
        }

        ChatMessage saved = messageRepository.save(
                new ChatMessage(sender, room, incoming.content(), LocalDateTime.now())
        );

        String fromName = sender.getDisplayName() != null ? sender.getDisplayName() : sender.getFirstName();

        return new OutboundMessageDTO(
                saved.getId(),
                sender.getId(),
                fromName,
                saved.getContent(),
                saved.getCreatedAt()
        );
    }

    public List<OutboundMessageDTO> history(String roomName, UUID userId) {
        Room room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new InvalidRoomException("Unknown room: " + roomName));

        if (roomMemberRepository.findById(new RoomMemberId(userId, room.getId())).isEmpty()) {
            throw new InvalidUserException("User is not a member of the room: " + roomName);
        }

        // This returns an empty list [] if no messages exist.
        List<ChatMessage> messages = messageRepository.findByRoomNameOrderByCreatedAtAsc(roomName).get();

        return messages
                .stream()
                .map(m -> {
                    User sender = m.getSender();
                    String fromName = sender.getDisplayName() != null ? sender.getDisplayName() : sender.getFirstName();
                    return new OutboundMessageDTO(
                            m.getId(),
                            sender.getId(),
                            fromName,
                            m.getContent(),
                            m.getCreatedAt()
                    );
                })
                .toList();
    }
}
