package com.asyncpipeline.infra.storage;

import com.asyncpipeline.job.application.port.out.ArtifactStoragePort;
import com.asyncpipeline.job.application.port.out.StoredObjectMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;

@Component
public class LocalFileStorageAdapter implements ArtifactStoragePort {

    private static final Logger log = LoggerFactory.getLogger(LocalFileStorageAdapter.class);

    private final Path baseDir;

    public LocalFileStorageAdapter(@Value("${app.storage.root-dir:.local-storage}") String rootDir) {
        this.baseDir = Path.of(rootDir).toAbsolutePath();
        log.info("Local file storage initialized at {}", this.baseDir);
    }

    @Override
    public void save(String objectPath, byte[] content, String contentType) {
        Path filePath = baseDir.resolve(objectPath);
        try {
            Files.createDirectories(filePath.getParent());
            Files.write(filePath, content);

            String checksum = computeSha256(content);

            Path metaPath = Path.of(filePath + ".meta");
            Properties meta = new Properties();
            meta.setProperty("contentType", contentType);
            meta.setProperty("sizeBytes", String.valueOf(content.length));
            meta.setProperty("checksum", checksum);

            try (var out = Files.newOutputStream(metaPath)) {
                meta.store(out, "Storage metadata");
            }

            log.debug("Saved {} ({} bytes, sha256={})", objectPath, content.length, checksum);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to save object: " + objectPath, ex);
        }
    }

    @Override
    public byte[] read(String objectPath) {
        Path filePath = baseDir.resolve(objectPath);
        try {
            return Files.readAllBytes(filePath);
        } catch (NoSuchFileException ex) {
            throw new UncheckedIOException("Object not found: " + objectPath, ex);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read object: " + objectPath, ex);
        }
    }

    @Override
    public StoredObjectMeta stat(String objectPath) {
        Path metaPath = Path.of(baseDir.resolve(objectPath) + ".meta");
        if (!Files.exists(metaPath)) {
            return new StoredObjectMeta(false, 0, null, null);
        }

        try {
            Properties meta = new Properties();
            try (var in = Files.newInputStream(metaPath)) {
                meta.load(in);
            }
            return new StoredObjectMeta(
                    true,
                    Long.parseLong(meta.getProperty("sizeBytes", "0")),
                    meta.getProperty("contentType"),
                    meta.getProperty("checksum")
            );
        } catch (IOException ex) {
            log.warn("Failed to read metadata for {}: {}", objectPath, ex.getMessage());
            return new StoredObjectMeta(false, 0, null, null);
        }
    }

    private String computeSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("SHA-256 algorithm not available", ex);
        }
    }
}
