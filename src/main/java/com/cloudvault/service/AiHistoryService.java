package com.cloudvault.service;

import com.cloudvault.entity.AiHistory;
import com.cloudvault.repository.AiHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AiHistoryService {

    private final AiHistoryRepository repository;

    public AiHistoryService(
            AiHistoryRepository repository) {
        this.repository = repository;
    }

    public AiHistory save(
            Long fileId,
            String username,
            String action,
            String question,
            String response) {

        AiHistory history = AiHistory.builder()
                .fileId(fileId)
                .username(username)
                .action(action)
                .question(question)
                .response(response)
                .build();

        return repository.save(history);
    }

    public List<AiHistory> getHistory(
            Long fileId,
            String username) {

        return repository
                .findByFileIdAndUsernameOrderByCreatedAtDesc(
                        fileId,
                        username
                );
    }
}