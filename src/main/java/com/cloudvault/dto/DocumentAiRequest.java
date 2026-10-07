package com.cloudvault.dto;

import lombok.Data;

@Data
public class DocumentAiRequest {

    private String action;

    private String question;
}