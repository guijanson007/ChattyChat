package com.chattychat.Entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "room_members")
@AllArgsConstructor
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

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

}