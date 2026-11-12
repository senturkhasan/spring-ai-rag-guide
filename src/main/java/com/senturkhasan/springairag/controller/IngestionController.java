package com.senturkhasan.springairag.controller;

import com.senturkhasan.springairag.dto.DocumentUploadResponse;
import com.senturkhasan.springairag.service.IngestionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestionController {
    private final IngestionService ingestionService;

    // Açık Constructor Enjeksiyonu
    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadPdf(@RequestParam("file") MultipartFile file) {
        DocumentUploadResponse response = ingestionService.ingestPdf(file);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/test")
    public ResponseEntity<String> test() {
        return ResponseEntity.ok("Test endpoint is working!");
    }
}