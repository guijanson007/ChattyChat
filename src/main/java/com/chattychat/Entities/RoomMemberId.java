package com.chattychat.Entities;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class RoomMemberId implements Serializable {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "room_id")
    private UUID roomId;

    protected RoomMemberId() {}   // JPA needs the no-arg ctor

    public RoomMemberId(UUID userId, UUID roomId) {
        this.userId = userId;
        this.roomId = roomId;
    }

    // getters

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RoomMemberId that)) return false;
        return Objects.equals(userId, that.userId)
                && Objects.equals(roomId, that.roomId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roomId);
    }
}