package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.CATEGORY_TREE;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_SLUG;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_DETAILS;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_SEARCH;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_UNIQUE_CATEGORIES;
import static com.example.nexusCommerce.configs.CacheConfig.VARIANT_COLOR_VALUES;
import static com.example.nexusCommerce.configs.CacheConfig.VARIANT_SIZE_VALUES;

import java.math.BigDecimal;
import java.util.List;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.ProductAdapter;
import com.example.nexusCommerce.dtos.CreateProductRequestDto;
import com.example.nexusCommerce.dtos.GetProductResponseDto;
import com.example.nexusCommerce.dtos.GetProductWithDetailsResponseDto;
import com.example.nexusCommerce.dtos.GetVariantResponseDto;
import com.example.nexusCommerce.dtos.ProductSearchRequestDto;
import com.example.nexusCommerce.dtos.UpdateProductRequestDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.CartItemRepository;
import com.example.nexusCommerce.repositories.ProductImageRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.repositories.WishlistItemRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductImage;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;
import com.example.nexusCommerce.utils.Slugs;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Set<String> GENDERS = Set.of("men", "women", "unisex", "kids");

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductImageRepository imageRepository;
    private final CartItemRepository cartItemRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final ProductAdapter productAdapter;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_ALL, key = "#page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetProductResponseDto> getAllProducts(int page, int size) {
        Page<Product> products = productRepository.findAll(Pagination.of(page, size));
        return PageResponse.of(products, productAdapter::mapToGetProductResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_BY_ID, key = "#id")
    @Override
    public GetProductResponseDto getProductById(Long id){
        return productRepository.findById(id)
                .map(productAdapter::mapToGetProductResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_DETAILS, key = "#id")
    @Override
    public GetProductWithDetailsResponseDto getProductWithDetailsById(Long id){
        Product product = productRepository.findProductWithDetailsById(id)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));

        return mapWithVariants(product);
    }

    /**
     * The storefront routes by slug, and a slug is stable when a product is renamed -- which is
     * why SEO URLs and the seeded catalogue both use it rather than the numeric id.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_BY_SLUG, key = "#slug.toLowerCase()", sync = true)
    @Override
    public GetProductWithDetailsResponseDto getProductBySlug(String slug){
        if (slug == null || slug.isBlank()) {
            throw new InvalidRequestException("Product slug must not be blank!");
        }
        Product product = productRepository.findBySlugIgnoreCase(slug.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product with slug '" + slug.trim() + "' Not Found!"));
        return mapWithVariants(product);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = VARIANT_SIZE_VALUES, sync = true)
    @Override
    public List<String> getAvailableSizes() {
        return productRepository.findUniqueVariantSizes().stream()
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = VARIANT_COLOR_VALUES, sync = true)
    @Override
    public List<String> getAvailableColors() {
        return productRepository.findUniqueVariantColors().stream()
                .filter(Objects::nonNull)
                .sorted()
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_SEARCH, key = "#query.cacheKey()", sync = true)
    @Override
    public PageResponse<GetProductWithDetailsResponseDto> search(ProductSearchRequestDto query) {
        validateSearch(query);

        List<String> sizes = query.normalizedSizes();
        List<String> colors = query.normalizedColors();
        // Always exactly MAX_KEYWORD_TOKENS long, padded with nulls.
        List<String> keywords = query.keywordTokens();

        // An empty list would render "IN ()"; the count parameter is the real switch and the
        // list always keeps at least one never-matching element.
        Page<Product> products = productRepository.search(
                query.normalizedGender(),
                query.normalizedCategorySlug(),
                keywords.get(0),
                keywords.get(1),
                keywords.get(2),
                keywords.get(3),
                query.getMinPrice(),
                query.getMaxPrice(),
                sizes.isEmpty() ? List.of("") : sizes,
                sizes.size(),
                colors.isEmpty() ? List.of("") : colors,
                colors.size(),
                Pagination.of(query.getPage(), query.getSize(), query.getSort(),
                        ProductSearchRequestDto.ALLOWED_SORT_FIELDS));

        return PageResponse.of(products, productAdapter::mapToGetProductWithDetailsResponseDto);
    }

    /**
     * Every facet is checked against the catalogue before the query runs. Spring Cache only
     * writes on a successful return, so rejecting an unknown gender, category, size or colour
     * here is also what stops a caller from minting an unlimited number of cache keys.
     */
    private void validateSearch(ProductSearchRequestDto query) {
        Pagination.validatePage(query.getPage());

        String gender = query.normalizedGender();
        if (gender != null && !GENDERS.contains(gender)) {
            throw new InvalidRequestException(
                    "gender must be one of " + String.join(", ", "MEN", "WOMEN", "UNISEX", "KIDS"));
        }

        String categorySlug = query.normalizedCategorySlug();
        if (categorySlug != null
                && categoryRepository.findBySlugIgnoreCase(categorySlug).isEmpty()) {
            throw new ResourceNotFoundException("Category with slug '" + categorySlug + "' Not Found!");
        }

        if (query.getMinPrice() != null && query.getMinPrice().signum() < 0) {
            throw new InvalidRequestException("minPrice must not be negative");
        }
        if (query.getMaxPrice() != null && query.getMaxPrice().signum() < 0) {
            throw new InvalidRequestException("maxPrice must not be negative");
        }
        if (query.getMinPrice() != null && query.getMaxPrice() != null
                && query.getMinPrice().compareTo(query.getMaxPrice()) > 0) {
            throw new InvalidRequestException("minPrice must not exceed maxPrice");
        }

        // Read straight from the repository: this only runs on a search-cache miss, and calling
        // the cached public methods here would self-invoke and silently bypass the proxy.
        requireKnownFacet(query.normalizedSizes(),
                productRepository.findUniqueVariantSizes(), "size");
        requireKnownFacet(query.normalizedColors(),
                productRepository.findUniqueVariantColors(), "color");
    }

    private void requireKnownFacet(List<String> requested, List<String> known, String label) {
        if (requested.size() > ProductSearchRequestDto.MAX_FACET_VALUES) {
            throw new InvalidRequestException("At most "
                    + ProductSearchRequestDto.MAX_FACET_VALUES + " " + label + " filters are allowed");
        }
        List<String> allowed = known.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toList());
        for (String value : requested) {
            if (!allowed.contains(value)) {
                // A value no product sells in is a bad request param, not a missing resource:
                // rejecting it turns a storefront typo into an error instead of an empty page.
                throw new InvalidRequestException(
                        "No products are sold in " + label + " '" + value + "'");
            }
        }
    }

    @Transactional(readOnly = true)
    @Override
    public List<GetVariantResponseDto> getVariants(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + productId + " Not Found!"));
        return productAdapter.mapToGetVariantResponseDtoList(
                variantRepository.findByProductIdOrderBySizeAscColorNameAsc(productId), product.getPrice());
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS,
            PRODUCT_BY_CATEGORY, PRODUCT_SEARCH, PRODUCT_UNIQUE_CATEGORIES, CATEGORY_TREE }, allEntries = true)
    @Override
    public GetProductResponseDto createProduct(CreateProductRequestDto requestDto){
        Category category = categoryRepository.findById(requestDto.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category with id: " + requestDto.getCategoryId() + " Not Found!"));

        if (productRepository.existsByTitleIgnoreCase(requestDto.getTitle())) {
            throw new DuplicateResourceException("Product with name '" + requestDto.getTitle() + "' already exists!");
        }

        String slug = Slugs.unique(Slugs.slugify(
                requestDto.getSlug() != null && !requestDto.getSlug().isBlank()
                        ? requestDto.getSlug() : requestDto.getTitle()),
                candidate -> productRepository.existsBySlug(candidate));

        List<ProductVariant> variants = toVariants(requestDto);
        List<ProductImage> images = toImages(requestDto);

        Product newProduct = Product.builder()
                            .title(requestDto.getTitle().trim())
                            .category(category)
                            .description(requestDto.getDescription())
                            .image(requestDto.getImage())
                            .price(requestDto.getPrice())
                            .compareAtPrice(requestDto.getCompareAtPrice())
                            .rating(requestDto.getRating())
                            // Variant stock is the truth once variants exist, so the legacy
                            // aggregate has to start as its sum rather than the request value.
                            .stock(variants.isEmpty()
                                    ? (requestDto.getStock() != null ? requestDto.getStock() : 0)
                                    : variants.stream().mapToInt(ProductVariant::getStock).sum())
                            .slug(slug)
                            .brand(trimToNull(requestDto.getBrand()))
                            .material(trimToNull(requestDto.getMaterial()))
                            .careInstructions(trimToNull(requestDto.getCareInstructions()))
                            .gender(requestDto.getGender())
                            .isActive(requestDto.getIsActive() == null || requestDto.getIsActive())
                            .build();

        Product saved = productRepository.save(newProduct);

        if (!variants.isEmpty()) {
            variants.forEach(variant -> variant.setProduct(saved));
            variantRepository.saveAll(variants);
        }
        if (!images.isEmpty()) {
            images.forEach(image -> image.setProduct(saved));
            imageRepository.saveAll(images);
            if (saved.getImage() == null) {
                saved.setImage(images.get(0).getUrl());
                productRepository.save(saved);
            }
        }

        return productAdapter.mapToGetProductResponseDto(saved);
    }

    /**
     * SKUs arrive uppercase from the catalogue sheet but V16 compares the live-sku uniqueness
     * case-insensitively, so both are normalised here and duplicates are rejected before the
     * database has to.
     */
    private List<ProductVariant> toVariants(CreateProductRequestDto requestDto) {
        if (requestDto.getVariants() == null || requestDto.getVariants().isEmpty()) {
            return List.of();
        }
        Set<String> seenSkus = new HashSet<>();
        return requestDto.getVariants().stream()
                .map(v -> {
                    String sku = v.getSku().trim().toUpperCase(Locale.ROOT);
                    if (!seenSkus.add(sku)) {
                        throw new DuplicateResourceException("Duplicate SKU '" + sku + "' in request");
                    }
                    if (variantRepository.findBySku(sku).isPresent()) {
                        throw new DuplicateResourceException("Variant with SKU '" + sku + "' already exists!");
                    }
                    return ProductVariant.builder()
                            .sku(sku)
                            .size(v.getSize().trim())
                            .colorName(v.getColorName().trim())
                            .colorHex(v.getColorHex().trim().toUpperCase(Locale.ROOT))
                            .priceDelta(v.getPriceDelta() != null ? v.getPriceDelta() : BigDecimal.ZERO)
                            .imageUrl(v.getImageUrl())
                            .stock(v.getStock() != null ? v.getStock() : 0)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private List<ProductImage> toImages(CreateProductRequestDto requestDto) {
        if (requestDto.getImages() == null || requestDto.getImages().isEmpty()) {
            return List.of();
        }
        return requestDto.getImages().stream()
                .map(i -> ProductImage.builder()
                        .url(i.getUrl().trim())
                        .position(i.getPosition() != null ? i.getPosition() : 0)
                        .alt(trimToNull(i.getAlt()))
                        .build())
                .collect(Collectors.toList());
    }

    // The facet vocabulary is derived from the variants of *active, live* products, so flipping
    // isActive here removes (or restores) every size and colour that style sells. Without these
    // two caches the storefront keeps offering a filter that now returns an empty page.
    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS,
            PRODUCT_BY_CATEGORY, PRODUCT_SEARCH, PRODUCT_UNIQUE_CATEGORIES, CATEGORY_TREE,
            VARIANT_SIZE_VALUES, VARIANT_COLOR_VALUES }, allEntries = true)
    @Override
    public GetProductResponseDto updateProduct(Long id, UpdateProductRequestDto requestDto){
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));

        if (requestDto.getTitle() != null && !requestDto.getTitle().isBlank()) {
            String title = requestDto.getTitle().trim();
            if (!product.getTitle().equalsIgnoreCase(title) && productRepository.existsByTitleIgnoreCase(title)) {
                throw new DuplicateResourceException("Product with name '" + title + "' already exists!");
            }
            product.setTitle(title);
        }
        if (requestDto.getDescription() != null) {
            product.setDescription(requestDto.getDescription());
        }
        if (requestDto.getPrice() != null) {
            if (requestDto.getPrice().signum() < 0) {
                throw new InvalidRequestException("Product price must not be negative!");
            }
            product.setPrice(requestDto.getPrice());
        }
        if (requestDto.getCompareAtPrice() != null) {
            if (requestDto.getCompareAtPrice().signum() < 0) {
                throw new InvalidRequestException("Compare-at price must not be negative!");
            }
            product.setCompareAtPrice(requestDto.getCompareAtPrice());
        }
        if (requestDto.getImage() != null) {
            product.setImage(requestDto.getImage());
        }
        if (requestDto.getRating() != null) {
            if (requestDto.getRating().signum() < 0 || requestDto.getRating().compareTo(BigDecimal.TEN) > 0) {
                throw new InvalidRequestException("Product rating must be between 0 and 10!");
            }
            product.setRating(requestDto.getRating());
        }
        if (requestDto.getStock() != null) {
            if (requestDto.getStock() < 0) {
                throw new InvalidRequestException("Stock must not be negative!");
            }
            setAggregateStock(product, requestDto.getStock());
        }
        if (requestDto.getCategoryId() != null) {
            Category category = categoryRepository.findById(requestDto.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Category with id: " + requestDto.getCategoryId() + " Not Found!"));
            product.setCategory(category);
        }
        if (requestDto.getSlug() != null && !requestDto.getSlug().isBlank()) {
            String slug = Slugs.slugify(requestDto.getSlug());
            if (!slug.equals(product.getSlug()) && productRepository.existsBySlug(slug)) {
                throw new DuplicateResourceException("Product with slug '" + slug + "' already exists!");
            }
            product.setSlug(slug);
        }
        if (requestDto.getBrand() != null) {
            product.setBrand(trimToNull(requestDto.getBrand()));
        }
        if (requestDto.getMaterial() != null) {
            product.setMaterial(trimToNull(requestDto.getMaterial()));
        }
        if (requestDto.getCareInstructions() != null) {
            product.setCareInstructions(trimToNull(requestDto.getCareInstructions()));
        }
        if (requestDto.getGender() != null) {
            product.setGender(requestDto.getGender().isBlank() ? null : requestDto.getGender());
        }
        if (requestDto.getIsActive() != null) {
            product.setIsActive(requestDto.getIsActive());
        }

        return productAdapter.mapToGetProductResponseDto(productRepository.save(product));
    }

    /**
     * A product with a size+colour grid cannot have its stock set as one number: the variants
     * are the source of truth and writing the aggregate alone would be undone by the next
     * resync. Single-variant products keep working through that one variant.
     */
    private void setAggregateStock(Product product, int stock) {
        long variantCount = variantRepository.countByProductId(product.getId());
        if (variantCount == 1) {
            ProductVariant only = variantRepository
                    .findFirstByProductIdOrderByCreatedAtAsc(product.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Variant of product " + product.getId() + " Not Found!"));
            only.setStock(stock);
            variantRepository.save(only);
            product.setStock(stock);
        } else if (variantCount > 1) {
            throw new InvalidRequestException("Product " + product.getId() + " has " + variantCount
                    + " variants; set stock on each variant instead of on the product");
        } else {
            product.setStock(stock);
        }
    }

    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS,
            PRODUCT_BY_CATEGORY, PRODUCT_SEARCH, CATEGORY_TREE }, allEntries = true)
    @Override
    public GetProductResponseDto setStock(Long id, Integer stock){
        if (stock == null || stock < 0) {
            throw new InvalidRequestException("Stock must not be negative!");
        }
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id: " + id + " Not Found!"));
        setAggregateStock(product, stock);
        return productAdapter.mapToGetProductResponseDto(productRepository.save(product));
    }

    // A style that is gone cannot be bought in any of its sizes. Leaving the variant rows live
    // would keep their SKUs claimed by the unique index forever and let an admin edit stock on
    // a product that no reader can find, so the whole grid is retired with the product. Live
    // carts and saved wishes are retired for the same reason: a line whose product no longer
    // resolves makes GET /cart throw for that shopper, and a wish that cannot be bought is a
    // broken promise. Reviews are NOT retired here — they are purchase history, and Review keeps
    // readable through the soft-delete restriction via its read-only FK mirrors.
    @Transactional
    @CacheEvict(cacheNames = { PRODUCT_ALL, PRODUCT_BY_ID, PRODUCT_BY_SLUG, PRODUCT_DETAILS,
            PRODUCT_BY_CATEGORY, PRODUCT_SEARCH, PRODUCT_UNIQUE_CATEGORIES, CATEGORY_TREE,
            VARIANT_SIZE_VALUES, VARIANT_COLOR_VALUES }, allEntries = true)
    @Override
    public void deleteProduct(Long id){
        if (!productRepository.existsById(id)) {
            throw new ResourceNotFoundException("Product with id: " + id + " Not Found!");
        }
        variantRepository.findByProductIdOrderBySizeAscColorNameAsc(id)
                .forEach(variant -> variantRepository.deleteById(variant.getId()));
        imageRepository.findByProductIdOrderByPositionAsc(id).forEach(imageRepository::delete);
        productRepository.deleteById(id);
        cartItemRepository.retireByProduct(id);
        wishlistItemRepository.retireByProduct(id);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_BY_CATEGORY,
            key = "#category.trim().toLowerCase() + ':' + #page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetProductWithDetailsResponseDto> findByCategory(String category, int page, int size){
        if (category == null || category.isBlank()) {
            throw new InvalidRequestException("Category name must not be blank!");
        }

        // Unknown categories are rejected before any result exists, so arbitrary search
        // terms cannot mint cache keys: exceptions are never cached.
        categoryRepository.findFirstByNameIgnoreCaseOrderByIdAsc(category.trim())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Category with name '" + category.trim() + "' Not Found!"));

        Page<Product> products = productRepository.findByCategory_NameIgnoreCase(
                category.trim(), Pagination.of(page, size));
        return PageResponse.of(products, productAdapter::mapToGetProductWithDetailsResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = PRODUCT_UNIQUE_CATEGORIES, sync = true)
    @Override
    public List<String> getUniqueCategories() {
        return productRepository.findUniqueCategory()
                .stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /** Loads the lazy variant and gallery collections inside the read transaction, then maps. */
    private GetProductWithDetailsResponseDto mapWithVariants(Product product) {
        return productAdapter.mapToGetProductWithDetailsResponseDto(product,
                variantRepository.findByProductIdOrderBySizeAscColorNameAsc(product.getId()),
                imageRepository.findByProductIdOrderByPositionAsc(product.getId()));
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
