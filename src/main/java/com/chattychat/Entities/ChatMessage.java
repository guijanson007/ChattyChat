package com.chattychat.Entities;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "sent_at", nullable = false)   // no unique
    private LocalDateTime sentAt;

    public ChatMessage(User sender, Room room, String content, LocalDateTime sentAt) {
        this.sender = sender;
        this.room = room;
        this.content = content;
        this.sentAt = sentAt;
    }
}