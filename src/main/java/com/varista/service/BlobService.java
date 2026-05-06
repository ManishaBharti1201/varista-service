package com.varista.service;

import com.varista.entity.Blob;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface BlobService {
    List<Blob> getAllBlobs();
    Optional<Resource> downloadBlob(Long id) throws IOException;
    void deleteBlob(Long id) throws IOException;
    Optional<Blob> getBlobById(Long id); // Added for potential use in controller for metadata
}
