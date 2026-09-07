package com.senturkhasan.springairag.dto;

public record DocumentUploadResponse(
        String fileName,
        int chunkCount,
        String status
) {}
