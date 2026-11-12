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
               /*
               *
               * Token Nedir?Yapay zeka modelleri metinleri bizim gibi kelime kelime değil, token adı verilen parçalar halinde okur.
               * Türkçe için ortalama bir kural olarak: 1 kelime $\approx$ 1.5 - 2 token civarındadır.Yani 800 token'lık bir
               *  sınır koyduğunuzda, yapay zekaya "Bana tek seferde yaklaşık 400 - 500 kelimelik metin parçaları oluştur" demiş olursunuz.Neden Böyle
               * Bir Sınır (Maksimum Token) Koyuyoruz?Anlam Bütünlüğü (Bağlam): Devasa bir PDF kitabını yapay zekaya tek parça halinde verirseniz aradığı
               * bilgiyi bulmakta zorlanır. Çok küçük parçalara (örneğin sadece 10 kelime) bölerseniz de cümlenin başı ile sonu arasındaki anlam ilişkisi kaybolur.
               * 800 token, bir konunun anlatımını ve anlam bütünlüğünü bozmadan saklamak için ideal bir "paragraf / sayfa" boyutudur.Vektör Veritabanı ve
               * Arama Başarısı: RAG mimarisinde kullanıcı bir soru sorduğunda, sistem bu 800 token'lık parçalar arasında en alakalı olan 3-5 parçayı bulur.
               * Parça boyutu dengeli olduğunda, kullanıcının sorusuna nokta atışı yanıt veren doğru paragraf daha kolay tespit edilir.Maliyet ve Hız: Yapay zeka modellerine
               * gönderdiğiniz token sayısı arttıkça işlem süresi (gecikme) ve (API kullanılıyorsa) ücret artar. Maksimum sınırı belirleyerek modelin gereksiz devasa metinlerle
               *  yavaşlamasını engellersiniz.Özetle; defaultChunkSize: 800 ayarı, uzun dokümanlarınızı kesip biçerken "Her bir parçanın boyutu en fazla 800 token
               *  (yaklaşık 1-1.5 sayfa/paragraf)
               * olsun, bundan uzun olursa yeni bir parçaya geç" talimatıdır.
               * */
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