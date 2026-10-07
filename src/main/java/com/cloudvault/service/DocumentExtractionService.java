package com.cloudvault.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;


@Service
public class DocumentExtractionService {
    public String extractText(byte[] fileBytes,String contentType) throws IOException {
        if("application/pdf".equalsIgnoreCase(contentType)){
            return extractPdfText(fileBytes);
        }
        if("text/plain".equalsIgnoreCase(contentType)){
            return new String(fileBytes);
        }
        throw new IllegalArgumentException(
                "Unsupported document type: " + contentType
        );
    }
    private String extractPdfText(byte[] fileBytes) throws IOException {

        try (PDDocument document = Loader.loadPDF(fileBytes)) {

            PDFTextStripper stripper = new PDFTextStripper();

            return stripper.getText(document);
        }
    }
}
