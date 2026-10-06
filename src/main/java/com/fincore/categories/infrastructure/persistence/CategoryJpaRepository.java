package com.fincore.categories.infrastructure.persistence;

import com.fincore.categories.domain.Category;
import com.fincore.categories.domain.CategoryKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CategoryJpaRepository extends JpaRepository<Category, UUID> {

    @Query("select c from Category c where c.ownerId is null or c.ownerId = :ownerId order by c.name")
    List<Category> findVisibleTo(@Param("ownerId") UUID ownerId);

    @Query("""
            select c from Category c
            where (c.ownerId is null or c.ownerId = :ownerId) and c.kind = :kind
            order by c.name
            """)
    List<Category> findVisibleToByKind(@Param("ownerId") UUID ownerId, @Param("kind") CategoryKind kind);
}
