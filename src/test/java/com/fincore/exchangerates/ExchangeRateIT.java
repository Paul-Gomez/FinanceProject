package com.fincore.exchangerates;

import com.fincore.exchangerates.application.ExchangeRateService;
import com.fincore.exchangerates.application.ExchangeRateService.ExchangeRateNotAvailableException;
import com.fincore.shared.money.Money;
import com.fincore.support.IntegrationTest;
import com.fincore.users.domain.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ExchangeRateIT extends IntegrationTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency CHF = Currency.getInstance("CHF");

    @Autowired
    private ExchangeRateService exchangeRates;

    @Test
    void onlyAdminsCanLoadRates() throws Exception {
        Tokens user = registerAndLogin("normal");
        putRate(user, "EUR", "JPY", "2026-10-01", "160.5").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        Tokens admin = registerAndLogin("admin");
        AppUser adminUser = userRepository.findByEmail(admin.email()).orElseThrow();
        adminUser.promoteToAdmin();
        userRepository.save(adminUser);
        Tokens adminSession = login(admin.email(), TEST_PASSWORD, "admin");

        putRate(adminSession, "EUR", "JPY", "2026-10-01", "160.5").andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/exchange-rates").param("base", "EUR").param("quote", "JPY")
                        .header("Authorization", user.bearer()))
                .andExpect(jsonPath("$[0].rate").value(160.5));
    }

    @Test
    void convertsWithTheRateValidOnTheDateOrItsInverse() {
        exchangeRates.save("EUR", "USD", LocalDate.of(2026, 1, 1), new BigDecimal("1.10"), "test");
        exchangeRates.save("EUR", "USD", LocalDate.of(2026, 6, 1), new BigDecimal("1.20"), "test");

        assertThat(exchangeRates.convert(Money.of("100.00", "EUR"), USD, LocalDate.of(2026, 3, 15)))
                .isEqualTo(Money.of("110.00", "USD"));
        assertThat(exchangeRates.convert(Money.of("100.00", "EUR"), USD, LocalDate.of(2026, 7, 1)))
                .isEqualTo(Money.of("120.00", "USD"));
        // Solo existe EUR->USD: USD->EUR usa el inverso (100 / 1.20 = 83.333 -> 83.33)
        assertThat(exchangeRates.convert(Money.of("100.00", "USD"), EUR, LocalDate.of(2026, 7, 1)))
                .isEqualTo(Money.of("83.33", "EUR"));
        assertThat(exchangeRates.convert(Money.of("5.00", "EUR"), EUR, LocalDate.now()))
                .isEqualTo(Money.of("5.00", "EUR"));
    }

    @Test
    void failsExplicitlyWhenThereIsNoRate() {
        exchangeRates.save("GBP", "CHF", LocalDate.of(2026, 5, 1), new BigDecimal("1.13"), "test");

        assertThatThrownBy(() -> exchangeRates.convert(Money.of("10.00", "GBP"), CHF, LocalDate.of(2026, 4, 30)))
                .isInstanceOf(ExchangeRateNotAvailableException.class);
        assertThatThrownBy(() -> exchangeRates.convert(Money.of("10.00", "GBP"), Currency.getInstance("SEK"),
                LocalDate.of(2026, 6, 1))).isInstanceOf(ExchangeRateNotAvailableException.class);
        assertThat(exchangeRates.convert(Money.of("10.00", "GBP"), CHF, LocalDate.of(2026, 6, 1)))
                .isEqualTo(Money.of("11.30", "CHF"));
    }

    @Test
    void userCanChangeTheBaseCurrency() throws Exception {
        Tokens user = registerAndLogin("perfil");

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.baseCurrency").value("EUR"));
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", user.bearer())
                        .contentType("application/json").content("{\"baseCurrency\":\"USD\",\"displayName\":\"Paul\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("USD"))
                .andExpect(jsonPath("$.displayName").value("Paul"));
        mockMvc.perform(patch("/api/v1/users/me").header("Authorization", user.bearer())
                        .contentType("application/json").content("{\"baseCurrency\":\"ZZZ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRENCY"));
    }

    private ResultActions putRate(Tokens tokens, String base, String quote, String date, String rate) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/exchange-rates").header("Authorization", tokens.bearer())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("baseCurrency", base, "quoteCurrency", quote,
                        "date", date, "rate", rate))));
    }
}
