package com.fincore.categories.infrastructure.persistence;

import com.fincore.categories.domain.CategoryRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, UUID> {

    @Query("select r from CategoryRule r where r.ownerId is null or r.ownerId = :ownerId")
    List<CategoryRule> findApplicableTo(@Param("ownerId") UUID ownerId);

    List<CategoryRule> findAllByOwnerIdOrderByPriorityDescPatternAsc(UUID ownerId);
}
