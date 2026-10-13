package com.fincore.categories.application;

import com.fincore.categories.domain.Category;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.categories.infrastructure.persistence.CategoryJpaRepository;
import com.fincore.categories.infrastructure.web.dto.CreateCategoryRequest;
import com.fincore.categories.infrastructure.web.dto.UpdateCategoryRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryJpaRepository categoryRepository;

    public CategoryService(CategoryJpaRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category create(UUID ownerId, CreateCategoryRequest request) {
        if (request.parentId() != null) {
            requireValidParent(request.parentId(), ownerId, request);
        }

        Category category = new Category(ownerId, request.parentId(), request.name().trim(),
                request.kind(), request.color(), request.icon());
        return saveOrFailIfDuplicated(category);
    }

    /** Solo un nivel de subcategorías, y del mismo tipo que el padre (no hay "Nómina > Supermercado"). */
    private void requireValidParent(UUID parentId, UUID ownerId, CreateCategoryRequest request) {
        Category parent = getVisibleTo(parentId, ownerId);
        if (!parent.isRoot()) {
            throw new InvalidCategoryParentException("una subcategoría no puede tener a su vez subcategorías");
        }
        if (parent.getKind() != request.kind()) {
            throw new InvalidCategoryParentException(
                    "la categoría padre es de tipo %s y la nueva de tipo %s".formatted(parent.getKind(), request.kind()));
        }
    }

    @Transactional(readOnly = true)
    public List<Category> listVisibleTo(UUID userId, CategoryKind kind) {
        return kind == null
                ? categoryRepository.findVisibleTo(userId)
                : categoryRepository.findVisibleToByKind(userId, kind);
    }

    @Transactional(readOnly = true)
    public Category getVisibleTo(UUID categoryId, UUID userId) {
        Category category = findOrThrow(categoryId);
        if (!category.isVisibleTo(userId)) {
            throw new CategoryAccessDeniedException(categoryId);
        }
        return category;
    }

    @Transactional
    public Category update(UUID categoryId, UUID userId, UpdateCategoryRequest request) {
        Category category = getEditable(categoryId, userId);
        category.update(request.name().trim(), request.color(), request.icon());
        return saveOrFailIfDuplicated(category);
    }

    /**
     * La clave foránea de transactions impide borrar una categoría con movimientos. Se fuerza el
     * flush para que ese error salte aquí y se traduzca a un 409, y no al confirmar (un 500).
     */
    @Transactional
    public void delete(UUID categoryId, UUID userId) {
        try {
            categoryRepository.delete(getEditable(categoryId, userId));
            categoryRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CategoryInUseException(categoryId);
        }
    }

    private Category getEditable(UUID categoryId, UUID userId) {
        Category category = getVisibleTo(categoryId, userId);
        if (category.isSystem()) {
            throw new CategoryReadOnlyException(categoryId);
        }
        return category;
    }

    private Category findOrThrow(UUID categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new CategoryNotFoundException(categoryId));
    }

    private Category saveOrFailIfDuplicated(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new CategoryAlreadyExistsException(category.getName());
        }
    }
}
