package com.example.inventory.shared;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;

public final class PageSupport {

    private static final int MAX_PAGE_SIZE = 100;

    private PageSupport() {
    }

    public static PageRequest request(int page, int size, Sort sort) {
        if (page < 0) {
            throw new BadRequestException("Page index must not be negative");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }
        return PageRequest.of(page, size, sort);
    }

    public static void validateDateRange(Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("The from date must not be after the to date");
        }
    }
}
