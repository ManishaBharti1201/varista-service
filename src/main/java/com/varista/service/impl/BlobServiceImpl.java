package com.varista.service.impl;

import com.varista.entity.Blob;
import com.varista.repository.BlobRepository;
import com.varista.service.BlobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class BlobServiceImpl implements BlobService {

    @Autowired
    private BlobRepository blobRepository;

    @Override
    public List<Blob> getAllBlobs() {
        log.info("Fetching all blob records.");
        return blobRepository.findAll();
    }

    @Override
    public Optional<Resource> downloadBlob(Long id) throws IOException {
        log.info("Attempting to download blob with ID: {}", id);
        Optional<Blob> blobOptional = blobRepository.findById(id);
        if (blobOptional.isPresent()) {
            Blob blob = blobOptional.get();
            Path filePath = Paths.get(blob.getFilePath());
            if (Files.exists(filePath)) {
                try {
                    Resource resource = new UrlResource(filePath.toUri());
                    if (resource.exists() || resource.isReadable()) {
                        log.info("Successfully found and can read file for blob ID: {}", id);
                        return Optional.of(resource);
                    } else {
                        log.warn("File for blob ID {} exists but is not readable: {}", id, filePath.toAbsolutePath());
                        throw new IOException("Could not read the file: " + filePath);
                    }
                } catch (MalformedURLException e) {
                    log.error("Malformed URL for file path {}: {}", filePath, e.getMessage());
                    throw new IOException("File path could not be converted to URL", e);
                }
            } else {
                log.warn("File not found on disk for blob ID {}: {}", id, filePath.toAbsolutePath());
                // Optionally delete the blob record if the file is missing
                // blobRepository.delete(blob);
                // log.info("Deleted blob record {} as file was missing.", id);
                return Optional.empty();
            }
        }
        log.warn("Blob record not found for ID: {}", id);
        return Optional.empty();
    }

    @Override
    public void deleteBlob(Long id) throws IOException {
        log.info("Attempting to delete blob with ID: {}", id);
        Optional<Blob> blobOptional = blobRepository.findById(id);
        if (blobOptional.isPresent()) {
            Blob blob = blobOptional.get();
            Path filePath = Paths.get(blob.getFilePath());

            // Delete file from file system
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Successfully deleted file from disk: {}", filePath.toAbsolutePath());
            } else {
                log.warn("File not found on disk for blob ID {}. Skipping file deletion: {}", id, filePath.toAbsolutePath());
            }

            // Delete record from database
            blobRepository.delete(blob);
            log.info("Successfully deleted blob record from database for ID: {}", id);
        } else {
            log.warn("Blob record not found for ID: {}. No deletion performed.", id);
        }
    }

    @Override
    public Optional<Blob> getBlobById(Long id) {
        log.debug("Fetching blob record by ID: {}", id);
        return blobRepository.findById(id);
    }
}
