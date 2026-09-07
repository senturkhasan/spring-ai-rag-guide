package com.senturkhasan.springairag.dto;

public record QueryRequest(
        String question,
        String conversationId

) {}