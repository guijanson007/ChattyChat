package com.chattychat.Services;

import com.chattychat.Entities.*;
import com.chattychat.Exception.InvalidInviteException;
import com.chattychat.Exception.InvalidRoomException;
import com.chattychat.Exception.InvalidUserException;
import com.chattychat.Exception.InviteNotFoundException;
import com.chattychat.Repositories.RoomInviteRepository;
import com.chattychat.Repositories.RoomMemberRepository;
import com.chattychat.Repositories.RoomRepository;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.RoomInviteDTO;
import jakarta.transaction.Transactional;
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


    public RoomInviteDTO createInvite(String room, UUID inviterId, UUID inviteeId) {
        if (inviterId.equals(inviteeId)) {
            throw new InvalidInviteException("You may not invite yourself");
        }

        Room roomObj = roomRepository.findByName(room)
                .orElseThrow(() -> new InvalidRoomException("Room not found"));

        User inviterObj = userRepository.findById(inviterId)
                .orElseThrow(() -> new InvalidUserException("Inviter not found"));

        User inviteeObj = userRepository.findById(inviteeId)
                .orElseThrow(() -> new InvalidUserException("Invitee not found"));

        if (!roomMemberRepository.existsById(new RoomMemberId(inviterId, roomObj.getId()))) {
            throw new InvalidInviteException("The inviter is not a member of the room");
        }

       return roomInviteRepository.save(new RoomInvite(roomObj, inviterObj, inviteeObj)).toDTO();
    }

    public List<RoomInviteDTO> getInvitesForUser(UUID userId) {
        return roomInviteRepository.findAllByInvitee_Id(userId)
                .stream()
                .map(RoomInvite::toDTO)
                .toList();
    }

    @Transactional
    public void acceptInvite(UUID inviteId, UUID userId) {
        // 1. find the invite
        RoomInvite inviteObj = roomInviteRepository.findById(inviteId)
                .orElseThrow(() -> new InviteNotFoundException("Invite not found"));

        // 2. check ownership and delete the invite
        if (!inviteObj.getInvitee().getId().equals(userId)) {
            throw new InvalidInviteException("You may not accept someone else's invite");
        }
        roomInviteRepository.deleteById(inviteId);
        roomInviteRepository.flush();

        // 3. find the user
        User userObj = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("User not found"));

        // 4. add the user to the room
        roomMemberRepository.save(new RoomMember(userObj, inviteObj.getRoom()));
    }

    public void declineInvite(UUID inviteId, UUID userId) {
        RoomInvite inviteObj = roomInviteRepository.findById(inviteId)
                .orElseThrow(() -> new InviteNotFoundException("Invite not found"));

        if (!inviteObj.getInvitee().getId().equals(userId)) {
            throw new InvalidInviteException("You may not decline someone else's invite");
        }
        roomInviteRepository.deleteById(inviteId);
        roomInviteRepository.flush();
    }
}
