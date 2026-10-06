package com.fincore.categories.application;

import com.fincore.categories.domain.Category;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.categories.infrastructure.persistence.CategoryJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * API pública del módulo categories para otros módulos (transactions, budgets...).
 */
@Service
public class CategoryQueryService {

    private final CategoryJpaRepository categoryRepository;

    public CategoryQueryService(CategoryJpaRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /** Comprueba que el usuario puede usar la categoría y que es del tipo esperado. */
    @Transactional(readOnly = true)
    public void requireUsable(UUID categoryId, UUID userId, CategoryKind expectedKind) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
        if (!category.isVisibleTo(userId)) {
            throw new CategoryAccessDeniedException(categoryId);
        }
        if (category.getKind() != expectedKind) {
            throw new CategoryKindMismatchException(category.getName(), category.getKind(), expectedKind);
        }
    }
}
