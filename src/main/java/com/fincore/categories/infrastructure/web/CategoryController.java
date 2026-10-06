package com.fincore.categories.infrastructure.web;

import com.fincore.categories.application.CategoryService;
import com.fincore.categories.domain.Category;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.categories.infrastructure.web.dto.CategoryResponse;
import com.fincore.categories.infrastructure.web.dto.CreateCategoryRequest;
import com.fincore.categories.infrastructure.web.dto.UpdateCategoryRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {

    // TODO(identity): sustituir esta cabecera por el usuario autenticado (JWT) cuando exista el módulo identity.
    private static final String OWNER_HEADER = "X-User-Id";

    private final CategoryService categoryService;
    private final CategoryMapper categoryMapper;

    public CategoryController(CategoryService categoryService, CategoryMapper categoryMapper) {
        this.categoryService = categoryService;
        this.categoryMapper = categoryMapper;
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> create(
            @RequestHeader(OWNER_HEADER) UUID userId,
            @Valid @RequestBody CreateCategoryRequest request) {
        Category category = categoryService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryMapper.toResponse(category));
    }

    @GetMapping
    public List<CategoryResponse> list(
            @RequestHeader(OWNER_HEADER) UUID userId,
            @RequestParam(required = false) CategoryKind kind) {
        return categoryService.listVisibleTo(userId, kind).stream()
                .map(categoryMapper::toResponse)
                .toList();
    }

    @GetMapping("/{categoryId}")
    public CategoryResponse getById(@RequestHeader(OWNER_HEADER) UUID userId, @PathVariable UUID categoryId) {
        return categoryMapper.toResponse(categoryService.getVisibleTo(categoryId, userId));
    }

    @PutMapping("/{categoryId}")
    public CategoryResponse update(
            @RequestHeader(OWNER_HEADER) UUID userId,
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryMapper.toResponse(categoryService.update(categoryId, userId, request));
    }

    @DeleteMapping("/{categoryId}")
    public ResponseEntity<Void> delete(@RequestHeader(OWNER_HEADER) UUID userId, @PathVariable UUID categoryId) {
        categoryService.delete(categoryId, userId);
        return ResponseEntity.noContent().build();
    }
}
