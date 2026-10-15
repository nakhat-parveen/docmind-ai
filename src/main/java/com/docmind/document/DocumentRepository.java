package com.docmind.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<StoredDocument, UUID> {

    Optional<StoredDocument> findByChecksum(String checksum);
}
