package com.asyncpipeline.infra.storage;

import com.asyncpipeline.job.application.port.out.ArtifactStoragePort;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Higher-level service for storing and retrieving uploaded documents.
 * Delegates to {@link ArtifactStoragePort} for the actual I/O.
 */
@Service
public class DocumentStorageService {

    private final ArtifactStoragePort storagePort;

    public DocumentStorageService(ArtifactStoragePort storagePort) {
        this.storagePort = storagePort;
    }

    /**
     * Stores a document at {@code documents/{documentId}/{fileName}} and returns the storage path.
     *
     * @param documentId  unique identifier for the document
     * @param fileName    original file name
     * @param content     raw file bytes
     * @param contentType MIME type of the file
     * @return the storage path string
     */
    public String storeDocument(UUID documentId, String fileName, byte[] content, String contentType) {
        String storagePath = "documents/" + documentId + "/" + fileName;
        storagePort.save(storagePath, content, contentType);
        return storagePath;
    }

    /**
     * Reads a previously stored document by its storage path.
     *
     * @param storagePath the path returned by {@link #storeDocument}
     * @return raw file bytes
     */
    public byte[] readDocument(String storagePath) {
        return storagePort.read(storagePath);
    }
}
