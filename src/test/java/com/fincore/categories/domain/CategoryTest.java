package com.fincore.categories.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTest {

    @Test
    void usesDefaultColorWhenNoneIsGiven() {
        Category category = new Category(UUID.randomUUID(), "Mascotas", CategoryKind.EXPENSE, null, null);

        assertThat(category.getColor()).isEqualTo(Category.DEFAULT_COLOR);
    }

    @Test
    void systemCategoryIsVisibleToEveryone() {
        Category system = new Category(null, "Ocio", CategoryKind.EXPENSE, "#E91E63", null);

        assertThat(system.isSystem()).isTrue();
        assertThat(system.isVisibleTo(UUID.randomUUID())).isTrue();
    }

    @Test
    void customCategoryIsOnlyVisibleToItsOwner() {
        UUID owner = UUID.randomUUID();
        Category custom = new Category(owner, "Mascotas", CategoryKind.EXPENSE, null, null);

        assertThat(custom.isSystem()).isFalse();
        assertThat(custom.isVisibleTo(owner)).isTrue();
        assertThat(custom.isVisibleTo(UUID.randomUUID())).isFalse();
    }
}
