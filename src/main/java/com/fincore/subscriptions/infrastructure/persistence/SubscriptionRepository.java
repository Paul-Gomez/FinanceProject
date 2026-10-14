package com.fincore.subscriptions.infrastructure.persistence;

import com.fincore.subscriptions.domain.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

    List<Subscription> findAllByOwnerIdAndActiveTrueOrderByNameAsc(UUID ownerId);

    List<Subscription> findAllByActiveTrue();
}
