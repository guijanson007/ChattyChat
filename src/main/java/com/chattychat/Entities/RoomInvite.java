package com.chattychat.Entities;

import com.chattychat.dto.RoomInviteDTO;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@NoArgsConstructor
@Getter
@Setter
@Table(name="room_invites")
public class RoomInvite {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    private Room room;

    @ManyToOne
    @JoinColumn(name = "inviter_id")
    private User inviter;

    @ManyToOne
    @JoinColumn(name = "invitee_id")
    private User invitee;

    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;

    public RoomInvite(Room room, User inviter, User invitee) {
        this.room = room;
        this.inviter = inviter;
        this.invitee = invitee;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        expiresAt = createdAt.plusDays(7); // Invite expires in 7 days
    }

    public RoomInviteDTO toDTO() {
        return new RoomInviteDTO(
                id,
                room.getName(),
                inviter.toDTO(),
                invitee.toDTO(),
                createdAt,
                expiresAt
        );
    }

}
