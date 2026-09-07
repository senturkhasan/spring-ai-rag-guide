package com.senturkhasan.springairag.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;

    public void ingestDocument(MultipartFile file) throws IOException {
        log.info("Doküman işleniyor: {}", file.getOriginalFilename());

        // 1. TikaReader ile PDF, DOCX, TXT vb. tüm formatları oku
        var resource = new InputStreamResource(file.getInputStream());
        var reader = new TikaDocumentReader(resource);
        List<Document> rawDocuments = reader.get();

        // 2. Metni 800 token'lık parçalara böl (350 token çakışma/overlap ile)
        var textSplitter = new TokenTextSplitter(800, 350, 5, 10000, true);
        List<Document> splitDocuments = textSplitter.apply(rawDocuments);

        // 3. Vektör veritabanına kaydet (Embedding otomatik üretilir)
        vectorStore.accept(splitDocuments);
        log.info("{} adet parça (chunk) vektör veritabanına aktarıldı.", splitDocuments.size());
    }
}
