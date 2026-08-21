package com.chattychat.Repositories;

import com.chattychat.Entities.RoomInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomInviteRepository extends JpaRepository<RoomInvite, UUID> {
}
