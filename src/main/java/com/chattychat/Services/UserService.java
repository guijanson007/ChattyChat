package com.chattychat.Services;

import com.chattychat.Entities.User;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.UserDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    public List<UserDTO> getUsers() {
        return userRepository.findAll()
                .stream()
                .map(User::toDTO)
                .collect(Collectors.toList());
    }

    public UserDTO getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(User::toDTO)
                .orElse(null);
    }

    public UserDTO addUser(UserDTO user) {
        return userRepository.save(new User(user.id(), user.name())).toDTO();
    }
}
