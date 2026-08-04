package com.chattychat.Entities;

import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Getter
@Table (name = "Users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, unique = true)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

}
