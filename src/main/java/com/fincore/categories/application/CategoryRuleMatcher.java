package com.fincore.categories.application;

import com.fincore.categories.domain.CategoryKind;
import com.fincore.shared.text.TextNormalizer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Reglas de un usuario ya cargadas y ordenadas, para evaluarlas en memoria (por ejemplo, para
 * todas las filas de una importación) sin ir a la base de datos por cada descripción.
 * Orden: primero las del usuario y después las del sistema; dentro de cada grupo, más prioridad
 * primero y, a igual prioridad, el patrón más largo (más específico: "AMAZON PRIME" antes que "AMAZON").
 */
public final class CategoryRuleMatcher {

    public record CompiledRule(UUID ruleId, String pattern, UUID categoryId, CategoryKind kind,
                               boolean userRule, int priority) {
    }

    private static final Comparator<CompiledRule> ORDER = Comparator
            .comparing(CompiledRule::userRule).reversed()
            .thenComparing(Comparator.comparingInt(CompiledRule::priority).reversed())
            .thenComparing(Comparator.comparingInt((CompiledRule r) -> r.pattern().length()).reversed());

    private final List<CompiledRule> rules;

    public CategoryRuleMatcher(List<CompiledRule> rules) {
        this.rules = rules.stream().sorted(ORDER).toList();
    }

    public Optional<CompiledRule> match(String description, CategoryKind kind) {
        String normalized = TextNormalizer.normalize(description);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return rules.stream()
                .filter(rule -> rule.kind() == kind && normalized.contains(rule.pattern()))
                .findFirst();
    }
}
