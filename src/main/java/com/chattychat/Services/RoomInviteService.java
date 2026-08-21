package com.chattychat.Services;

import com.chattychat.Entities.Room;
import com.chattychat.Entities.RoomInvite;
import com.chattychat.Entities.RoomMemberId;
import com.chattychat.Entities.User;
import com.chattychat.Exception.InvalidInviteException;
import com.chattychat.Exception.InvalidRoomException;
import com.chattychat.Exception.InvalidUserException;
import com.chattychat.Repositories.RoomInviteRepository;
import com.chattychat.Repositories.RoomMemberRepository;
import com.chattychat.Repositories.RoomRepository;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.RoomInviteDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RoomInviteService {
    private final RoomInviteRepository roomInviteRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;

    public RoomInviteService(
            RoomRepository roomRepository,
            RoomInviteRepository roomInviteRepository,
            UserRepository userRepository, RoomMemberRepository roomMemberRepository) {
        this.roomInviteRepository = roomInviteRepository;
        this.roomRepository = roomRepository;
        this.userRepository = userRepository;
        this.roomMemberRepository = roomMemberRepository;
    }


    public void createInvite(String room, UUID inviterId, UUID inviteeId) {
        if (inviterId.equals(inviteeId)) {
            throw new InvalidInviteException("You may not invite yourself");
        }

        Room roomObj = roomRepository.findByName(room)
                .orElseThrow(() -> new InvalidRoomException("Room not found"));

        User inviterObj = userRepository.findById(inviterId)
                .orElseThrow(() -> new InvalidUserException("Inviter not found"));

        User inviteeObj = userRepository.findById(inviteeId)
                .orElseThrow(() -> new InvalidUserException("Invitee not found"));

        if (roomMemberRepository.existsById(new RoomMemberId(inviterId, roomObj.getId()))) {
            throw new InvalidInviteException("The inviter is not a member of the room");
        }

        roomInviteRepository.save(new RoomInvite(roomObj, inviterObj, inviteeObj));
    }

    public List<RoomInviteDTO> getInvitesForUser(UUID userId) {
        return roomInviteRepository.findAllByInviteeId(userId)
                .stream()
                .map(RoomInvite::toDTO)
                .toList();
    }
}
