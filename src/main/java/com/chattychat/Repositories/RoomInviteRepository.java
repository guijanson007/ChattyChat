package com.chattychat.Repositories;

import com.chattychat.Entities.RoomInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomInviteRepository extends JpaRepository<RoomInvite, UUID> {
    List<RoomInvite> findAllByInvitee_Id(UUID inviteeId);
}
