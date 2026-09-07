package com.senturkhasan.springairag.service;

import static org.junit.jupiter.api.Assertions.*;
import com.senturkhasan.springairag.dto.QueryRequest;
import com.senturkhasan.springairag.dto.QueryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class RagServiceTest {

    // pgvector destekli resmi PostgreSQL imajı
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres")
    );

    @Autowired
    private RagService ragService;

    @Test
    void shouldReturnAnswerForValidQuery() {
        // Given
        QueryRequest request = new QueryRequest("Oğuzlar kimdir?", "test-session");

        // When
        QueryResponse response = ragService.askQuestion(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.answer()).isNotBlank();

        // Verifikasyon: Konteynerin ayakta olduğunu doğrula
        assertThat(postgres.isRunning()).isTrue();
    }
}