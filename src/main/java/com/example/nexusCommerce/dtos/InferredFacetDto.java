package com.example.nexusCommerce.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A facet the search took from the words the shopper typed rather than from the filter panel,
 * and the word it came from. The storefront renders these as chips the shopper can take off:
 * "black tee" is searched as colour=Black plus the text "tee", and {@code textQuery} in the
 * same response is what removing a chip sends.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InferredFacetDto {

    /** {@code color} or {@code size} -- which panel filter this word became. */
    private String facet;

    /** The catalogue's own spelling, so the chip says "Black" rather than "blck". */
    private String value;

    /** The word as typed, which is how a near-miss like "blck" can be explained back. */
    private String word;
}
