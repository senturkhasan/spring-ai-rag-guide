package com.senturkhasan.springairag.dto;

import java.util.List;

public record QueryResponse(
        String answer,
        List<String> sourceContexts
) {}