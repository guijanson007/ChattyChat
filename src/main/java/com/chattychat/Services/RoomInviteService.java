package com.chattychat.Services;

import com.chattychat.Entities.Room;
import com.chattychat.Entities.RoomInvite;
import com.chattychat.Entities.User;
import com.chattychat.Exception.InvalidRoomException;
import com.chattychat.Exception.InvalidUserException;
import com.chattychat.Repositories.RoomInviteRepository;
import com.chattychat.Repositories.RoomRepository;
import com.chattychat.Repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RoomInviteService {
    private final RoomInviteRepository roomInviteRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public RoomInviteService(
            RoomRepository roomRepository,
            RoomInviteRepository roomInviteRepository,
            UserRepository userRepository) {
        this.roomInviteRepository = roomInviteRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
    }


    public void createInvite(String room, UUID userId) {
        Room roomObj = roomRepository.findByName(room)
                .orElseThrow(() -> new InvalidRoomException("Room not found"));

        User userObj = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("User not found"));

        roomInviteRepository.save(new RoomInvite(roomObj, userObj.getEmail()));
    }

}
