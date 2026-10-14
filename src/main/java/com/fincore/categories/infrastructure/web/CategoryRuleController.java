package com.fincore.categories.infrastructure.web;

import com.fincore.categories.application.CategoryRuleMatcher;
import com.fincore.categories.application.CategoryRuleService;
import com.fincore.categories.domain.CategoryRule;
import com.fincore.categories.infrastructure.web.dto.CategoryRuleResponse;
import com.fincore.categories.infrastructure.web.dto.CreateCategoryRuleRequest;
import com.fincore.categories.infrastructure.web.dto.TestCategoryRuleRequest;
import com.fincore.categories.infrastructure.web.dto.TestCategoryRuleResponse;
import com.fincore.identity.infrastructure.security.CurrentUserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/category-rules")
public class CategoryRuleController {

    private final CategoryRuleService ruleService;

    public CategoryRuleController(CategoryRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    public ResponseEntity<CategoryRuleResponse> create(@CurrentUserId UUID userId,
                                                       @Valid @RequestBody CreateCategoryRuleRequest request) {
        CategoryRule rule = ruleService.create(userId, request.pattern(), request.categoryId(), request.priority());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(rule));
    }

    @GetMapping
    public List<CategoryRuleResponse> listOwn(@CurrentUserId UUID userId) {
        return ruleService.listOwn(userId).stream().map(CategoryRuleController::toResponse).toList();
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> delete(@CurrentUserId UUID userId, @PathVariable UUID ruleId) {
        ruleService.delete(ruleId, userId);
        return ResponseEntity.noContent().build();
    }

    /** Para probar una descripción y ver qué categoría le pondrían las reglas, sin crear nada. */
    @PostMapping("/test")
    public TestCategoryRuleResponse test(@CurrentUserId UUID userId, @Valid @RequestBody TestCategoryRuleRequest request) {
        return ruleService.matcherFor(userId).match(request.description(), request.kind())
                .map(rule -> new TestCategoryRuleResponse(rule.categoryId(), rule.ruleId(), rule.pattern()))
                .orElse(new TestCategoryRuleResponse(null, null, null));
    }

    private static CategoryRuleResponse toResponse(CategoryRule rule) {
        return new CategoryRuleResponse(rule.getId(), rule.getPattern(), rule.getCategoryId(), rule.getPriority(),
                rule.getCreatedAt());
    }
}
