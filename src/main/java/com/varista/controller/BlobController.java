package com.varista.controller;

import com.varista.entity.Blob;
import com.varista.service.BlobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/blob")
@Slf4j
public class BlobController {

    @Autowired
    private BlobService blobService;

    @GetMapping
    public ResponseEntity<List<Blob>> getAllBlobs() {
        log.info("Received request to get all blobs.");
        List<Blob> blobs = blobService.getAllBlobs();
        return ResponseEntity.ok(blobs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Blob> getBlobById(@PathVariable Long id) {
        log.info("Received request to get blob by ID: {}", id);
        Optional<Blob> blob = blobService.getBlobById(id);
        return blob.map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Blob not found with ID: " + id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadBlob(@PathVariable Long id) {
        log.info("Received request to download blob with ID: {}", id);
        try {
            Optional<Resource> resourceOptional = blobService.downloadBlob(id);
            if (resourceOptional.isPresent()) {
                Resource resource = resourceOptional.get();
                Optional<Blob> blobOptional = blobService.getBlobById(id); // Get blob metadata for content type and filename
                
                String contentType = "application/octet-stream"; // Default content type
                String headerValue = "attachment; filename=\"" + resource.getFilename() + "\"";

                if (blobOptional.isPresent()) {
                    Blob blob = blobOptional.get();
                    contentType = blob.getContentType();
                    headerValue = "attachment; filename=\"" + blob.getFileName() + "\"";
                } else {
                    log.warn("Blob metadata not found for ID: {}. Using default content type and filename from resource.", id);
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, headerValue)
                        .body(resource);
            } else {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found for blob ID: " + id);
            }
        } catch (IOException e) {
            log.error("Error downloading blob with ID {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error downloading file", e);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBlob(@PathVariable Long id) {
        log.info("Received request to delete blob with ID: {}", id);
        try {
            Optional<Blob> blobOptional = blobService.getBlobById(id);
            if (blobOptional.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Blob not found with ID: " + id);
            }
            blobService.deleteBlob(id);
            return ResponseEntity.noContent().build();
        } catch (IOException e) {
            log.error("Error deleting blob with ID {}: {}", id, e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error deleting file", e);
        }
    }
}
