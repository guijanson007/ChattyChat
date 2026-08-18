package com.chattychat.Entities;

import com.chattychat.dto.RoomDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "rooms")
@AllArgsConstructor
@NoArgsConstructor
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime lastUpdatedAt;
    private boolean isPublic;

    public RoomDTO toDTO() {
        return new RoomDTO(id, name, createdAt, isPublic);
    }

    public Room(String name) {
        this.name = name;
    }

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        lastUpdatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        lastUpdatedAt = LocalDateTime.now();
    }
}
