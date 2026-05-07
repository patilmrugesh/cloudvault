package com.cloudvault.repository;

import com.cloudvault.model.FileChunkMap;
import com.cloudvault.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FileChunkMapRepository extends JpaRepository<FileChunkMap, Long> {

    List<FileChunkMap> findByFileOrderByChunkIndexAsc(FileMetadata file);

    void deleteByFile(FileMetadata file);
}