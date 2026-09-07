package com.senturkhasan.springairag.controller;

import com.senturkhasan.springairag.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService ingestionService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            ingestionService.ingestDocument(file);
            return ResponseEntity.ok("Doküman başarıyla işlendi ve vektörleştirildi: " + file.getOriginalFilename());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Doküman yükleme hatası: " + e.getMessage());
        }
    }
}