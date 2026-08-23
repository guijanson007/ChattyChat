package com.chattychat.Services;

import com.chattychat.Entities.User;
import com.chattychat.Exception.InvalidUserException;
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


    public List<UserDTO> getUsers() {
        return userRepository.findAll()
                .stream()
                .map(User::toDTO)
                .toList();
    }

    public UserDTO getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(User::toDTO)
                .orElseThrow(() -> new InvalidUserException("User not found"));
    }

    public UserDTO getUserByProviderAndProviderId(String provider, String providerId) {
        return userRepository.findByProviderAndProviderId(provider, providerId)
                .map(User::toDTO)
                .orElseThrow(() -> new InvalidUserException("User not found"));
    }

    public UserDTO updateUserDisplayName(UUID userId, String newDisplayName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("User not found"));

        user.setDisplayName(newDisplayName);
        userRepository.save(user);
        return user.toDTO();
    }

    public UserDTO deleteUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidUserException("User not found"));


        userRepository.delete(user);
        return user.toDTO();
    }

}
