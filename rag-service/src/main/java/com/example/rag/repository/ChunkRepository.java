package com.example.rag.repository;

import com.example.rag.retrieval.ChunkCandidate;
import com.example.rag.retrieval.SearchFilters;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * Direct SQL on vector_store for what Spring AI's VectorStore does not offer:
 *   - the KEYWORD leg of hybrid search (PostgreSQL full-text search on content_tsv)
 *   - deleting all chunks of a document
 * All user input is passed as bind parameters (?), never concatenated into SQL.
 */
@Repository
public class ChunkRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChunkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * websearch_to_tsquery understands normal search text ("return policy -refund",
     * "\"extended warranty\""); ts_rank_cd ranks by how often and how close the words appear.
     */
    public List<ChunkCandidate> keywordSearch(String query, SearchFilters filters, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT id::text                         AS chunk_id,
                       content,
                       metadata ->> 'documentId'        AS document_id,
                       metadata ->> 'filename'          AS filename,
                       metadata ->> 'category'          AS category,
                       metadata ->> 'fileType'          AS file_type,
                       metadata ->> 'pageNumber'        AS page_number,
                       metadata ->> 'chunkIndex'        AS chunk_index,
                       ts_rank_cd(content_tsv, q)       AS rank
                  FROM vector_store, websearch_to_tsquery('english', ?) AS q
                 WHERE content_tsv @@ q
                """);
        List<Object> params = new ArrayList<>();
        params.add(query);

        if (filters.hasDocumentIds()) {
            sql.append(" AND metadata ->> 'documentId' IN (")
                    .append(String.join(",", filters.documentIds().stream().map(id -> "?").toList()))
                    .append(")");
            params.addAll(filters.documentIds());
        }
        if (filters.category() != null) {
            sql.append(" AND metadata ->> 'category' = ?");
            params.add(filters.category());
        }
        if (filters.fileType() != null) {
            sql.append(" AND metadata ->> 'fileType' = ?");
            params.add(filters.fileType());
        }
        sql.append(" ORDER BY rank DESC LIMIT ?");
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            ChunkCandidate candidate = new ChunkCandidate(
                    rs.getString("chunk_id"),
                    rs.getString("content"),
                    rs.getString("document_id"),
                    rs.getString("filename"),
                    rs.getString("category"),
                    rs.getString("file_type"),
                    toInteger(rs.getString("page_number")),
                    toInteger(rs.getString("chunk_index")));
            candidate.setKeywordRank(rs.getDouble("rank"));
            return candidate;
        }, params.toArray());
    }

    public int deleteByDocumentId(String documentId) {
        return jdbcTemplate.update("DELETE FROM vector_store WHERE metadata ->> 'documentId' = ?", documentId);
    }

    private static Integer toInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return (int) Double.parseDouble(value);   // JSON numbers may come back as "3" or "3.0"
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
