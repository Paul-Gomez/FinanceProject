package com.fincore.categories.infrastructure.web;

import com.fincore.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.categories.domain.CategoryKind;
import com.fincore.categories.infrastructure.web.dto.CreateCategoryRequest;
import com.fincore.categories.infrastructure.web.dto.UpdateCategoryRequest;
import com.fincore.users.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CategoryControllerIT extends IntegrationTest {

    private UUID userId;
    private UUID otherUserId;

    @BeforeEach
    void setUp() {
        userId = userRepository.save(new AppUser("user-" + UUID.randomUUID() + "@fincore.test")).getId();
        otherUserId = userRepository.save(new AppUser("other-" + UUID.randomUUID() + "@fincore.test")).getId();
    }

    @Test
    void listsSystemCategoriesForAnyUser() throws Exception {
        mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Alimentación')].system").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Nómina')].kind").value("INCOME"));
    }

    @Test
    void filtersByKind() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories").param("kind", "INCOME")
                        .header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        for (JsonNode category : objectMapper.readTree(body)) {
            assertThat(category.get("kind").asText()).isEqualTo("INCOME");
        }
    }

    @Test
    void customCategoryIsOnlyVisibleToItsOwner() throws Exception {
        String name = "Mascotas " + UUID.randomUUID();
        create(userId, name, CategoryKind.EXPENSE, "#112233").andExpect(status().isCreated())
                .andExpect(jsonPath("$.system").value(false))
                .andExpect(jsonPath("$.color").value("#112233"));

        mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(userId)))
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isNotEmpty());
        mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(otherUserId)))
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isEmpty());
    }

    @Test
    void rejectsDuplicateNameOfSameKindIgnoringCase() throws Exception {
        String name = "Gimnasio " + UUID.randomUUID();
        create(userId, name, CategoryKind.EXPENSE, null).andExpect(status().isCreated());

        create(userId, name.toUpperCase(), CategoryKind.EXPENSE, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_ALREADY_EXISTS"));

        create(userId, name, CategoryKind.INCOME, null).andExpect(status().isCreated());
        create(otherUserId, name, CategoryKind.EXPENSE, null).andExpect(status().isCreated());
    }

    @Test
    void systemCategoriesCannotBeEditedOrDeleted() throws Exception {
        UUID systemId = findSystemCategoryId("Ocio");

        mockMvc.perform(put("/api/v1/categories/" + systemId)
                        .header("Authorization", bearer(userId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateCategoryRequest("Otro", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_READ_ONLY"));

        mockMvc.perform(delete("/api/v1/categories/" + systemId).header("Authorization", bearer(userId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_READ_ONLY"));
    }

    @Test
    void cannotTouchSomeoneElsesCategory() throws Exception {
        UUID mine = createAndGetId(userId, "Privada " + UUID.randomUUID(), CategoryKind.EXPENSE);

        mockMvc.perform(get("/api/v1/categories/" + mine).header("Authorization", bearer(otherUserId)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/categories/" + mine).header("Authorization", bearer(otherUserId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesAndDeletesOwnCategory() throws Exception {
        UUID id = createAndGetId(userId, "Vieja " + UUID.randomUUID(), CategoryKind.EXPENSE);

        mockMvc.perform(put("/api/v1/categories/" + id)
                        .header("Authorization", bearer(userId))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateCategoryRequest("Nueva " + id, "#ABCDEF", "star"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#ABCDEF"))
                .andExpect(jsonPath("$.icon").value("star"));

        mockMvc.perform(delete("/api/v1/categories/" + id).header("Authorization", bearer(userId)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/categories/" + id).header("Authorization", bearer(userId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void createsSubcategoryUnderASystemCategory() throws Exception {
        UUID food = findSystemCategoryId("Alimentación");

        createChild(userId, "Supermercado", CategoryKind.EXPENSE, food)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.parentId").value(food.toString()))
                .andExpect(jsonPath("$.system").value(false));
    }

    @Test
    void sameNameIsAllowedUnderDifferentParents() throws Exception {
        createChild(userId, "Otros", CategoryKind.EXPENSE, findSystemCategoryId("Ocio")).andExpect(status().isCreated());
        createChild(userId, "Otros", CategoryKind.EXPENSE, findSystemCategoryId("Compras")).andExpect(status().isCreated());
        createChild(userId, "otros", CategoryKind.EXPENSE, findSystemCategoryId("Compras"))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsSecondLevelAndKindMismatch() throws Exception {
        UUID transport = findSystemCategoryId("Transporte");
        String child = createChild(userId, "Gasolina", CategoryKind.EXPENSE, transport)
                .andReturn().getResponse().getContentAsString();
        UUID childId = UUID.fromString(objectMapper.readTree(child).get("id").asText());

        createChild(userId, "Repsol", CategoryKind.EXPENSE, childId)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_PARENT"));
        createChild(userId, "Bonus", CategoryKind.INCOME, transport)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_CATEGORY_PARENT"));
    }

    @Test
    void cannotUseSomeoneElsesCategoryAsParent() throws Exception {
        UUID foreign = createAndGetId(otherUserId, "Ajena " + UUID.randomUUID(), CategoryKind.EXPENSE);

        createChild(userId, "Hija", CategoryKind.EXPENSE, foreign).andExpect(status().isForbidden());
    }

    @Test
    void parentWithSubcategoriesCannotBeDeleted() throws Exception {
        UUID parent = createAndGetId(userId, "Viajes " + UUID.randomUUID(), CategoryKind.EXPENSE);
        createChild(userId, "Hoteles", CategoryKind.EXPENSE, parent).andExpect(status().isCreated());

        mockMvc.perform(delete("/api/v1/categories/" + parent).header("Authorization", bearer(userId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CATEGORY_IN_USE"));
    }

    @Test
    void rejectsInvalidColor() throws Exception {
        create(userId, "Color malo " + UUID.randomUUID(), CategoryKind.EXPENSE, "rojo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ResultActions createChild(UUID user, String name, CategoryKind kind, UUID parentId) throws Exception {
        return mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", bearer(user))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new CreateCategoryRequest(name, kind, parentId, null, null))));
    }

    private ResultActions create(UUID user, String name, CategoryKind kind, String color) throws Exception {
        return mockMvc.perform(post("/api/v1/categories")
                .header("Authorization", bearer(user))
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new CreateCategoryRequest(name, kind, null, color, null))));
    }

    private UUID createAndGetId(UUID user, String name, CategoryKind kind) throws Exception {
        String body = create(user, name, kind, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID findSystemCategoryId(String name) throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories").header("Authorization", bearer(userId)))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (JsonNode category : objectMapper.readTree(body)) {
            if (category.get("system").asBoolean() && category.get("name").asText().equals(name)) {
                return UUID.fromString(category.get("id").asText());
            }
        }
        throw new IllegalStateException("No existe la categoría del sistema " + name);
    }
}
