package com.example.nexusCommerce.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.example.nexusCommerce.exceptions.InvalidRequestException;

public final class Pagination {

    public static final int MAX_SIZE = 100;
    // Bounds how many distinct page keys one list cache can ever hold; without it a
    // caller can mint an unlimited number of cache entries by paging into empty space.
    public static final int MAX_PAGE_DEPTH = 1000;

    private Pagination() {
    }

    public static int clampSize(int size) {
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    public static void validatePage(int page) {
        if (page < 0) {
            throw new InvalidRequestException("page must not be negative");
        }
        if (page >= MAX_PAGE_DEPTH) {
            throw new InvalidRequestException("page must be less than " + MAX_PAGE_DEPTH);
        }
    }

    /**
     * Clamps the caller-supplied page size so a request cannot ask for the whole table,
     * and pins the sort to the primary key for repeatable pages.
     */
    public static PageRequest of(int page, int size) {
        validatePage(page);
        return PageRequest.of(page, clampSize(size), Sort.by(Sort.Direction.ASC, "id"));
    }
}
