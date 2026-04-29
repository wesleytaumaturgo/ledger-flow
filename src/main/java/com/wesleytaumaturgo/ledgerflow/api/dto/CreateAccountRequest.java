package com.wesleytaumaturgo.ledgerflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @NotBlank(message = "ownerId is required")
    @Size(max = 100, message = "ownerId must not exceed 100 characters")
    String ownerId
) {}
