package com.fincore.imports.infrastructure.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincore.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ImportIT extends IntegrationTest {

    private static final DateTimeFormatter ES = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private Tokens user;
    private UUID accountId;
    private String d1;
    private String d2;

    @BeforeEach
    void setUp() throws Exception {
        user = registerAndLogin("tests");
        accountId = createAccount("1000.00");
        d1 = LocalDate.now().minusDays(5).format(ES);
        d2 = LocalDate.now().minusDays(3).format(ES);
    }

    @Test
    void importsASpanishBankExportClassifyingEveryRow() throws Exception {
        String csv = """
                Fecha operación;Fecha valor;Concepto;Importe;Saldo
                %s;%s;COMPRA MERCADONA Pº CASTELLANA;-54,30;945,70
                %s;%s;Nómina octubre;1.500,00;2.445,70
                31/02/2026;31/02/2026;Fecha imposible;-10,00;2.435,70
                %s;%s;Cine;-8,555;2.427,15
                """.formatted(d1, d1, d2, d2, d2, d2);

        upload(csv.getBytes(WINDOWS_1252), "extracto.csv")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.importedRows").value(2))
                .andExpect(jsonPath("$.duplicateRows").value(0))
                .andExpect(jsonPath("$.errorRows").value(2));

        assertBalance("2445.70");
        JsonNode movements = movements();
        JsonNode mercadona = find(movements, "COMPRA MERCADONA Pº CASTELLANA");
        assertThat(mercadona.get("amount").decimalValue()).isEqualByComparingTo("-54.30");
        assertThat(mercadona.get("categoryId").isNull()).isFalse();
        assertThat(find(movements, "Nómina octubre").get("type").asText()).isEqualTo("INCOME");
    }

    @Test
    void reimportingTheSameFileOnlyProducesDuplicates() throws Exception {
        String csv = "Fecha;Concepto;Importe\n%s;Gasolina;-60,00\n%s;Farmacia;-12,40\n".formatted(d1, d2);
        upload(csv.getBytes(StandardCharsets.UTF_8), "a.csv").andExpect(jsonPath("$.importedRows").value(2));

        upload(csv.getBytes(StandardCharsets.UTF_8), "a.csv")
                .andExpect(jsonPath("$.importedRows").value(0))
                .andExpect(jsonPath("$.duplicateRows").value(2));

        assertBalance("927.60");
    }

    @Test
    void twoIdenticalCoffeesTheSameDayAreNotDuplicates() throws Exception {
        String csv = "Fecha;Concepto;Importe\n%s;Café;-1,50\n%s;Café;-1,50\n".formatted(d1, d1);

        upload(csv.getBytes(StandardCharsets.UTF_8), "cafes.csv").andExpect(jsonPath("$.importedRows").value(2));
        upload(csv.getBytes(StandardCharsets.UTF_8), "cafes.csv").andExpect(jsonPath("$.duplicateRows").value(2));

        assertBalance("997.00");
    }

    @Test
    void debitCreditColumnsAndBankReferences() throws Exception {
        String iso1 = LocalDate.now().minusDays(4).toString();
        String csv = """
                Date,Description,Paid out,Paid in,Reference
                %s,Groceries,"1,020.10",,TX-1
                %s,Refund,,25.00,TX-2
                %s,Groceries again (same reference),"1,020.10",,TX-1
                """.formatted(iso1, iso1, iso1);

        upload(csv.getBytes(StandardCharsets.UTF_8), "statement.csv")
                .andExpect(jsonPath("$.importedRows").value(2))
                .andExpect(jsonPath("$.duplicateRows").value(1));

        assertBalance("4.90");
    }

    @Test
    void reportListsEveryRowWithItsResult() throws Exception {
        String csv = "Fecha;Concepto;Importe\n%s;Libros;-30,00\nmal;Error;-1,00\n".formatted(d1);
        String jobId = objectMapper.readTree(upload(csv.getBytes(StandardCharsets.UTF_8), "r.csv")
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        String report = mockMvc.perform(get("/api/v1/imports/" + jobId + "/report").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(report).contains("fila;estado;fecha;importe;descripcion;mensaje");
        assertThat(report).contains("2;IMPORTED");
        assertThat(report).contains("3;ERROR");
        mockMvc.perform(get("/api/v1/imports/" + jobId + "/rows").param("status", "ERROR")
                        .header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].rowNumber").value(3));
    }

    @Test
    void largeFileIsImportedInBatchesWithTheRightBalance() throws Exception {
        StringBuilder csv = new StringBuilder("Fecha;Concepto;Importe\n");
        BigDecimal total = BigDecimal.ZERO;
        for (int i = 1; i <= 1_200; i++) {
            BigDecimal amount = new BigDecimal("-0.01").multiply(BigDecimal.valueOf(i % 7 + 1));
            total = total.add(amount);
            csv.append(d1).append(";Movimiento ").append(i).append(';').append(amount.toPlainString().replace('.', ',')).append('\n');
        }

        upload(csv.toString().getBytes(StandardCharsets.UTF_8), "grande.csv")
                .andExpect(jsonPath("$.importedRows").value(1200));

        assertBalance(new BigDecimal("1000.00").add(total).toPlainString());
    }

    @Test
    void rejectsUnreadableFilesAndOthersAccounts() throws Exception {
        upload("Nombre;Apellidos\nAna;López\n".getBytes(StandardCharsets.UTF_8), "x.csv")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_IMPORT_FILE"));
        upload("cualquier cosa".getBytes(StandardCharsets.UTF_8), "foto.png")
                .andExpect(status().isUnprocessableEntity());

        Tokens other = registerAndLogin("otro");
        mockMvc.perform(multipart("/api/v1/imports")
                        .file(new MockMultipartFile("file", "a.csv", "text/csv", "Fecha;Importe\n01/01/2026;1\n".getBytes()))
                        .param("accountId", accountId.toString())
                        .header("Authorization", other.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void completedImportIsNotified() throws Exception {
        upload("Fecha;Concepto;Importe\n%s;Algo;-5,00\n".formatted(d1).getBytes(StandardCharsets.UTF_8), "n.csv");

        mockMvc.perform(get("/api/v1/notifications").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.content[0].type").value("IMPORT_COMPLETED"));
    }

    private ResultActions upload(byte[] content, String fileName) throws Exception {
        return mockMvc.perform(multipart("/api/v1/imports")
                .file(new MockMultipartFile("file", fileName, "text/csv", content))
                .param("accountId", accountId.toString())
                .header("Authorization", user.bearer()));
    }

    private void assertBalance(String expected) throws Exception {
        String body = mockMvc.perform(get("/api/v1/accounts/" + accountId).header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(body).get("balance").decimalValue()).isEqualByComparingTo(expected);
    }

    private JsonNode movements() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/transactions").param("accountId", accountId.toString())
                        .param("size", "100").header("Authorization", user.bearer()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)).get("content");
    }

    private static JsonNode find(JsonNode movements, String description) {
        for (JsonNode movement : movements) {
            if (description.equals(movement.get("description").asText())) {
                return movement;
            }
        }
        throw new AssertionError("No hay ningún movimiento con descripción " + description);
    }

    private UUID createAccount(String balance) throws Exception {
        String body = mockMvc.perform(post("/api/v1/accounts").header("Authorization", user.bearer())
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("name", "Banco", "type", "BANK",
                                "currencyCode", "EUR", "initialBalance", balance))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(body).get("id").asText());
    }
}
