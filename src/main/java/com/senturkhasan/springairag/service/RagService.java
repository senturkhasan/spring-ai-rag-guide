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
    }

    public QueryResponse askQuestion(QueryRequest request) {
        // 1. Yazım Hatalarını Tolere Etme (Yalın client kullanılır)
        String fixedQuestion = rewordAndFixQuery(request.question());
        log.info("Ham Soru: '{}' -> Düzeltilmiş Soru: '{}'", request.question(), fixedQuestion);

        // 2. Vektör Araması
        List<Document> similarDocuments = vectorStore.similaritySearch(
                SearchRequest.query(fixedQuestion)
                        .withTopK(3)
                        .withSimilarityThreshold(0.50)
        );

        List<String> contextList = similarDocuments.stream()
                .map(Document::getContent)
                .toList();

        String context = String.join("\n\n---\n\n", contextList);

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