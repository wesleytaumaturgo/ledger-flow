package com.wesleytaumaturgo.ledgerflow.api.dto;

import com.wesleytaumaturgo.ledgerflow.query.application.usecase.TransactionHistoryView;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * HTTP response DTO for GET /api/v1/accounts/{id}/transactions.
 *
 * Wraps Spring's Page&lt;TransactionHistoryView&gt; into an explicit JSON shape
 * with stable field names for the API contract.
 * Fields: content, page, size, totalElements, totalPages.
 */
public record TransactionsPageResponse(
        List<TransactionHistoryView> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Maps a Spring Page of TransactionHistoryView to this response record.
     */
    public static TransactionsPageResponse from(Page<TransactionHistoryView> page) {
        return new TransactionsPageResponse(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
