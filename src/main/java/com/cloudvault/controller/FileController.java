package com.cloudvault.controller;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/files")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            Principal principal) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body("No file provided");
        }

        try {
            String username = principal.getName();
            FileMetadata metadata = fileService.uploadFile(file, username);
            return ResponseEntity.ok("File uploaded successfully! ID: " + metadata.getId());

        } catch (Exception e) {
            // FIX: log the real exception so you can see it in Spring Boot console
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<?> downloadFile(
            @PathVariable Long id,
            Principal principal) {

        try {
            String username = principal.getName();

            // FIX: snapshot the filename/contentType BEFORE the byte[] fetch,
            //      so if getFileMetadata() is called again after downloadFile()
            //      it doesn't do a second DB hit unnecessarily.
            FileMetadata metadata = fileService.getFileMetadata(id);

            // Security: check ownership before doing any work
            if (!metadata.getOwner().getUsername().equals(username)) {
                return ResponseEntity.status(403).body("Access denied");
            }

            byte[] fileData = fileService.downloadFile(id, username);

            String contentType = metadata.getContentType();
            MediaType mediaType;
            try {
                mediaType = (contentType != null && !contentType.isBlank())
                        ? MediaType.parseMediaType(contentType)
                        : MediaType.APPLICATION_OCTET_STREAM;
            } catch (Exception e) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + metadata.getFileName() + "\""
                    )
                    .contentLength(fileData.length)
                    .body(fileData);

        } catch (SecurityException e) {
            return ResponseEntity.status(403).body("Access denied");

        } catch (Exception e) {
            // FIX: log the REAL exception — this is what tells you what's actually wrong
            log.error("Download failed for fileId={}", id, e);
            // FIX: return the message in body so the frontend can show it
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
    }

    @GetMapping("/list")
    public ResponseEntity<List<FileMetadata>> listUserFiles(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(fileService.getUserFiles(username));
    }
}