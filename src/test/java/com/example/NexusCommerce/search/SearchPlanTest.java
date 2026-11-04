package com.example.nexusCommerce.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.example.nexusCommerce.dtos.InferredFacetDto;
import com.example.nexusCommerce.dtos.ProductSearchRequestDto;

/**
 * The words a shopper types and the words a catalogue publishes are the only inputs here, so
 * every case is the whole rule: which words become filters, which stay text, and what the
 * response can honestly claim about both.
 */
class SearchPlanTest {

    private static final List<String> COLORS = List.of("Black", "Bone", "Olive");
    private static final List<String> SIZES = List.of("XS", "S", "M", "L", "XL");

    private SearchPlan plan(String keyword) {
        ProductSearchRequestDto query = new ProductSearchRequestDto();
        query.setKeyword(keyword);
        return SearchPlan.of(query.keywordWords(), query.normalizedColors(), query.normalizedSizes(),
                COLORS, SIZES);
    }

    @Test
    void aColourWordBecomesAFacetAndLeavesTheText() {
        SearchPlan plan = plan("black tee");

        assertEquals(List.of("tee"), plan.words());
        assertEquals(List.of("black"), plan.colors());
        assertEquals(List.of(), plan.sizes());
        assertEquals("tee", plan.phrase());
    }

    @Test
    void theInferenceReportsTheWordAndTheCataloguesSpelling() {
        List<InferredFacetDto> inferred = plan("blck tee").inferred();

        assertEquals(1, inferred.size());
        assertEquals(SearchPlan.COLOR, inferred.get(0).getFacet());
        // The chip says Black, the catalogue's own spelling, even though the shopper typed four
        // letters that are not in that order.
        assertEquals("Black", inferred.get(0).getValue());
        assertEquals("blck", inferred.get(0).getWord());
    }

    @Test
    void aSizeWordBecomesAFacetToo() {
        SearchPlan plan = plan("olive hoodie xl");

        assertEquals(List.of("hoodie"), plan.words());
        assertEquals(List.of("olive"), plan.colors());
        assertEquals(List.of("xl"), plan.sizes());
    }

    @Test
    void aWordThatIsNeitherAColourNorASizeStaysText() {
        SearchPlan plan = plan("organic linen crew neck");

        assertEquals(List.of("organic", "linen", "crew", "neck"), plan.words());
        assertTrue(plan.inferred().isEmpty());
    }

    @Test
    void shortWordsAreOnlyEverMatchedExactly() {
        // "M" is two keystrokes from "S" and both are real sizes, so guessing here would invent
        // a filter the shopper never asked for and take a word out of their search.
        assertEquals(List.of("x"), plan("x").words());
        assertEquals(List.of("med"), plan("med").words());
        assertEquals(List.of("m"), plan("m").sizes());
    }

    @Test
    void aWordContainingAColourIsReadAsThatColour() {
        assertEquals(List.of("black"), plan("blackish tee").colors());
        assertEquals(List.of("olive"), plan("olives socks").colors());
    }

    @Test
    void anExplicitFilterAbsorbsTheWordWithoutADuplicateChip() {
        ProductSearchRequestDto query = new ProductSearchRequestDto();
        query.setKeyword("black tee");
        query.setColors(List.of("Black"));

        SearchPlan plan = SearchPlan.of(query.keywordWords(), query.normalizedColors(),
                query.normalizedSizes(), COLORS, SIZES);

        assertEquals(List.of("tee"), plan.words());
        assertEquals(List.of("black"), plan.colors());
        // The panel already shows Black and can already clear it; a chip on top would be one the
        // shopper could press off and still have the filter stay on.
        assertTrue(plan.inferred().isEmpty());
    }

    @Test
    void whenEveryWordIsAFacetThereIsNoTextLeft() {
        SearchPlan plan = plan("black");

        assertFalse(plan.hasText());
        assertEquals(List.of("black"), plan.colors());
        assertEquals("", plan.phrase());
    }

    @Test
    void likePatternsPadsToTheArityTheQueryBinds() {
        // Arrays.asList, not List.of: the padding is literally null, and the JPQL branches switch
        // themselves off on it.
        assertEquals(Arrays.asList("%tee%", "%cotton%", null, null),
                plan("black tee cotton").likePatterns());
    }

    @Test
    void categoryWordUsesTheFirstWordStillBeingSearched() {
        assertEquals("%tee%", plan("black tee").categoryWordPattern());
    }

    @Test
    void nothingTypedIsNothingInferred() {
        SearchPlan plan = plan(null);

        assertEquals(List.of(), plan.words());
        assertEquals(List.of(), plan.colors());
        assertEquals(List.of(), plan.sizes());
        assertTrue(plan.inferred().isEmpty());
    }

    @Test
    void aFacetThatIsAlreadyFullKeepsItsWordInTheText() {
        ProductSearchRequestDto query = new ProductSearchRequestDto();
        query.setKeyword("black tee");
        query.setColors(IntStream.range(0, ProductSearchRequestDto.MAX_FACET_VALUES)
                .mapToObj(index -> "shade-" + index)
                .toList());

        SearchPlan plan = SearchPlan.of(query.keywordWords(), query.normalizedColors(),
                query.normalizedSizes(), COLORS, SIZES);

        assertEquals(ProductSearchRequestDto.MAX_FACET_VALUES, plan.colors().size());
        // No chip is claimed for a filter that could not be added, and the word is still
        // searched rather than silently dropped.
        assertTrue(plan.inferred().isEmpty());
        assertEquals(List.of("black", "tee"), plan.words());
    }
}
