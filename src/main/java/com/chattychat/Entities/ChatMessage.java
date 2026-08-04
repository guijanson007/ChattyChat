package com.chattychat.Entities;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "ChatMessages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Long id;

    @OneToOne
    @JoinColumn(name="sender_id", referencedColumnName="id")
    private User sender;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "status", nullable = false)
    private boolean status;

    @Column(name = "created_at", nullable = false, unique = true)
    private LocalDateTime createdAt;

    @Column(name = "username", nullable = false)
    private String username;

}
