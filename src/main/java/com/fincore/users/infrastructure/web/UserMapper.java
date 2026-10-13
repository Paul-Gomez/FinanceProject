package com.fincore.users.infrastructure.web;

import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.web.dto.UserResponse;
import org.springframework.stereotype.Component;

@Component
public class UserMapper {

    public UserResponse toResponse(AppUser user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                user.isEmailVerified(), user.getCreatedAt());
    }
}
