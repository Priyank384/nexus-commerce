package com.example.nexusCommerce.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.example.nexusCommerce.adapters.WishlistAdapter;
import com.example.nexusCommerce.dtos.AddToWishlistRequestDto;
import com.example.nexusCommerce.dtos.CreateWishlistRequestDto;
import com.example.nexusCommerce.dtos.GetWishlistResponseDto;
import com.example.nexusCommerce.exceptions.DuplicateResourceException;
import com.example.nexusCommerce.exceptions.InvalidRequestException;
import com.example.nexusCommerce.exceptions.ResourceNotFoundException;
import com.example.nexusCommerce.repositories.ProductRepository;
import com.example.nexusCommerce.repositories.ProductVariantRepository;
import com.example.nexusCommerce.repositories.UserRepository;
import com.example.nexusCommerce.repositories.WishlistItemRepository;
import com.example.nexusCommerce.repositories.WishlistRepository;
import com.example.nexusCommerce.schema.Product;
import com.example.nexusCommerce.schema.ProductVariant;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.schema.Wishlist;
import com.example.nexusCommerce.schema.WishlistItem;

/**
 * Uses the real {@link WishlistAdapter} rather than a mock: the regressions this file guards
 * were in how the service resolved ids for the adapter, and a stubbed mapper would have
 * asserted the bug away.
 */
// The repositories are stubbed as a stateful in-memory store rather than per-test, so the
// "unnecessary stubbing" check does not apply to this class: those stubs are the schema, not
// expectations.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WishlistServiceTest {

    @Mock private WishlistRepository wishlistRepository;
    @Mock private WishlistItemRepository itemRepository;
    @Mock private ProductRepository productRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private UserRepository userRepository;

    private WishlistServiceImpl service;

    private final List<Wishlist> wishlists = new ArrayList<>();
    private final List<WishlistItem> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new WishlistServiceImpl(wishlistRepository, itemRepository, productRepository,
                variantRepository, userRepository, new WishlistAdapter());

        when(wishlistRepository.save(any(Wishlist.class))).thenAnswer(inv -> {
            Wishlist w = inv.getArgument(0);
            if (w.getId() == null) {
                w.setId(1L);
            }
            if (!wishlists.contains(w)) {
                wishlists.add(w);
            }
            return w;
        });
        when(wishlistRepository.findById(anyLong()))
                .thenAnswer(inv -> wishlists.stream().filter(w -> w.getId().equals(inv.getArgument(0)))
                        .findFirst());
        when(wishlistRepository.findFirstByUserIdOrderByCreatedAtAsc(anyLong()))
                .thenAnswer(inv -> wishlists.stream()
                        .filter(w -> ownerId(w).equals(inv.getArgument(0))).findFirst());
        when(wishlistRepository.findByUserId(anyLong()))
                .thenAnswer(inv -> wishlists.stream().filter(w -> ownerId(w).equals(inv.getArgument(0))).toList());
        when(wishlistRepository.findByUserIdAndNameIgnoreCase(anyLong(), any()))
                .thenAnswer(inv -> wishlists.stream().filter(w -> ownerId(w).equals(inv.getArgument(0)))
                        .filter(w -> w.getName().equalsIgnoreCase((String) inv.getArgument(1))).findFirst());

        when(itemRepository.save(any(WishlistItem.class))).thenAnswer(inv -> {
            WishlistItem item = inv.getArgument(0);
            if (item.getId() == null) {
                item.setId(50L);
            }
            if (!items.contains(item)) {
                items.add(item);
            }
            return item;
        });
        when(itemRepository.findByWishlistIdOrderByCreatedAtDesc(anyLong()))
                .thenAnswer(inv -> items.stream().filter(i -> listId(i).equals(inv.getArgument(0))).toList());
        when(itemRepository.findByWishlistIdAndProductId(anyLong(), anyLong()))
                .thenAnswer(inv -> items.stream()
                        .filter(i -> listId(i).equals(inv.getArgument(0)))
                        .filter(i -> productIdOf(i).equals(inv.getArgument(1)))
                        .findFirst());
        doAnswer(inv -> { items.remove((WishlistItem) inv.getArgument(0)); return null; })
                .when(itemRepository).delete(any(WishlistItem.class));
        doAnswer(inv -> { wishlists.remove((Wishlist) inv.getArgument(0)); return null; })
                .when(wishlistRepository).delete(any(Wishlist.class));

        when(itemRepository.countByWishlistId(anyLong()))
                .thenAnswer(inv -> items.stream().filter(i -> listId(i).equals(inv.getArgument(0))).count());

        when(productRepository.findAllById(any()))
                .thenAnswer(inv -> productsFor(inv.getArgument(0)));
        when(variantRepository.findAllById(any()))
                .thenAnswer(inv -> variantsFor(inv.getArgument(0)));
    }

    private User user(Long id) {
        User user = User.builder().name("Shopper " + id).email(id + "@example.com").build();
        user.setId(id);
        return user;
    }

    private Product product(Long id) {
        Product product = Product.builder().title("Organic Cotton Crew Tee " + id)
                .price(new BigDecimal("890.00")).image("tee.jpg").build();
        product.setId(id);
        return product;
    }

    private ProductVariant variant(Long id, Long productId) {
        ProductVariant variant = ProductVariant.builder().sku("PT-" + id).size("M")
                .colorName("Black").colorHex("#0F0F0F").stock(10).build();
        variant.setId(id);
        variant.setProductId(productId);
        return variant;
    }

    private Wishlist existingWishlist(Long userId, String name) {
        Wishlist wishlist = Wishlist.builder().user(user(userId)).name(name).build();
        wishlist.setId((long) (wishlists.size() + 1));
        wishlists.add(wishlist);
        return wishlist;
    }

    private List<Product> productsFor(Iterable<Long> ids) {
        List<Product> found = new ArrayList<>();
        for (Long id : ids) {
            found.add(product(id));
        }
        return found;
    }

    private List<ProductVariant> variantsFor(Iterable<Long> ids) {
        List<ProductVariant> found = new ArrayList<>();
        for (Long id : ids) {
            found.add(variant(id, 1101L));
        }
        return found;
    }

    private static Long ownerId(Wishlist wishlist) {
        return wishlist.getUserId() != null ? wishlist.getUserId() : wishlist.getUser().getId();
    }

    private static Long listId(WishlistItem item) {
        return item.getWishlistId() != null ? item.getWishlistId() : item.getWishlist().getId();
    }

    private static Long productIdOf(WishlistItem item) {
        return item.getProductId() != null ? item.getProductId() : item.getProduct().getId();
    }

    @Test
    void getMyWishlist_onFirstVisit_showsAnEmptyListWithoutCreatingARow() {
        GetWishlistResponseDto result = service.getMyWishlist(1L);

        assertEquals(0, result.getItemCount());
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void addItem_materialisesTheDefaultListAndAnswersWithTheFullLine() {
        // The just-saved item has no product_id scalar (the column is insertable=false), so a
        // read model keyed off that scalar answered every field null -- the storefront's heart
        // icon had nothing to render until the next page load.
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(productRepository.findById(1101L)).thenReturn(Optional.of(product(1101L)));

        GetWishlistResponseDto result = service.addItem(1L, null,
                AddToWishlistRequestDto.builder().productId(1101L).build());

        assertEquals(WishlistServiceImpl.DEFAULT_NAME, result.getName());
        assertEquals(1, result.getItemCount());
        assertEquals(1101L, result.getItems().get(0).getProductId());
        assertEquals("Organic Cotton Crew Tee 1101", result.getItems().get(0).getProductTitle());
        assertNotNull(result.getItems().get(0).getPrice());
    }

    @Test
    void addItem_resolvesAVariantLineOnTheSameWriteResponse() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(productRepository.findById(1101L)).thenReturn(Optional.of(product(1101L)));
        when(variantRepository.findById(21L)).thenReturn(Optional.of(variant(21L, 1101L)));

        GetWishlistResponseDto result = service.addItem(1L, null,
                AddToWishlistRequestDto.builder().productId(1101L).variantId(21L).build());

        assertEquals(21L, result.getItems().get(0).getVariantId());
        assertEquals("M", result.getItems().get(0).getSize());
        assertEquals("PT-21", result.getItems().get(0).getSku());
    }

    @Test
    void addItem_twiceForTheSameProductRestatesTheLineInsteadOfDuplicatingIt() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(productRepository.findById(1101L)).thenReturn(Optional.of(product(1101L)));
        when(variantRepository.findById(21L)).thenReturn(Optional.of(variant(21L, 1101L)));
        when(variantRepository.findById(22L)).thenReturn(Optional.of(variant(22L, 1101L)));

        service.addItem(1L, null, AddToWishlistRequestDto.builder().productId(1101L).variantId(21L).build());
        GetWishlistResponseDto second = service.addItem(1L, null,
                AddToWishlistRequestDto.builder().productId(1101L).variantId(22L).build());

        assertEquals(1, second.getItemCount());
        assertEquals(22L, second.getItems().get(0).getVariantId());
    }

    @Test
    void addItem_withAVariantOfAnotherProduct_isRejected() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L)));
        when(productRepository.findById(1101L)).thenReturn(Optional.of(product(1101L)));
        when(variantRepository.findById(21L)).thenReturn(Optional.of(variant(21L, 9999L)));

        assertThrows(InvalidRequestException.class, () -> service.addItem(1L, null,
                AddToWishlistRequestDto.builder().productId(1101L).variantId(21L).build()));
    }

    @Test
    void addItem_toAnotherUsersWishlist_isRejected() {
        Wishlist theirs = existingWishlist(2L, "Theirs");
        when(productRepository.findById(1101L)).thenReturn(Optional.of(product(1101L)));

        assertThrows(InvalidRequestException.class, () -> service.addItem(1L, theirs.getId(),
                AddToWishlistRequestDto.builder().productId(1101L).build()));
    }

    @Test
    void removeItem_fromTheDefaultList_resolvesItInsteadOfLookingUpANullId() {
        // removeItem used to hand the null "default list" marker straight to findById, which
        // threw from inside the JPA layer and surfaced as a 400 on a legitimate un-save.
        Wishlist mine = existingWishlist(1L, WishlistServiceImpl.DEFAULT_NAME);
        WishlistItem saved = WishlistItem.builder().wishlist(mine).product(product(1101L)).build();
        saved.setId(70L);
        items.add(saved);

        service.removeItem(1L, null, 1101L);

        assertEquals(0, items.size());
        verify(wishlistRepository, never()).findById(null);
    }

    @Test
    void removeItem_withNothingSaved_yetIsNotFoundInsteadOfCreatingAList() {
        assertThrows(ResourceNotFoundException.class, () -> service.removeItem(1L, null, 1101L));
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void removeItem_namedList_stillRemovesTheLine() {
        Wishlist mine = existingWishlist(1L, "Summer");
        WishlistItem saved = WishlistItem.builder().wishlist(mine).product(product(1101L)).build();
        saved.setId(71L);
        items.add(saved);

        service.removeItem(1L, mine.getId(), 1101L);

        assertEquals(0, items.size());
    }

    @Test
    void removeItem_forAProductThatIsNotSaved_isNotFound() {
        existingWishlist(1L, WishlistServiceImpl.DEFAULT_NAME);

        assertThrows(ResourceNotFoundException.class, () -> service.removeItem(1L, null, 404L));
    }

    @Test
    void create_rejectsADuplicateNameCaseInsensitively() {
        existingWishlist(1L, "Summer");

        assertThrows(DuplicateResourceException.class, () -> service.create(1L,
                CreateWishlistRequestDto.builder().name("summer").build()));
    }

    @Test
    void delete_removesItsItemsFirstBecauseTheForeignKeyRestricts() {
        Wishlist mine = existingWishlist(1L, "Summer");
        WishlistItem saved = WishlistItem.builder().wishlist(mine).product(product(1101L)).build();
        saved.setId(72L);
        items.add(saved);

        service.delete(1L, mine.getId());

        assertEquals(0, items.size());
        verify(wishlistRepository).delete(mine);
    }

    @Test
    void listMyWishlists_countsItemsPerListWithoutLoadingThem() {
        Wishlist first = existingWishlist(1L, "Summer");
        WishlistItem saved = WishlistItem.builder().wishlist(first).product(product(1101L)).build();
        saved.setId(73L);
        items.add(saved);

        assertEquals(1, service.listMyWishlists(1L).size());
        assertEquals(1, service.listMyWishlists(1L).get(0).getItemCount());
        verify(itemRepository, never()).findByWishlistIdOrderByCreatedAtDesc(anyLong());
    }
}
