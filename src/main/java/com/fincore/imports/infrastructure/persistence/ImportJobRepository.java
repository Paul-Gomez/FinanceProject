package com.fincore.imports.infrastructure.persistence;

import com.fincore.imports.domain.ImportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {

    List<ImportJob> findTop50ByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
