package com.fincore.categories.infrastructure.web;

import com.fincore.categories.domain.Category;
import com.fincore.categories.infrastructure.web.dto.CategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getParentId(),
                category.isSystem(),
                category.getName(),
                category.getKind(),
                category.getColor(),
                category.getIcon(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }
}
