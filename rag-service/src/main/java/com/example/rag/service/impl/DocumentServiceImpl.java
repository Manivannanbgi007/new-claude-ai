package com.example.rag.service.impl;

import com.example.rag.config.RagProperties;
import com.example.rag.dto.DocumentResponse;
import com.example.rag.dto.PageResponse;
import com.example.rag.exception.DocumentNotFoundException;
import com.example.rag.exception.DuplicateDocumentException;
import com.example.rag.exception.InvalidDocumentException;
import com.example.rag.ingestion.DocumentIngestionService;
import com.example.rag.mapper.RagMapper;
import com.example.rag.model.DocumentStatus;
import com.example.rag.model.RagDocument;
import com.example.rag.repository.ChunkRepository;
import com.example.rag.repository.RagDocumentRepository;
import com.example.rag.service.DocumentService;
import com.example.rag.util.AppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);

    private final RagDocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final DocumentIngestionService ingestionService;
    private final RagMapper mapper;
    private final RagProperties.Ingestion settings;

    public DocumentServiceImpl(RagDocumentRepository documentRepository, ChunkRepository chunkRepository,
                               DocumentIngestionService ingestionService, RagMapper mapper, RagProperties properties) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.ingestionService = ingestionService;
        this.mapper = mapper;
        this.settings = properties.ingestion();
    }

    /**
     * NOT @Transactional on purpose: the document row must be COMMITTED before the
     * background ingestion starts, otherwise the ingestion thread could not see it yet.
     * saveAndFlush() runs in its own short transaction.
     */
    @Override
    public DocumentResponse upload(MultipartFile file, String category, String title) {
        if (file == null || file.isEmpty()) {
            throw new InvalidDocumentException("The file is empty.");
        }
        if (file.getSize() > settings.maxFileSize().toBytes()) {
            throw new InvalidDocumentException("The file is larger than " + settings.maxFileSize().toMegabytes() + " MB.");
        }
        String filename = cleanFilename(file.getOriginalFilename());
        String fileType = extension(filename);
        if (!settings.allowedExtensions().contains(fileType)) {
            throw new InvalidDocumentException("Unsupported file type '." + fileType + "'. Allowed: " + settings.allowedExtensions());
        }
        String normalizedCategory = StringUtils.hasText(category) ? category.trim().toLowerCase(Locale.ROOT) : AppConstants.DEFAULT_CATEGORY;
        if (!normalizedCategory.matches(AppConstants.CATEGORY_REGEX)) {
            throw new InvalidDocumentException("category may contain only lower-case letters, digits and '-' (max 50).");
        }
        String documentTitle = StringUtils.hasText(title) ? truncate(title.trim(), 255) : titleFrom(filename);

        byte[] content = readBytes(file);
        String sha256 = sha256(content);
        documentRepository.findBySha256(sha256).ifPresent(existing -> {
            throw new DuplicateDocumentException(existing.getId(), existing.getFilename());
        });

        RagDocument document = documentRepository.saveAndFlush(new RagDocument(UUID.randomUUID().toString(), filename,
                documentTitle, normalizedCategory, fileType, file.getContentType(), content.length, sha256));
        log.info("Document {} ({}, {} bytes, category {}) accepted for ingestion",
                document.getId(), filename, content.length, normalizedCategory);

        try {
            ingestionService.ingest(document.getId(), content);
        } catch (TaskRejectedException ex) {
            document.markFailed("Rejected: too many documents are being processed. Upload it again later.");
            documentRepository.save(document);
            throw ex;
        }
        return mapper.toResponse(document);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DocumentResponse> list(DocumentStatus status, String category, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        String normalizedCategory = StringUtils.hasText(category) ? category.trim().toLowerCase(Locale.ROOT) : null;
        Page<RagDocument> result;
        if (status != null && normalizedCategory != null) {
            result = documentRepository.findByStatusAndCategory(status, normalizedCategory, pageable);
        } else if (status != null) {
            result = documentRepository.findByStatus(status, pageable);
        } else if (normalizedCategory != null) {
            result = documentRepository.findByCategory(normalizedCategory, pageable);
        } else {
            result = documentRepository.findAll(pageable);
        }
        return PageResponse.from(result, mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentResponse get(String id) {
        return mapper.toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public void delete(String id) {
        RagDocument document = findOrThrow(id);
        int chunks = chunkRepository.deleteByDocumentId(id);
        documentRepository.delete(document);
        log.info("Deleted document {} ({}) and {} chunks", id, document.getFilename(), chunks);
    }

    private RagDocument findOrThrow(String id) {
        return documentRepository.findById(id.toLowerCase(Locale.ROOT)).orElseThrow(() -> new DocumentNotFoundException(id));
    }

    /** Keeps only the file name ("C:\\docs\\a.pdf" -> "a.pdf") - never trust a client-supplied path. */
    private static String cleanFilename(String original) {
        if (!StringUtils.hasText(original)) {
            throw new InvalidDocumentException("The file has no name.");
        }
        String name = original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty() || !name.contains(".")) {
            throw new InvalidDocumentException("The file name must have an extension, e.g. handbook.pdf");
        }
        return truncate(name, 255);
    }

    private static String extension(String filename) {
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }

    private static String titleFrom(String filename) {
        String base = filename.substring(0, filename.lastIndexOf('.'));
        return truncate(base.replace('_', ' ').replace('-', ' ').trim(), 255);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new InvalidDocumentException("The file could not be read: " + ex.getMessage());
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
