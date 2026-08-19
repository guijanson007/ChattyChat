package com.chattychat.Repositories;

import com.chattychat.Entities.RoomMember;
import com.chattychat.Entities.RoomMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMemberId> {

}
