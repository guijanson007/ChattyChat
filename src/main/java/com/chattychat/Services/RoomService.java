package com.chattychat.Services;

import com.chattychat.Entities.Room;
import com.chattychat.Entities.RoomMember;
import com.chattychat.Entities.User;
import com.chattychat.Exception.InvalidRoomException;
import com.chattychat.Exception.InvalidUserException;
import com.chattychat.Repositories.RoomMemberRepository;
import com.chattychat.Repositories.RoomRepository;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.RoomDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RoomService {
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final UserRepository userRepository;

    public RoomService(RoomRepository roomRepository, RoomMemberRepository roomMemberRepository, UserRepository userRepository) {
        this.roomRepository = roomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.userRepository = userRepository;
    }

    public List<RoomDTO> getAllSubscribedRooms(UUID userId) {
        return roomRepository.findAllByUserId(userId)
                .stream()
                .map(Room::toDTO)
                .toList();
    }

    public RoomDTO createRoom(RoomDTO room) {
        return roomRepository.save(new Room(room.name())).toDTO();
    }

    public void joinRoom(String roomName, UUID userId) {
        Room room = roomRepository.findByName(roomName)
                .orElseThrow(() -> new InvalidRoomException("Room not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("User not found"));

        roomMemberRepository.save(new RoomMember(user, room));
    }

    public List<RoomDTO> getPublicRoomsNotJoined(UUID userId) {
        return roomRepository.findAllByIsPublicNotJoined(userId)
                .stream()
                .map(Room::toDTO)
                .toList();
    }
}
