package com.senturkhasan.springairag.service;

import com.senturkhasan.springairag.dto.QueryRequest;
import com.senturkhasan.springairag.dto.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import java.util.List;

@Slf4j
@Service
public class RagService {
        /*
        * paketi, Spring AI ekosisteminde Vektör Veritabanları (Vector Databases) ile iletişim kurmak, veri saklamak ve anlamsal (semantic) aramalar yapmak için kullanılan temel soyutlama katmanıdır.

Özellikle RAG (Retrieval-Augmented Generation) mimarilerinde, büyük dil modellerine (LLM) kendi özel verilerinizi bağlarken kritik bir rol oynar.


      vectorStore  Spring AI kütüphanesinin sunduğu bir Java Arayüzüdür (Interface / Abstraction Layer).
        * */
    private final VectorStore vectorStore;
    private final ChatClient ragChatClient;    // Chat Memory destekli ana client
    private final ChatClient simpleChatClient; // Soru düzeltme için yalın client

    public RagService(VectorStore vectorStore, ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.vectorStore = vectorStore;

        // 1. Soru düzeltme gibi işlemlerde geçmişi kirletmemek için yalın ChatClient
        this.simpleChatClient = chatClientBuilder.build();

        // 2. Ana RAG akışında kullanılacak ChatMemory Advisor bağlı ChatClient
        this.ragChatClient = chatClientBuilder
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
        /*
        * defaultAdvisors(...) metodu sayesinde bu client'a bir "Hafıza Danışmanı" (ChatMemoryAdvisor) ekliyorsun. Artık bu client üzerinden yapılan her .call() veya .stream() isteğinde Spring AI otomatik olarak:

Verilen conversationId'ye ait geçmiş mesajları veritabanından/bellekten çekip prompt'a ekler.

Üretilen yeni cevabı otomatik olarak aynı conversationId ile hafızaya kaydeder.
        * */
    }

    public QueryResponse askQuestion(QueryRequest request) {
        // 1. Yazım Hatalarını Tolere Etme (Yalın client kullanılır)
        String fixedQuestion = rewordAndFixQuery(request.question());
        log.info("Ham Soru: '{}' -> Düzeltilmiş Soru: '{}'", request.question(), fixedQuestion);

        // 2. Vektör Araması
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(fixedQuestion)
                        .withTopK(3)//Aramanız sonucunda veritabanından en fazla kaç adet ilgili belgenin (Document) getirileceğini belirler
                        .withSimilarityThreshold(0.50)
                /*Vektör veritabanları sorunuz ile belgeler arasındaki anlamsal yakınlığı genellikle 0.0 ile 1.0 arasında bir skorla olçer
                 (1.0 = birebir aynı anlam, 0.0 = tamamen alakasız).
                    0.50 yazdığınızda, benzerlik skoru %50'nin altında kalan alakasız sonuçlar elenir.*/
        );

        /*
        * Arama Sorusu: "Sistemde izin başvurusu nasıl yapılır?"

            Veritabanındaki Sonuçlar ve Benzerlik Skorları:
            1. Belge A: %85 benzerlik (İzin süreçleri)    -> Barajı geçti (>=0.50)
            2. Belge B: %70 benzerlik (İK prosedürleri)  -> Barajı geçti (>=0.50)
            3. Belge C: %60 benzerlik (Mesai saatleri)    -> Barajı geçti (>=0.50)
            4. Belge D: %52 benzerlik (Maaş bordrosu)    -> Barajı geçti ama TopK limitine (3) takıldı, elendi.
            5. Belge E: %30 benzerlik (Yemek kartı)      -> Threshold barajına (0.50) takıldı, elendi.

            Sonuç: Sadece Belge A, B ve C döner.
        * */
        /* RAG (Retrieval-Augmented Generation) mimarisinde LLM'e verilecek context (bağlam) metnini hazırlar.*/
        List<String> contextList = similarDocuments.stream()
                .map(Document::getContent)
                .toList();
        /*
        * Vektör veritabanından similaritySearch ile dönen Document nesnelerinin listesidir.

            .map(Document::getContent): Her bir Document nesnesinin içindeki ham metin bilgisini (content) çekip alır (metadata veya id gibi diğer alanları dışarıda bırakır).

            .toList(): Elde edilen metinleri bir List<String> içerisine toplar.
        * */
        String context = String.join("\n\n---\n\n", contextList);
        /*
        * Listede bulunan tüm metin parçalarını aralarına belirlediğiniz ayracı koyarak tek bir dev String haline getirir.

"\n\n---\n\n": Her belge metninin arasına iki satır başı ve bir çizgi ekler. Bu sayede farklı kaynaklardan gelen metinler LLM için belirgin bir şekilde birbirinden ayrılmış olur.
        * */

        // 3. System Prompt
        String systemPrompt = """
            Sen kurumsal bir asistansın. Sana sağlanan bağlam (context) bilgilerini kullanarak kullanıcının sorusunu yanıtla.
            
            Kurallar:
            1. Yanıtlarında Türkçe dil bilgisi ve imla kurallarına kesinlikle uy (örneğin özel isim eklerini doğru ayır: "Hasan Şentürk'ün").
            2. Eğer verilen bağlam içerisinde sorunun cevabı yoksa, kendi bilgini uydurma ve 'Aranan bilgi yüklenen dokümanlarda bulunamadı.' de.
            
            Bağlam Bilgisi:
            {context}
            """;

        // 4. Conversation ID Yönetimi
        String conversationId = (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId()
                : "default-session";

        // 5. RAG ChatClient ile yanıt alma ve hafızaya kaydetme
        String aiAnswer = ragChatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .system(sp -> sp.text(systemPrompt).param("context", context))
                .user(fixedQuestion)
                .call()
                .content();

        return new QueryResponse(aiAnswer, contextList);
    }

    /**
     * Soru düzeltme işlemini ChatMemory'den bağımsız yalın client ile yapar.
     */
    private String rewordAndFixQuery(String rawQuery) {
        try {
            String fixPrompt = """
            Kullanıcının sorduğu sorudaki imla hatalarını düzelt. 
            Sadece düzeltilmiş cümleyi yaz, başka hiçbir açıklama yapma.
            
            Soru: %s
            """.formatted(rawQuery);

            String fixed = simpleChatClient.prompt()
                    .user(fixPrompt)
                    .call()
                    .content()
                    .trim();

            // Eğer model boş yanıt dönerse veya saçmalarsa ham soruyu kullan
            return (fixed.isBlank() || fixed.length() > rawQuery.length() * 2) ? rawQuery : fixed;
        } catch (Exception e) {
            log.warn("Soru düzeltme adımında hata oluştu, ham soru kullanılıyor: {}", e.getMessage());
            return rawQuery;
        }
    }

    public Flux<String> askQuestionStream(QueryRequest request) {
        String fixedQuestion = rewordAndFixQuery(request.question());

        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(fixedQuestion)
                        .withTopK(3)
                        .withSimilarityThreshold(0.55)
        );

        List<String> contextList = similarDocuments.stream()
                .map(Document::getContent)
                .toList();

        String context = String.join("\n\n---\n\n", contextList);

        String systemPrompt = """
        Sen kurumsal bir asistansın. Sana sağlanan bağlam (context) bilgilerini kullanarak kullanıcının sorusunu yanıtla.
        
        Kurallar:
        1. Yanıtlarında Türkçe dil bilgisi ve imla kurallarına kesinlikle uy.
        2. Eğer verilen bağlam içerisinde sorunun cevabı yoksa, kendi bilgini uydurma ve 'Aranan bilgi yüklenen dokümanlarda bulunamadı.' de.
        
        Bağlam Bilgisi:
        {context}
        """;

        String conversationId = (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId()
                : "default-session";

        return ragChatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(MessageChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, conversationId))
                .system(sp -> sp.text(systemPrompt).param("context", context))
                .user(fixedQuestion)
                .stream()
                .content();
    }
}