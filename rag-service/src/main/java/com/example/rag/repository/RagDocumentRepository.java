package com.example.rag.repository;

import com.example.rag.model.DocumentStatus;
import com.example.rag.model.RagDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RagDocumentRepository extends JpaRepository<RagDocument, String> {

    Optional<RagDocument> findBySha256(String sha256);

    Page<RagDocument> findByStatus(DocumentStatus status, Pageable pageable);

    Page<RagDocument> findByCategory(String category, Pageable pageable);

    Page<RagDocument> findByStatusAndCategory(DocumentStatus status, String category, Pageable pageable);

    /** Which of these documents may be used for answers right now. */
    @Query("SELECT d.id FROM RagDocument d WHERE d.id IN :ids AND d.status = com.example.rag.model.DocumentStatus.READY")
    List<String> findReadyIds(@Param("ids") Collection<String> ids);

    /** Documents left PROCESSING by a crash can never finish (the upload bytes are gone). */
    @Modifying
    @Query("""
            UPDATE RagDocument d
               SET d.status = com.example.rag.model.DocumentStatus.FAILED,
                   d.errorMessage = :reason
             WHERE d.status = com.example.rag.model.DocumentStatus.PROCESSING
            """)
    int failAllProcessing(@Param("reason") String reason);
}
