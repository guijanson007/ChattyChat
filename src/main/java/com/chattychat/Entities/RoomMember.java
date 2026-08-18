package com.chattychat.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "room_members")
@NoArgsConstructor
public class RoomMember {

    @EmbeddedId
    private RoomMemberId id = new RoomMemberId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User member;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("roomId")
    @JoinColumn(name = "room_id")
    private Room room;

    private LocalDateTime joinedAt;

    // Custom constructor for easier instantiation
    public RoomMember(User member, Room room) {
        this.member = member;
        this.room = room;
        this.joinedAt = LocalDateTime.now();
        // Manually sync the composite ID to avoid issues before Hibernate flushes
        this.id = new RoomMemberId(member.getId(), room.getId());
    }
}
