package com.chattychat.Entities;

import com.chattychat.dto.UserDTO;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private String providerId;

    @Column(nullable = false)
    private String provider;

    private String firstName;

    private String lastName;

    private String email;

    private String displayName;

    public UserDTO toDTO() {
        return new UserDTO(id, firstName, lastName, email, displayName);
    }
}
