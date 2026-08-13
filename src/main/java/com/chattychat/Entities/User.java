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
@Table(
        name = "Users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"provider", "provider_id"})
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "provider_id", nullable = false)
    private String providerId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = true)
    private String email;

    public UserDTO toDTO() {
        return new UserDTO(id, firstName, lastName, email);
    }

}
