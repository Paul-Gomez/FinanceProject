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
        mockMvc.perform(get("/api/v1/categories").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Alimentación')].system").value(true))
                .andExpect(jsonPath("$[?(@.name == 'Nómina')].kind").value("INCOME"));
    }

    @Test
    void filtersByKind() throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories").param("kind", "INCOME")
                        .header("X-User-Id", userId.toString()))
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

        mockMvc.perform(get("/api/v1/categories").header("X-User-Id", userId.toString()))
                .andExpect(jsonPath("$[?(@.name == '" + name + "')]").isNotEmpty());
        mockMvc.perform(get("/api/v1/categories").header("X-User-Id", otherUserId.toString()))
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
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateCategoryRequest("Otro", null, null))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_READ_ONLY"));

        mockMvc.perform(delete("/api/v1/categories/" + systemId).header("X-User-Id", userId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATEGORY_READ_ONLY"));
    }

    @Test
    void cannotTouchSomeoneElsesCategory() throws Exception {
        UUID mine = createAndGetId(userId, "Privada " + UUID.randomUUID(), CategoryKind.EXPENSE);

        mockMvc.perform(get("/api/v1/categories/" + mine).header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/categories/" + mine).header("X-User-Id", otherUserId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void updatesAndDeletesOwnCategory() throws Exception {
        UUID id = createAndGetId(userId, "Vieja " + UUID.randomUUID(), CategoryKind.EXPENSE);

        mockMvc.perform(put("/api/v1/categories/" + id)
                        .header("X-User-Id", userId.toString())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new UpdateCategoryRequest("Nueva " + id, "#ABCDEF", "star"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.color").value("#ABCDEF"))
                .andExpect(jsonPath("$.icon").value("star"));

        mockMvc.perform(delete("/api/v1/categories/" + id).header("X-User-Id", userId.toString()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/categories/" + id).header("X-User-Id", userId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_FOUND"));
    }

    @Test
    void rejectsInvalidColor() throws Exception {
        create(userId, "Color malo " + UUID.randomUUID(), CategoryKind.EXPENSE, "rojo")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private ResultActions create(UUID user, String name, CategoryKind kind, String color) throws Exception {
        return mockMvc.perform(post("/api/v1/categories")
                .header("X-User-Id", user.toString())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new CreateCategoryRequest(name, kind, color, null))));
    }

    private UUID createAndGetId(UUID user, String name, CategoryKind kind) throws Exception {
        String body = create(user, name, kind, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }

    private UUID findSystemCategoryId(String name) throws Exception {
        String body = mockMvc.perform(get("/api/v1/categories").header("X-User-Id", userId.toString()))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode category : objectMapper.readTree(body)) {
            if (category.get("system").asBoolean() && category.get("name").asText().equals(name)) {
                return UUID.fromString(category.get("id").asText());
            }
        }
        throw new IllegalStateException("No existe la categoría del sistema " + name);
    }
}
