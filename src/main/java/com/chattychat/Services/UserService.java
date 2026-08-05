package com.chattychat.Services;

import com.chattychat.Entities.User;
import com.chattychat.Repositories.UserRepository;
import com.chattychat.dto.UserDTO;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }


    public List<User> getUsers() {
        return userRepository.findAll();
    }

    public User getUserById(UUID userId) {
        return userRepository.findById(userId).orElse(null);
    }

    public User addUser(UserDTO user) {
        return userRepository.save(new User(user.id(), user.name()));
    }
}
