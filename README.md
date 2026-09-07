# 🚀 Spring Boot 3.3 & Spring AI ile Enterprise RAG Mimarisi

Üretim ortamına uygun (production-grade), yüksek doğruluk oranına sahip, yazım hatalarını tolere edebilen ve canlı yanıt akışı sunan gelişmiş Retrieval-Augmented Generation (RAG) mimarisi.

> [!NOTE]
> **Neden Bu Mimari?**
> Klasik RAG uygulamalarındaki kötü veri girdisi, halüsinasyon, bağlam kaybı ve yavaş yanıt problemlerini uçtan uca çözen kurumsal mekanizmalar barındırır.

---

## 💡 Öne Çıkan Özellikler

* 🛠️ **Otomatik Soru Düzeltme (Query Rewriting):** Kullanıcı sorularındaki imla ve anlatım bozuklukları LLM ile vektör araması öncesinde otomatik düzeltilir.
* 🧹 **Gelişmiş Metin Ön İşleme & Chunking:** PDF ve doküman ayrıştırma aşamasındaki gürültüler temizlenir, `TokenTextSplitter` ile ideal parçalara bölünerek vektör veritabanına aktarılır.
* 🎯 **Benzerlik Mesafe Eşiği (Cosine Distance / Threshold):** Yüksek kaliteli eşleşmeler çekilerek alakasız verilerin LLM'e gitmesi engellenir.
* 🧠 **Oturum Bazlı Hafıza (Chat Memory):** `MessageChatMemoryAdvisor` ve `conversationId` entegrasyonu ile çoklu kullanıcı sohbet geçmişi izole edilir.
* ⚡ **İzole LLM İstemcileri (Dual ChatClient):** Soru düzeltme ve ana yanıt üretimi için iki ayrı `ChatClient` örneği kullanılarak hafızanın kirlenmesi önlenir.
* 🌊 **Gerçek Zamanlı Streaming (SSE):** Spring WebFlux (`Flux<String>`) altyapısı ile ChatGPT benzeri canlı yanıt akışı ve Web UI desteği.
* 📊 **Metrik & İzlenebilirlik (Observability):** Spring Boot Actuator ve Micrometer ile LLM çağrıları ve sistem metrikleri izlenir.
* 🧪 **Testcontainers Otomasyonu:** `@SpringBootTest` ve Docker üzerinde geçici `pgvector` container'ı ile tamamen izole entegrasyon testleri.

---

## 🛠️ Teknoloji Yığını

| Bileşen | Kullanılan Teknoloji / Kütüphane |
| :--- | :--- |
| **Ana Dil & Framework** | Java 21 (Temurin LTS), Spring Boot 3.3.x |
| **Yapay Zeka Framework** | Spring AI (Ollama / OpenAI Desteği) |
| **Vektör Veritabanı** | PostgreSQL (`pgvector` eklentisi & HNSW İndeksi) |
| **Reaktif Akış & UI** | Spring WebFlux (`Flux<String>`), SSE, Static Web UI |
| **Metrik & Test** | Spring Boot Actuator, Testcontainers (`pgvector/pgvector:pg16`), Swagger UI |

---

## 🧪 Adım Adım Test ve Kullanım

Uygulamayı çalıştırdıktan sonra tarayıcınızdan `http://localhost:8080/swagger-ui.html` veya dahili arayüz için `http://localhost:8080/index.html` adresine gidin.

### 1️⃣ Doküman Yükleme (Ingestion Pipeline)
* **Endpoint:** `POST /api/v1/documents/upload`
* **Açıklama:** PDF/TXT/DOCX dosyanızı seçin ve gönderin. Metnin temizlenip parçalar halinde `pgvector` veritabanına aktarıldığını gözlemleyin.

### 2️⃣ Streaming Chat & RAG Sorgusu
* **Endpoint:** `POST /api/v1/rag/stream`
* **Request Body:**
```json
{
  "question": "XX kişisi tecrübesi ne kadar?",
  "conversationId": "oturum-101"
}
[!TIP]
Test Senaryosu:

İlk istekte kesme işareti ve ek hatasına rağmen sorunun otomatik düzeltilip akıcı bir şekilde yanıtlandığını doğrulayın.

Aynı conversationId (oturum-101) ile ikinci soru olarak "Peki hangi şirketlerde çalışmış?" gönderin ve sistemin önceki soruyu hatırladığını (Chat Memory) test edin.

🚀 Hızlı Başlangıç
Bash
# 1. PostgreSQL + pgvector servisini başlatın
docker-compose up -d

# 2. Entegrasyon testlerini çalıştırın (Testcontainers otomatik pgvector kaldıracaktır)
./mvnw test

# 3. Uygulamayı ayağa kaldırın
./mvnw spring-boot:run