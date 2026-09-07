package com.senturkhasan.springairag.service;

import com.senturkhasan.springairag.dto.DocumentUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final VectorStore vectorStore;

    public DocumentUploadResponse ingestPdf(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Yüklenen dosya boş olamaz.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("Lütfen sadece geçerli bir PDF dosyası yükleyin.");
        }
        try {
            // 1. PDF'i Spring AI Resource olarak oku
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(file.getResource());
            List<Document> rawDocuments = pdfReader.get();

            // 2. Metin Ön İşleme (Preprocessing): Ardışık boşlukları ve satır sonlarını temizleme
            List<Document> cleanedDocuments = rawDocuments.stream()
                    .map(doc -> {
                        String cleanText = doc.getContent()
                                .replaceAll("\\s+", " ") // Birden fazla yan yana boşluğu/satır başını tek boşluk yap
                                .trim();
                        return new Document(cleanText, doc.getMetadata());
                    })
                    .toList();

            // 3. Metinleri anlamlı parçalara bölme (Chunking)
            TokenTextSplitter splitter = new TokenTextSplitter(800, 350, 5, 10000, true);
            List<Document> splitDocuments = splitter.apply(cleanedDocuments);

            // 4. Vektörleştirme ve pgvector veritabanına kaydetme
            vectorStore.accept(splitDocuments);

            log.info("PDF başarıyla işlendi ve vektörleştirildi: {}, Chunk Sayısı: {}",
                    file.getOriginalFilename(), splitDocuments.size());

            return new DocumentUploadResponse(
                    file.getOriginalFilename(),
                    splitDocuments.size(),
                    "SUCCESS"
            );
        } catch (Exception e) {
            log.error("Doküman işlenirken hata oluştu: {}", e.getMessage(), e);
            throw new RuntimeException("Doküman işlenirken hata oluştu: " + e.getMessage(), e);
        }
    }
}