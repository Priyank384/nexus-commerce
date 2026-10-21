package com.example.nexusCommerce.utils;

import java.util.Set;

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

    /**
     * Same as {@link #of(int, int)} but honours a caller-supplied {@code field,direction}
     * spec. The field must be in {@code allowedFields} (an allowlist, not a denylist, so
     * a caller cannot probe column names). An id-ascending tiebreaker is always appended
     * to keep pages stable when the primary sort has duplicates.
     */
    public static PageRequest of(int page, int size, String sort, Set<String> allowedFields) {
        validatePage(page);
        return PageRequest.of(page, clampSize(size), parseSort(sort, allowedFields));
    }

    /**
     * Normalises the raw sort query param into the cache-key string so that "?sort=",
     * "?sort=id,asc" and omitted sort all collapse to the same default cache entry
     * instead of fragmenting it.
     */
    public static String canonicalSort(String sort, Set<String> allowedFields) {
        if (sort == null || sort.isBlank()) {
            return "id:asc";
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!allowedFields.contains(field)) {
            throw new InvalidRequestException("sort field not allowed: " + field);
        }
        Sort.Direction dir = parts.length > 1
                ? Sort.Direction.fromString(parts[1].trim())
                : Sort.Direction.ASC;
        return field + ":" + dir.name().toLowerCase();
    }

    private static Sort parseSort(String sort, Set<String> allowedFields) {
        String canonical = canonicalSort(sort, allowedFields);
        String[] kv = canonical.split(":");
        Sort.Direction dir = Sort.Direction.fromString(kv[1].toUpperCase());
        Sort primary = Sort.by(dir, kv[0]);
        if ("id".equals(kv[0])) {
            return primary;
        }
        return primary.and(Sort.by(Sort.Direction.ASC, "id"));
    }
}
