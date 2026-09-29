package com.fincore.users.application;

import java.util.UUID;

/**
 * API pública del módulo users. Otros módulos (accounts, etc.) dependen de esta
 * interfaz en vez de acceder directamente al repositorio de usuarios.
 */
public interface UserQueryService {

    boolean existsById(UUID userId);
}
