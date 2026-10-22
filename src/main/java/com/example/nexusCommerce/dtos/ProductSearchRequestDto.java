package com.example.nexusCommerce.dtos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Faceted catalogue filter bound from query params. Everything is normalised once here rather
 * than in the service, because the same normalised form is what goes into the cache key: if it
 * were not canonical, {@code ?sizes=S,M} and {@code ?sizes=m&sizes=S} would each mint their own
 * Redis entry for an identical result set.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchRequestDto {

    public static final Set<String> ALLOWED_SORT_FIELDS =
            Set.of("id", "price", "rating", "createdAt", "title");
    public static final int MAX_FACET_VALUES = 12;

    private String gender;
    private String categorySlug;
    private List<String> sizes;
    private List<String> colors;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String sort;
    // Field initializers are the only defaults this DTO has, and it is deliberately built by
    // the no-args constructor that query-param binding uses: a @Builder here would mean a
    // @Builder.Default value the HTTP path never applies, so size would silently be 0.
    private int page = 0;
    private int size = 20;

    public String normalizedGender() {
        return gender == null || gender.isBlank() ? null : gender.trim().toLowerCase(Locale.ROOT);
    }

    public String normalizedCategorySlug() {
        return categorySlug == null || categorySlug.isBlank() ? null : categorySlug.trim().toLowerCase(Locale.ROOT);
    }

    public List<String> normalizedSizes() {
        return normalize(sizes);
    }

    public List<String> normalizedColors() {
        return normalize(colors);
    }

    /** Trim, lowercase, de-duplicate and sort so the key is order-independent. */
    private static List<String> normalize(List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                values.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        List<String> sorted = new ArrayList<>(values);
        sorted.sort(String::compareTo);
        return List.copyOf(sorted);
    }

    /**
     * Canonical cache key. An unnormalised {@code null} for an absent price is intentional:
     * writing it as a fixed sentinel would make "no bound" and "bound of zero" collide.
     */
    public String cacheKey() {
        return String.join("|",
                str(normalizedGender()),
                str(normalizedCategorySlug()),
                String.join(",", normalizedSizes()),
                String.join(",", normalizedColors()),
                minPrice == null ? "" : minPrice.stripTrailingZeros().toPlainString(),
                maxPrice == null ? "" : maxPrice.stripTrailingZeros().toPlainString(),
                str(normalizedSort()),
                Integer.toString(page),
                Integer.toString(size));
    }

    public String normalizedSort() {
        if (sort == null || sort.isBlank()) {
            return "id:asc";
        }
        String[] parts = sort.trim().split(",");
        String field = parts[0].trim();
        String direction = parts.length > 1 ? parts[1].trim().toLowerCase(Locale.ROOT) : "asc";
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            direction = "asc";
        }
        return field + ":" + direction;
    }

    private static String str(String value) {
        return value == null ? "" : value;
    }
}
