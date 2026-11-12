package com.senturkhasan.springairag.controller;

import com.senturkhasan.springairag.dto.QueryRequest;
import com.senturkhasan.springairag.dto.QueryResponse;
import com.senturkhasan.springairag.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/v1/rag") 
public class RagController {

    private final RagService ragService;

    // Explicit Constructor Injection
    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ResponseEntity<QueryResponse> askQuestion(@RequestBody QueryRequest request) {
        QueryResponse response = ragService.askQuestion(request);
        return ResponseEntity.ok(response);
    }

        /**endpoint'i üzerinden Spring WebFlux (Flux<String>) kullanarak LLM'in ürettiği yanıtı token token canlı olarak tarayıcıya (SSE) fırlatır.*/
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> askQuestionStream(@RequestBody QueryRequest request) {
        return ragService.askQuestionStream(request);
    }
}