package com.fincore.categories.application;

import com.fincore.categories.domain.Category;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.categories.domain.CategoryRule;
import com.fincore.categories.infrastructure.persistence.CategoryJpaRepository;
import com.fincore.categories.infrastructure.persistence.CategoryRuleRepository;
import com.fincore.shared.text.TextNormalizer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CategoryRuleService {

    private static final int MIN_PATTERN_LENGTH = 3;

    private final CategoryRuleRepository ruleRepository;
    private final CategoryJpaRepository categoryRepository;
    private final CategoryService categoryService;

    public CategoryRuleService(CategoryRuleRepository ruleRepository, CategoryJpaRepository categoryRepository,
                               CategoryService categoryService) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
        this.categoryService = categoryService;
    }

    @Transactional
    public CategoryRule create(UUID ownerId, String pattern, UUID categoryId, int priority) {
        String normalized = TextNormalizer.normalize(pattern);
        if (normalized.length() < MIN_PATTERN_LENGTH) {
            // Un patrón de 1 o 2 letras ("A", "DE") casaría con casi cualquier descripción.
            throw new InvalidCategoryRuleException("el patrón debe tener al menos " + MIN_PATTERN_LENGTH + " caracteres");
        }
        categoryService.getVisibleTo(categoryId, ownerId);

        try {
            return ruleRepository.saveAndFlush(new CategoryRule(ownerId, normalized, categoryId, priority));
        } catch (DataIntegrityViolationException e) {
            throw new InvalidCategoryRuleException("ya tienes una regla con el patrón '" + normalized + "'");
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryRule> listOwn(UUID ownerId) {
        return ruleRepository.findAllByOwnerIdOrderByPriorityDescPatternAsc(ownerId);
    }

    @Transactional
    public void delete(UUID ruleId, UUID ownerId) {
        CategoryRule rule = ruleRepository.findById(ruleId)
                .filter(r -> ownerId.equals(r.getOwnerId()))
                .orElseThrow(() -> new CategoryRuleNotFoundException(ruleId));
        ruleRepository.delete(rule);
    }

    /** Carga una vez las reglas aplicables al usuario (suyas y del sistema) con el tipo de su categoría. */
    @Transactional(readOnly = true)
    public CategoryRuleMatcher matcherFor(UUID userId) {
        Map<UUID, Category> categories = categoryRepository.findVisibleTo(userId).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));

        List<CategoryRuleMatcher.CompiledRule> compiled = ruleRepository.findApplicableTo(userId).stream()
                .filter(rule -> categories.containsKey(rule.getCategoryId()))
                .map(rule -> new CategoryRuleMatcher.CompiledRule(rule.getId(), rule.getPattern(), rule.getCategoryId(),
                        categories.get(rule.getCategoryId()).getKind(), !rule.isSystem(), rule.getPriority()))
                .toList();
        return new CategoryRuleMatcher(compiled);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> suggestCategory(UUID userId, String description, CategoryKind kind) {
        return matcherFor(userId).match(description, kind).map(CategoryRuleMatcher.CompiledRule::categoryId);
    }
}
