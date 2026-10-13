package com.fincore.users.infrastructure.web;

import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.users.application.UserService;
import com.fincore.users.infrastructure.web.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
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

    @GetMapping("/me")
    public UserResponse me(@CurrentUserId UUID userId) {
        return userMapper.toResponse(userService.getById(userId));
    }
}
