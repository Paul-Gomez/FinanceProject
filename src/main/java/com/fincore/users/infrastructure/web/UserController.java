package com.fincore.users.infrastructure.web;

import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.users.application.UserService;
import com.fincore.users.infrastructure.web.dto.UserResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserService userService;
    private final UserMapper userMapper;

    public UserController(UserService userService, UserMapper userMapper) {
        this.userService = userService;
        this.userMapper = userMapper;
    }

    public record UpdateProfileRequest(@Size(max = 100) String displayName,
                                       @Pattern(regexp = "[A-Z]{3}") String baseCurrency) {
    }

    @GetMapping("/me")
    public UserResponse me(@CurrentUserId UUID userId) {
        return userMapper.toResponse(userService.getById(userId));
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@CurrentUserId UUID userId, @Valid @RequestBody UpdateProfileRequest request) {
        return userMapper.toResponse(userService.updateProfile(userId, request.displayName(), request.baseCurrency()));
    }
}
