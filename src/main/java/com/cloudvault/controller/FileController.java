package com.cloudvault.controller;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * Endpoint to upload a file.
     * Requires a valid JWT token in the Authorization header.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, Principal principal) {
        try {
            // principal.getName() retrieves the username from the JWT token
            String username = principal.getName();

            FileMetadata metadata = fileService.uploadFile(file, username);

            return ResponseEntity.ok("File uploaded and encrypted successfully! Metadata ID: " + metadata.getId());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<byte[]> downloadFile(@PathVariable Long id) {
        try {
            byte[] fileData = fileService.downloadFile(id);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"cloudvault_file\"")
                    .body(fileData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}