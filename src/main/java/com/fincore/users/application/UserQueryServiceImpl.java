package com.fincore.users.application;

import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserQueryServiceImpl implements UserQueryService {

    private final AppUserJpaRepository repository;

    public UserQueryServiceImpl(AppUserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsById(UUID userId) {
        return repository.existsById(userId);
    }
}
