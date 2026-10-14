package com.fincore.goals.infrastructure.persistence;

import com.fincore.goals.domain.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findAllByGoalIdOrderByDateDescCreatedAtDesc(UUID goalId);
}
