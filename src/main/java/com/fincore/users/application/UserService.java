package com.fincore.users.application;

import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final AppUserJpaRepository userRepository;

    public UserService(AppUserJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppUser getById(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }
}
