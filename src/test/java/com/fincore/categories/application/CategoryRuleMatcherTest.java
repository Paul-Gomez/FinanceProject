package com.fincore.categories.application;

import com.fincore.categories.application.CategoryRuleMatcher.CompiledRule;
import com.fincore.categories.domain.CategoryKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryRuleMatcherTest {

    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID MY_SUPERMARKET = UUID.randomUUID();
    private static final UUID SHOPPING = UUID.randomUUID();
    private static final UUID SUBSCRIPTIONS = UUID.randomUUID();

    private static CompiledRule system(String pattern, UUID category) {
        return new CompiledRule(UUID.randomUUID(), pattern, category, CategoryKind.EXPENSE, false, 0);
    }

    @Test
    void matchesIgnoringCaseAndAccents() {
        CategoryRuleMatcher matcher = new CategoryRuleMatcher(List.of(system("MERCADONA", FOOD)));

        assertThat(matcher.match("compra mercadona 1234", CategoryKind.EXPENSE)).map(CompiledRule::categoryId).contains(FOOD);
        assertThat(matcher.match("Gasolinera", CategoryKind.EXPENSE)).isEmpty();
    }

    @Test
    void userRulesWinOverSystemRules() {
        CompiledRule mine = new CompiledRule(UUID.randomUUID(), "MERCADONA", MY_SUPERMARKET, CategoryKind.EXPENSE, true, 0);
        CategoryRuleMatcher matcher = new CategoryRuleMatcher(List.of(system("MERCADONA", FOOD), mine));

        assertThat(matcher.match("MERCADONA", CategoryKind.EXPENSE)).map(CompiledRule::categoryId).contains(MY_SUPERMARKET);
    }

    @Test
    void longerPatternWinsAtSamePriority() {
        CategoryRuleMatcher matcher = new CategoryRuleMatcher(List.of(
                system("AMAZON", SHOPPING), system("AMAZON PRIME", SUBSCRIPTIONS)));

        assertThat(matcher.match("AMAZON PRIME VIDEO", CategoryKind.EXPENSE)).map(CompiledRule::categoryId).contains(SUBSCRIPTIONS);
        assertThat(matcher.match("AMAZON MARKETPLACE", CategoryKind.EXPENSE)).map(CompiledRule::categoryId).contains(SHOPPING);
    }

    @Test
    void onlyRulesOfTheRightKindApply() {
        CategoryRuleMatcher matcher = new CategoryRuleMatcher(List.of(system("NETFLIX", SUBSCRIPTIONS)));

        assertThat(matcher.match("DEVOLUCION NETFLIX", CategoryKind.INCOME)).isEmpty();
    }
}
