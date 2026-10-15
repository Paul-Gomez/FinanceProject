package com.fincore.users.application;

import com.fincore.shared.money.Money;
import com.fincore.users.domain.AppUser;
import com.fincore.users.infrastructure.persistence.AppUserJpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Currency;
import java.util.UUID;

@Service
public class UserService {

    private final AppUserJpaRepository userRepository;

    public UserService(AppUserJpaRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppUser getById(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    @Transactional(readOnly = true)
    public Currency baseCurrencyOf(UUID userId) {
        return Currency.getInstance(getById(userId).getBaseCurrency());
    }

    /** Campos a null = no se cambian. */
    @Transactional
    public AppUser updateProfile(UUID userId, String displayName, String baseCurrency) {
        AppUser user = getById(userId);
        String currency = baseCurrency != null ? Money.currencyFor(baseCurrency).getCurrencyCode() : user.getBaseCurrency();
        user.updateProfile(displayName != null ? displayName : user.getDisplayName(), currency);
        return user;
    }
}
