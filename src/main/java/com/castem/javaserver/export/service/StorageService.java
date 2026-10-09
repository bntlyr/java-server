package com.castem.javaserver.export.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final String bucketRoot;

    public StorageService(@Value("${storage.bucket.root:./uploads}") String bucketRoot) {
        this.bucketRoot = bucketRoot;
    }

    public record StorageResult(
        String absolutePath,
        String relativePath,
        long fileSize
    ) {}

    /**
     * Resolves the canonical root directory of the storage bucket.
     */
    public Path getBucketRootPath() {
        return Paths.get(bucketRoot).toAbsolutePath().normalize();
    }

    /**
     * Resolves and validates a safe path contained strictly within the bucket root.
     */
    public Path resolveSafePath(String subPath, String filename) {
        Path root = getBucketRootPath();
        String cleanFilename = (filename != null && !filename.isBlank())
            ? new File(filename).getName()
            : "";
        String cleanSubPath = (subPath != null) ? subPath.trim() : "";

        Path targetPath = root.resolve(cleanSubPath).resolve(cleanFilename).normalize();

        if (!targetPath.startsWith(root)) {
            throw new SecurityException("Security violation: Path traversal detected outside bucket root");
        }

        return targetPath;
    }

    /**
     * Saves a byte buffer to the storage bucket.
     */
    public StorageResult saveBuffer(String destinationSubPath, String filename, byte[] data) throws IOException {
        Path targetPath = resolveSafePath(destinationSubPath, filename);
        Path parentDir = targetPath.getParent();

        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        try (FileOutputStream fos = new FileOutputStream(targetPath.toFile())) {
            fos.write(data);
            fos.flush();
        }

        Path relative = getBucketRootPath().relativize(targetPath);
        String relativePathStr = relative.toString().replace('\\', '/');

        log.info("Saved file to bucket: [{}], size: {} bytes", relativePathStr, data.length);

        return new StorageResult(
            targetPath.toString(),
            relativePathStr,
            data.length
        );
    }

    /**
     * Checks if a file exists in the bucket.
     */
    public boolean fileExists(String relativePath) {
        try {
            Path path = resolveSafePath(relativePath, "");
            return Files.exists(path) && Files.isRegularFile(path);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Resolves a file from bucket for reading.
     */
    public byte[] readFile(String relativePath) throws IOException {
        Path path = resolveSafePath(relativePath, "");
        if (!Files.exists(path)) {
            throw new IOException("File not found in bucket: " + relativePath);
        }
        return Files.readAllBytes(path);
    }
}
