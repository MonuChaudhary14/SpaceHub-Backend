package org.spacehub.service;

import org.spacehub.entities.User.User;
import org.spacehub.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final UserRepository userRepository;

    public DashboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User saveUsernameByEmail(String email, String username) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found with email: " + email));

        user.setUsername(username);
        return userRepository.save(user);
    }

}
