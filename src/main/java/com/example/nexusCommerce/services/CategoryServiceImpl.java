package com.example.nexusCommerce.services;

import static com.example.nexusCommerce.configs.CacheConfig.CATEGORY_ALL;
import static com.example.nexusCommerce.configs.CacheConfig.CATEGORY_BY_ID;
import static com.example.nexusCommerce.configs.CacheConfig.CATEGORY_TREE;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_BY_CATEGORY;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_SEARCH;
import static com.example.nexusCommerce.configs.CacheConfig.PRODUCT_UNIQUE_CATEGORIES;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.nexusCommerce.adapters.CategoryAdapter;
import com.example.nexusCommerce.dtos.CreateCategoryRequestDto;
import com.example.nexusCommerce.dtos.GetCategoryNodeResponseDto;
import com.example.nexusCommerce.dtos.GetCategoryResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.CategoryRepository;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.schema.Category;
import com.example.nexusCommerce.utils.PageResponse;
import com.example.nexusCommerce.utils.Pagination;
import com.example.nexusCommerce.utils.Slugs;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryAdapter categoryAdapter;

    // A category name or slug appears in the product search and unique-category caches, so
    // those have to be invalidated alongside the category caches.
    @Transactional
    @CacheEvict(cacheNames = { CATEGORY_ALL, CATEGORY_BY_ID, CATEGORY_TREE, PRODUCT_BY_CATEGORY,
            PRODUCT_SEARCH, PRODUCT_UNIQUE_CATEGORIES }, allEntries = true)
    @Override
    public GetCategoryResponseDto createCategory(CreateCategoryRequestDto requestDto){
        String normalizedName = validateAndNormalizeName(requestDto);
        Long parentId = requestDto.getParentId();
        Category parent = null;
        if (parentId != null) {
            parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category with id: " + parentId + " Not Found!"));
        }
        // V15 scoped the unique name to the parent, so this must not be a global check.
        if (nameTaken(normalizedName, parentId, null)) {
            throw new DuplicateResourceException(
                    "Category with name '" + normalizedName + "' already exists under this parent!");
        }

        String slug = Slugs.unique(Slugs.slugify(
                requestDto.getSlug() != null && !requestDto.getSlug().isBlank()
                        ? requestDto.getSlug() : normalizedName),
                candidate -> categoryRepository.findBySlugIgnoreCase(candidate).isPresent());

        Category newCategory = Category.builder()
                            .name(normalizedName)
                            .description(trimToNull(requestDto.getDescription()))
                            .slug(slug)
                            .parent(parent)
                            .build();

        return categoryAdapter.mapToGetCategoryResponseDto(categoryRepository.save(newCategory));
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CATEGORY_ALL, key = "#page + ':' + #size", sync = true)
    @Override
    public PageResponse<GetCategoryResponseDto> getAllCategory(int page, int size){
        return PageResponse.of(categoryRepository.findAll(Pagination.of(page, size)),
                categoryAdapter::mapToGetCategoryResponseDto);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CATEGORY_BY_ID, key = "#id")
    @Override
    public GetCategoryResponseDto getCategoryById(Long id){
        return categoryRepository.findById(id)
                .map(categoryAdapter::mapToGetCategoryResponseDto)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));
    }

    /**
     * One query for the whole tree, assembled in memory. Walking parent-to-child with a
     * repository call per node would be an N+1 on every page load, and {@code children} is a
     * lazy collection that cannot be traversed once the transaction has closed.
     */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CATEGORY_TREE, sync = true)
    @Override
    public List<GetCategoryNodeResponseDto> getCategoryTree(){
        List<Category> all = categoryRepository.findAll();

        Map<Long, GetCategoryNodeResponseDto> nodesById = new LinkedHashMap<>();
        for (Category category : all) {
            nodesById.put(category.getId(), categoryAdapter.mapToGetCategoryNodeResponseDto(category));
        }

        List<GetCategoryNodeResponseDto> roots = new ArrayList<>();
        for (Category category : all) {
            GetCategoryNodeResponseDto node = nodesById.get(category.getId());
            GetCategoryNodeResponseDto parent = category.getParentId() == null
                    ? null : nodesById.get(category.getParentId());
            if (parent != null) {
                parent.getChildren().add(node);
            } else {
                // A null parent makes a root; a parent that is missing from the result set
                // (soft-deleted above a live child) also surfaces as a root rather than
                // silently dropping the subtree.
                roots.add(node);
            }
        }
        return roots;
    }

    @Transactional
    @CacheEvict(cacheNames = { CATEGORY_ALL, CATEGORY_BY_ID, CATEGORY_TREE, PRODUCT_BY_CATEGORY,
            PRODUCT_SEARCH, PRODUCT_UNIQUE_CATEGORIES }, allEntries = true)
    @Override
    public void deleteCategory(Long id){
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));
        // Soft-deleting a category that still owns live products would orphan them: they'd
        // vanish from every category-scoped read while the products themselves stay active.
        if (productRepository.existsByCategory_Id(id)) {
            throw new InvalidRequestException(
                    "Cannot delete category with id: " + id + " because it still contains products");
        }
        if (categoryRepository.countByParentId(id) > 0) {
            throw new InvalidRequestException(
                    "Cannot delete category with id: " + id + " because it still has subcategories");
        }
        categoryRepository.deleteById(id);
    }

    @Transactional
    @CacheEvict(cacheNames = { CATEGORY_ALL, CATEGORY_BY_ID, CATEGORY_TREE, PRODUCT_BY_CATEGORY,
            PRODUCT_SEARCH, PRODUCT_UNIQUE_CATEGORIES }, allEntries = true)
    @Override
    public GetCategoryResponseDto updateCategory(Long id, CreateCategoryRequestDto requestDto) {
        String normalizedName = validateAndNormalizeName(requestDto);
        Category existingCategory = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id: " + id + " Not Found!"));

        Long parentId = requestDto.getParentId() != null ? requestDto.getParentId() : existingCategory.getParentId();
        if (id.equals(parentId)) {
            throw new InvalidRequestException("A category cannot be its own parent");
        }
        if (parentId != null) {
            categoryRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Parent category with id: " + parentId + " Not Found!"));
        }
        if (nameTaken(normalizedName, parentId, id)) {
            throw new DuplicateResourceException(
                    "Category with name '" + normalizedName + "' already exists under this parent!");
        }

        existingCategory.setName(normalizedName);
        if (requestDto.getDescription() != null) {
            existingCategory.setDescription(trimToNull(requestDto.getDescription()));
        }
        if (requestDto.getParentId() != null && !requestDto.getParentId().equals(existingCategory.getParentId())) {
            reparent(existingCategory, requestDto.getParentId());
        }
        if (requestDto.getSlug() != null && !requestDto.getSlug().isBlank()) {
            String slug = Slugs.slugify(requestDto.getSlug());
            if (slug != null && !slug.equals(existingCategory.getSlug())
                    && categoryRepository.findBySlugIgnoreCase(slug).isPresent()) {
                throw new DuplicateResourceException("Category with slug '" + slug + "' already exists!");
            }
            existingCategory.setSlug(slug);
        } else if (existingCategory.getSlug() == null) {
            existingCategory.setSlug(Slugs.unique(Slugs.slugify(normalizedName),
                    candidate -> hasSlugOtherThan(candidate, id)));
        }

        return categoryAdapter.mapToGetCategoryResponseDto(categoryRepository.save(existingCategory));
    }

    /**
     * Reparenting through the read-only {@code parent_id} column needs the association itself,
     * and the subtree has to be checked for a cycle: moving Men under Men/Denim would make the
     * whole branch unreadable from the tree.
     */
    private void reparent(Category category, Long newParentId) {
        Category parent = categoryRepository.findById(newParentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Parent category with id: " + newParentId + " Not Found!"));
        for (Category cursor = parent; cursor != null; ) {
            if (cursor.getId().equals(category.getId())) {
                throw new InvalidRequestException(
                        "Cannot move category " + category.getId() + " under its own descendant");
            }
            cursor = cursor.getParentId() == null
                    ? null : categoryRepository.findById(cursor.getParentId()).orElse(null);
        }
        category.setParent(parent);
        category.setParentId(newParentId);
    }

    private boolean hasSlugOtherThan(String slug, Long id) {
        return categoryRepository.findBySlugIgnoreCase(slug)
                .filter(other -> !other.getId().equals(id))
                .isPresent();
    }

    private boolean nameTaken(String name, Long parentId, Long excludedId) {
        var match = parentId == null
                ? categoryRepository.findByNameIgnoreCaseAndParentIdIsNull(name)
                : categoryRepository.findByNameIgnoreCaseAndParentId(name, parentId);
        return match.filter(category -> !category.getId().equals(excludedId)).isPresent();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String validateAndNormalizeName(CreateCategoryRequestDto requestDto) {
        if (requestDto == null || requestDto.getName() == null || requestDto.getName().isBlank()) {
            throw new InvalidRequestException("Category name must not be blank!");
        }
        return requestDto.getName().trim();
    }
}
