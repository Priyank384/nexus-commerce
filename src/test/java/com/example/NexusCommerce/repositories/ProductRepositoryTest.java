package com.example.nexusCommerce.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;

import com.example.nexusCommerce.configs.JpaAuditingConfig;
import com.example.nexusCommerce.dtos.ProductSearchRequestDto;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;

@DataJpaTest
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.ANY)
@Import(JpaAuditingConfig.class)
public class ProductRepositoryTest {

    @Autowired
    private TestEntityManager testEntityManager;

    @Autowired
    private ProductRepository productRepository;

    private Category electronicsCategory;
    private Category booksCategory;
    private Product phone;
    private Product laptop;

    @BeforeEach
    void setUp() {
        electronicsCategory = Category.builder().name("Electronics").build();
        booksCategory = Category.builder().name("Books").build();

        phone = Product.builder()
                .title("Phone")
                .description("A Phone")
                .price(BigDecimal.valueOf(999.0).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.5))
                .category(electronicsCategory)
                .build();

        laptop = Product.builder()
                .title("Laptop")
                .description("A Laptop")
                .price(BigDecimal.valueOf(1499.0).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.8))
                .category(electronicsCategory)
                .build();

        testEntityManager.persistAndFlush(electronicsCategory);
        testEntityManager.persistAndFlush(booksCategory);
        testEntityManager.persistAndFlush(phone);
        testEntityManager.persistAndFlush(laptop);

        testEntityManager.clear();
    }

    @Test
    void findProductWithDetailsById_whenFound_returnsProductWithCategory() {
        List<Product> result = productRepository.findProductWithDetailsById(phone.getId());

        assertEquals(1, result.size());
        assertEquals("Phone", result.get(0).getTitle());
        assertEquals("Electronics", result.get(0).getCategory().getName());
    }

    @Test
    void findByCategoryNameIgnoreCase_whenCategoryMatches_returnsProducts() {
        List<Product> result = productRepository
                .findByCategory_NameIgnoreCaseAndIsActiveTrue("electronics", Pageable.unpaged()).getContent();

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(p -> "Phone".equals(p.getTitle())));
        assertTrue(result.stream().anyMatch(p -> "Laptop".equals(p.getTitle())));
    }

    @Test
    void findByCategoryNameIgnoreCase_excludesUnpublishedProducts() {
        deactivate(persistProduct("Hidden Phone", electronicsCategory));

        List<Product> result = productRepository
                .findByCategory_NameIgnoreCaseAndIsActiveTrue("electronics", Pageable.unpaged()).getContent();

        assertEquals(List.of("Laptop", "Phone"), titles(result));
    }

    @Test
    void findByIsActiveTrue_hidesUnpublishedProducts() {
        deactivate(laptop);

        assertEquals(List.of("Phone"),
                titles(productRepository.findByIsActiveTrue(Pageable.unpaged()).getContent()));
    }

    @Test
    void searchForAdmin_includesDraftsAndFiltersOnKeyword() {
        deactivate(laptop);

        assertEquals(List.of("Laptop", "Phone"),
                titles(productRepository.searchForAdmin(null, null, Pageable.unpaged()).getContent()));
        assertEquals(List.of("Laptop"),
                titles(productRepository.searchForAdmin("%laptop%", null, Pageable.unpaged()).getContent()));
        assertEquals(List.of("Laptop", "Phone"),
                titles(productRepository.searchForAdmin("%electronics%", null, Pageable.unpaged()).getContent()));
    }

    @Test
    void searchForAdmin_outOfStockCeilingKeepsOnlyZeroStockProducts() {
        // Both fixtures start at the zero default, so the ceiling is the only filter in play.
        assertEquals(List.of("Laptop", "Phone"),
                titles(productRepository.searchForAdmin(null, 0, Pageable.unpaged()).getContent()));

        phone.setStock(4);
        testEntityManager.merge(phone);
        testEntityManager.flush();
        testEntityManager.clear();

        assertEquals(List.of("Laptop"),
                titles(productRepository.searchForAdmin(null, 0, Pageable.unpaged()).getContent()));
    }

    @Test
    void findByCategoryNameIgnoreCase_whenCategoryDoesNotMatch_returnsEmptyList() {
        List<Product> result = productRepository
                .findByCategory_NameIgnoreCaseAndIsActiveTrue("Books", Pageable.unpaged()).getContent();

        assertTrue(result.isEmpty());
    }

    @Test
    void existsByTitleIgnoreCase_whenTitleExists_returnsTrue() {
        assertTrue(productRepository.existsByTitleIgnoreCase("phone"));
    }

    @Test
    void existsByTitleIgnoreCase_whenTitleDoesNotExist_returnsFalse() {
        assertFalse(productRepository.existsByTitleIgnoreCase("Tablet"));
    }

    @Test
    void findUniqueCategory_returnsDistinctCategoryNames() {
        Product book = Product.builder()
                .title("Novel")
                .description("A Novel")
                .price(BigDecimal.valueOf(19.99).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.2))
                .category(booksCategory)
                .build();
        testEntityManager.persistAndFlush(book);
        testEntityManager.clear();

        List<String> result = productRepository.findUniqueCategory();

        assertEquals(2, result.size());
        assertTrue(result.contains("Electronics"));
        assertTrue(result.contains("Books"));
    }

    @Test
    void findUniqueVariantSizes_hidesSizesOfUnpublishedProducts() {
        persistVariant(phone, "M", "Bone");
        Product unpublished = persistProduct("Retired Tee", electronicsCategory);
        unpublished.setIsActive(false);
        persistVariant(unpublished, "XXL", "Bone");
        testEntityManager.flush();
        testEntityManager.clear();

        assertEquals(List.of("M"), productRepository.findUniqueVariantSizes());
        assertEquals(List.of("Bone"), productRepository.findUniqueVariantColors());
    }

    @Test
    void findUniqueVariantSizes_hidesVariantsOfSoftDeletedProducts() {
        Product retiring = persistProduct("Seasonal Tee", electronicsCategory);
        persistVariant(phone, "M", "Bone");
        persistVariant(retiring, "XS", "Olive");
        testEntityManager.flush();
        testEntityManager.clear();

        productRepository.deleteById(retiring.getId());
        testEntityManager.flush();
        testEntityManager.clear();

        assertEquals(List.of("M"), productRepository.findUniqueVariantSizes());
        assertEquals(List.of("Bone"), productRepository.findUniqueVariantColors());
    }

    @Test
    void search_whenKeywordMatchesTitle_returnsOnlyThatProduct() {
        assertEquals(List.of("Laptop"), titles(search("lap")));
    }

    @Test
    void search_whenKeywordMatchesDescriptionOrCategory_findsTheProductAnyway() {
        persistBook();

        // "paperback" appears only in the description, "electronics" only in the category name.
        assertEquals(List.of("Novel"), titles(search("paperback")));
        assertEquals(List.of("Laptop", "Phone"), titles(search("electronics")));
    }

    @Test
    void search_ANDsTheWordsRatherThanMatchingThePhrase() {
        // "laptop a" is not a contiguous substring of anything, but both words are present,
        // so a phrase LIKE would answer this reasonable query with an empty page.
        assertEquals(List.of("Laptop"), titles(search("laptop a")));
    }

    @Test
    void search_whenKeywordIsAbsent_returnsEveryActiveProduct() {
        assertEquals(2, search(null).size());
    }

    @Test
    void search_whenNothingMatches_returnsEmptyPage() {
        assertTrue(search("tablet").isEmpty());
    }

    @Test
    void search_neverReturnsUnpublishedProducts() {
        deactivate(laptop);

        // "a" appears in both titles/descriptions, so only the active flag can exclude Laptop.
        assertEquals(List.of("Phone"), titles(search("a")));
    }

    @Test
    void search_combinesKeywordWithPriceRange() {
        ProductSearchRequestDto query = new ProductSearchRequestDto();
        query.setKeyword("a");
        query.setMinPrice(BigDecimal.valueOf(1200));
        List<String> result = run(query).stream().map(Product::getTitle).toList();

        // Both titles/descriptions contain "a", but only Laptop clears the floor.
        assertEquals(List.of("Laptop"), result);
    }

    /** Goes through the DTO so the test exercises the same normalisation the HTTP path does. */
    private List<Product> search(String keyword) {
        ProductSearchRequestDto query = new ProductSearchRequestDto();
        query.setKeyword(keyword);
        return run(query);
    }

    private List<Product> run(ProductSearchRequestDto query) {
        List<String> keywords = query.keywordTokens();
        List<String> sizes = query.normalizedSizes();
        List<String> colors = query.normalizedColors();
        return productRepository.search(query.normalizedGender(), query.normalizedCategorySlug(),
                keywords.get(0), keywords.get(1), keywords.get(2), keywords.get(3),
                query.getMinPrice(), query.getMaxPrice(),
                sizes.isEmpty() ? List.of("") : sizes, sizes.size(),
                colors.isEmpty() ? List.of("") : colors, colors.size(),
                Pageable.unpaged()).getContent();
    }

    private Product persistBook() {
        return testEntityManager.persistAndFlush(Product.builder()
                .title("Novel")
                .description("A paperback")
                .price(BigDecimal.valueOf(19.99).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.2))
                .category(booksCategory)
                .build());
    }

    private List<String> titles(List<Product> products) {
        return products.stream().map(Product::getTitle).sorted().toList();
    }

    private Product persistProduct(String title, Category category) {
        return testEntityManager.persistAndFlush(Product.builder()
                .title(title)
                .description(title)
                .price(BigDecimal.valueOf(890).setScale(2, RoundingMode.HALF_UP))
                .rating(BigDecimal.valueOf(4.3))
                .category(category)
                .build());
    }

    /**
     * setUp clears the persistence context, so an entity it created is detached; a plain setter
     * would never reach the database. Merge first, then clear so the queries see the new state.
     */
    private void deactivate(Product product) {
        product.setIsActive(false);
        testEntityManager.merge(product);
        testEntityManager.flush();
        testEntityManager.clear();
    }

    private void persistVariant(Product product, String size, String colorName) {
        testEntityManager.persistAndFlush(ProductVariant.builder()
                .product(product)
                .sku(product.getTitle() + "-" + size + "-" + colorName)
                .size(size)
                .colorName(colorName)
                .colorHex("#FFFFFF")
                .stock(5)
                .build());
    }
}
