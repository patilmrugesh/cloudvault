package com.cloudvault.repository;

import com.cloudvault.model.FileMetadata;
import com.cloudvault.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FileRepository extends JpaRepository<FileMetadata, Long> {
    List<FileMetadata> findByOwner(User owner);
}