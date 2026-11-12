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

    /**
     * [İstemci / Postman / UI]
     *        │
     *        ▼ (1) HTTP POST /api/v1/documents/upload (MultipartFile)
     * [DocumentController]
     *        │
     *        ▼ (2) ingestionService.ingestDocument(file) çağrılır
     * [DocumentIngestionService]
     *        │
     *        ├──► (3) Apache Tika: Metin ayrıştırma ve okuma
     *        ├──► (4) TokenTextSplitter: Parçalama (Chunking)
     *        ├──► (5) Embedding Model (Ollama): Metni 1024 boyutlu vektöre dönüştürme
     *        └──► (6) VectorStore (pgvector): PostgreSQL'e kayıt
     *        │
     *        ▼ (7) Başarılı / Başarısız Dönüş
     * [DocumentController]
     *        │
     *        ▼ (8) ResponseEntity (200 OK veya 500 Internal Server Error)
     * [İstemci / Postman / UI]
     * */
    private final DocumentIngestionService ingestionService;
    //adresine gelen multipart/form-data formatındaki dosyayı kabul eder.
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