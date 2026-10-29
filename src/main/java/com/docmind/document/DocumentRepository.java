package com.docmind.document;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<StoredDocument, UUID>, JpaSpecificationExecutor<StoredDocument> {

    Optional<StoredDocument> findByChecksum(String checksum);
}
