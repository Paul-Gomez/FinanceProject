package com.fincore.users.infrastructure.persistence;

import com.fincore.users.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AppUserJpaRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String normalizedEmail);
}
