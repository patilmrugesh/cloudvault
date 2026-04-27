package com.cloudvault.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_metadata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fileName;

    private String contentType;

    private Long fileSize;

    // Track how many chunks the file was split into for HDFS storage
    private Integer totalChunks;

    // The AES key used to encrypt this specific file's chunks
    @Column(nullable = false)
    private String encryptionKey;

    private LocalDateTime uploadDate;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User owner;

    @PrePersist
    protected void onCreate() {
        uploadDate = LocalDateTime.now();
    }
}