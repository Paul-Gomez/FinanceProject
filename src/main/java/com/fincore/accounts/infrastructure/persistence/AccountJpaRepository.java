package com.fincore.accounts.infrastructure.persistence;

import com.fincore.accounts.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<Account, UUID> {

    List<Account> findAllByOwnerId(UUID ownerId);
}
