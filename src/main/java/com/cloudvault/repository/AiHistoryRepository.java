package com.cloudvault.repository;

import com.cloudvault.entity.AiHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiHistoryRepository extends JpaRepository<AiHistory,Long> {
    List<AiHistory> findByFileIdAndUsernameOrderByCreatedAtDesc(
            Long fieldId, String username
    );
}
