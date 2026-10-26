package com.example.nexusCommerce.utils;

import java.util.Locale;
import java.util.function.Predicate;

import com.example.nexusCommerce.exceptions.InvalidRequestException;

/**
 * URL slugs for categories and products. The SQL backfills in V15/V18 use the same rules
 * (lowercase, non-alphanumerics collapsed to a single hyphen) so seeded rows and rows created
 * through the API are indistinguishable.
 */
public final class Slugs {

    private Slugs() {
    }

    public static String slugify(String raw) {
        if (raw == null) {
            return null;
        }
        String slug = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? null : slug;
    }

    /**
     * Appends -2, -3... until {@code taken} says the candidate is free. The caller owns what
     * "free" means, because slugs are only unique among live rows and an update must not
     * collide with the very row it is editing.
     */
    public static String unique(String base, Predicate<String> taken) {
        if (base == null) {
            return null;
        }
        if (!taken.test(base)) {
            return base;
        }
        for (int i = 2; i < 1000; i++) {
            String candidate = base + "-" + i;
            if (!taken.test(candidate)) {
                return candidate;
            }
        }
        throw new InvalidRequestException("Could not derive a unique slug from '" + base + "'");
    }
}
