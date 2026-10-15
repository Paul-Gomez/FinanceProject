package com.fincore.imports.application;

import java.util.UUID;

public record ImportCompletedEvent(UUID ownerId, UUID importJobId, UUID accountId, int imported, int duplicates,
                                   int errors) {
}
