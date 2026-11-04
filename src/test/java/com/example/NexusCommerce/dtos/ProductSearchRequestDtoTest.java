package com.example.nexusCommerce.dtos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * The normalised form is what both the SQL patterns and the Redis cache key are built from, so
 * a query that differs only in case, spacing or padding must produce the identical key —
 * otherwise every shopper's typo gets its own cache entry.
 */
class ProductSearchRequestDtoTest {

    private ProductSearchRequestDto withKeyword(String keyword) {
        ProductSearchRequestDto query = new ProductSearchRequestDto();
        query.setKeyword(keyword);
        return query;
    }

    @Test
    void normalizedKeyword_foldsCaseAndWhitespace() {
        assertEquals("organic cotton crew tee",
                withKeyword("  Organic   Cotton  Crew  TEE ").normalizedKeyword());
    }

    @Test
    void normalizedKeyword_treatsBlankAndNullAsAbsent() {
        assertNull(withKeyword(null).normalizedKeyword());
        assertNull(withKeyword("   ").normalizedKeyword());
        // Metacharacters become spaces, so a query of nothing but wildcards is no query at all.
        assertNull(withKeyword("%_\\").normalizedKeyword());
    }

    @Test
    void normalizedKeyword_turnsLikeMetacharactersIntoWordBoundaries() {
        assertEquals("100 cotton", withKeyword("100% cotton").normalizedKeyword());
        assertEquals("selvedge denim", withKeyword("selvedge_denim").normalizedKeyword());
    }

    @Test
    void normalizedKeyword_clampsLength() {
        assertEquals("x".repeat(ProductSearchRequestDto.MAX_KEYWORD_LENGTH),
                withKeyword("x".repeat(500)).normalizedKeyword());
    }

    @Test
    void keywordWords_splitsThePhrase() {
        assertEquals(List.of("organic", "crew", "tee"),
                withKeyword("organic crew tee").keywordWords());
    }

    @Test
    void keywordWords_isEmptyWhenThereIsNoKeyword() {
        assertEquals(List.of(), withKeyword("   ").keywordWords());
    }

    @Test
    void keywordWords_capsTheWordCount() {
        // Words past the cap are ignored by the query, which is why the cache key uses the whole
        // phrase instead of these words.
        assertEquals(List.of("a", "b", "c", "d"), withKeyword("a b c d e f").keywordWords());
    }

    @Test
    void cacheKey_distinguishesKeywordsButNotTheirFormatting() {
        assertEquals(withKeyword("denim").cacheKey(), withKeyword("  DENIM ").cacheKey());
        assertNotEquals(withKeyword("denim").cacheKey(), withKeyword("jacket").cacheKey());
    }

    @Test
    void cacheKey_keepsWordsPastTheTokenCapDistinct() {
        // The query ignores the fifth word, but two different phrases must not share an entry.
        assertNotEquals(withKeyword("a b c d e").cacheKey(), withKeyword("a b c d f").cacheKey());
    }

    @Test
    void cacheKey_isStableAcrossFacetOrderAndCase() {
        ProductSearchRequestDto first = new ProductSearchRequestDto();
        first.setSizes(List.of("M", "S"));
        first.setColors(List.of("Bone"));
        ProductSearchRequestDto second = new ProductSearchRequestDto();
        second.setSizes(List.of("s", "m"));
        second.setColors(List.of("BONE"));
        assertEquals(first.cacheKey(), second.cacheKey());
    }

    @Test
    void cacheKey_foldsTheDefaultSortIntoAnOmittedOne() {
        // This collapse is why search() decides its query path on the canonical sort: an entry
        // written by one of these requests is read back by the other.
        ProductSearchRequestDto unset = new ProductSearchRequestDto();
        ProductSearchRequestDto explicit = new ProductSearchRequestDto();
        explicit.setSort("id,asc");
        ProductSearchRequestDto priceDesc = new ProductSearchRequestDto();
        priceDesc.setSort("price,desc");

        assertEquals(unset.cacheKey(), explicit.cacheKey());
        assertEquals("id:asc", unset.normalizedSort());
        assertNotEquals(unset.cacheKey(), priceDesc.cacheKey());
    }
}
