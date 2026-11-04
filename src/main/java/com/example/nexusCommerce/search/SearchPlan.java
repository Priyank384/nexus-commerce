package com.example.nexusCommerce.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.example.nexusCommerce.dtos.InferredFacetDto;
import com.example.nexusCommerce.dtos.ProductSearchRequestDto;

/**
 * The query the catalogue search actually runs, derived from what the shopper typed and the
 * facets the catalogue publishes.
 *
 * <p>A shopper who types "black tee" is filtering, not quoting: no product carries the word
 * black in its own text, because colour lives on the variant. Treating that word as text
 * answers a reasonable query with an empty page. So a word that names a colour or a size the
 * store sells becomes a facet filter and leaves the free text, and the words it becomes are
 * reported back so the storefront can show them as chips the shopper can take off again.
 *
 * <p>The vocabulary comes from {@code findUniqueVariantColors} / {@code findUniqueVariantSizes}
 * rather than a hard-coded list, for the reason those queries already give: a filter the
 * storefront can advertise is a filter some live product can be bought in. It also means the
 * inference learns new colours the day the catalogue adds them.
 */
public record SearchPlan(List<String> words,
                         List<String> colors,
                         List<String> sizes,
                         List<InferredFacetDto> inferred) {

    /**
     * Below this a word is only ever matched exactly. "M" is two keystrokes from "S" and both
     * are real sizes, so fuzzy matching on short words would invent filters the shopper never
     * asked for; sizes ('M', 'XL') therefore only match by typing them correctly.
     */
    private static final int MIN_FUZZY_LENGTH = 4;

    /** How close a mistyping has to be before it is read as the colour it meant. */
    private static final double MIN_SIMILARITY = 0.8;

    public static final String COLOR = "color";
    public static final String SIZE = "size";

    /**
     * @param words the query's words as typed, already lowercased and capped by
     *              {@link ProductSearchRequestDto#keywordWords()}
     * @param explicitColors facet values already chosen in the filter panel (lowercase)
     * @param explicitSizes  facet values already chosen in the filter panel (lowercase)
     * @param colorVocabulary the colours live products are sold in, in their display casing
     * @param sizeVocabulary  the sizes live products are sold in, in their display casing
     */
    public static SearchPlan of(List<String> words,
                                List<String> explicitColors,
                                List<String> explicitSizes,
                                List<String> colorVocabulary,
                                List<String> sizeVocabulary) {
        Set<String> mergedColors = new LinkedHashSet<>(explicitColors);
        Set<String> mergedSizes = new LinkedHashSet<>(explicitSizes);
        List<String> keptWords = new ArrayList<>();
        List<InferredFacetDto> inferred = new ArrayList<>();

        for (String word : words) {
            String colour = match(word, colorVocabulary);
            if (colour != null && canAdd(mergedColors, colour)) {
                // A colour the shopper already picked in the panel still drops the word from the
                // text, but reports no chip: the panel already shows it and already clears it.
                if (mergedColors.add(colour)) {
                    inferred.add(new InferredFacetDto(COLOR, displayOf(colour, colorVocabulary), word));
                }
                continue;
            }
            String size = match(word, sizeVocabulary);
            if (size != null && canAdd(mergedSizes, size)) {
                if (mergedSizes.add(size)) {
                    inferred.add(new InferredFacetDto(SIZE, displayOf(size, sizeVocabulary), word));
                }
                continue;
            }
            keptWords.add(word);
        }

        return new SearchPlan(List.copyOf(keptWords), capped(mergedColors), capped(mergedSizes),
                List.copyOf(inferred));
    }

    /** The whole remaining phrase, which is what a tsquery is built from. */
    public String phrase() {
        return String.join(" ", words);
    }

    public boolean hasText() {
        return !words.isEmpty();
    }

    /**
     * The first remaining word, wrapped as a LIKE pattern. V30's tsvector can only read the
     * product's own columns, so the joined category name keeps a LIKE branch or "dresses"
     * would stop finding the dresses it did find before.
     */
    public String categoryWordPattern() {
        return "%" + words.get(0) + "%";
    }

    /**
     * Exactly {@link ProductSearchRequestDto#MAX_KEYWORD_TOKENS} LIKE patterns, padded with
     * nulls, because the JPQL branches switch themselves off on null and a shorter list would
     * not bind.
     */
    public List<String> likePatterns() {
        List<String> patterns = new ArrayList<>();
        for (String word : words) {
            if (patterns.size() < ProductSearchRequestDto.MAX_KEYWORD_TOKENS) {
                patterns.add("%" + word + "%");
            }
        }
        while (patterns.size() < ProductSearchRequestDto.MAX_KEYWORD_TOKENS) {
            patterns.add(null);
        }
        // List.copyOf rejects nulls, and the padding is what keeps the arity fixed.
        return Collections.unmodifiableList(patterns);
    }

    /**
     * Closest word in one facet's vocabulary, or null when nothing is close enough. Colour is
     * tried before size by the caller; a word that names both is not something this catalogue
     * sells.
     */
    private static String match(String word, List<String> vocabulary) {
        String best = null;
        double bestScore = 0;
        for (String candidate : vocabulary) {
            double score = score(word, candidate.toLowerCase(Locale.ROOT));
            if (score > bestScore) {
                bestScore = score;
                best = candidate.toLowerCase(Locale.ROOT);
            }
        }
        return bestScore >= MIN_SIMILARITY ? best : null;
    }

    /**
     * 1 for an exact hit, 1 when one word contains the other, otherwise the edit-distance
     * ratio. Containment is what reads "blackish" or "olives" as the colour, which plain
     * distance would score too low to trust.
     */
    private static double score(String word, String candidate) {
        if (word.equals(candidate)) {
            return 1;
        }
        if (word.length() < MIN_FUZZY_LENGTH || candidate.length() < MIN_FUZZY_LENGTH) {
            return 0;
        }
        if (word.contains(candidate) || candidate.contains(word)) {
            return 1;
        }
        return 1.0 - (double) editDistance(word, candidate) / Math.max(word.length(), candidate.length());
    }

    private static int editDistance(String from, String to) {
        int[] previous = new int[to.length() + 1];
        int[] current = new int[to.length() + 1];
        for (int column = 0; column <= to.length(); column++) {
            previous[column] = column;
        }
        for (int row = 1; row <= from.length(); row++) {
            current[0] = row;
            for (int column = 1; column <= to.length(); column++) {
                int cost = from.charAt(row - 1) == to.charAt(column - 1) ? 0 : 1;
                current[column] = Math.min(Math.min(current[column - 1] + 1, previous[column] + 1),
                        previous[column - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[to.length()];
    }

    private static String displayOf(String lowercased, List<String> vocabulary) {
        return vocabulary.stream()
                .filter(value -> value.toLowerCase(Locale.ROOT).equals(lowercased))
                .findFirst()
                .orElse(lowercased);
    }

    private static List<String> capped(Set<String> values) {
        return values.stream().limit(ProductSearchRequestDto.MAX_FACET_VALUES).toList();
    }

    /**
     * Whether one more value can join this facet. Already-chosen values always can, and a full
     * facet cannot: a word turned into a filter that the query then drops would vanish from the
     * text and never appear as a filter, which is the empty page this whole class exists to
     * prevent.
     */
    private static boolean canAdd(Set<String> chosen, String value) {
        return chosen.contains(value) || chosen.size() < ProductSearchRequestDto.MAX_FACET_VALUES;
    }
}
