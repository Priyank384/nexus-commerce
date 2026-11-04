package com.example.nexusCommerce.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.nexusCommerce.dtos.ProductSearchRequestDto;
import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;
import com.example.nexusCommerce.security.JwtService;
import com.example.nexusCommerce.security.OpaqueToken;
import com.example.nexusCommerce.security.ShopperIdentity;
import com.example.nexusCommerce.security.TokenBlacklistService;
import com.example.nexusCommerce.services.AdminService;
import com.example.nexusCommerce.services.OrderService;
import com.example.nexusCommerce.services.ProductService;
import com.example.nexusCommerce.services.ReviewService;

/**
 * The authorization rules live in the filter chain, not in the controllers, so a unit test that
 * calls a controller method directly proves nothing about them: it cannot see a missing
 * hasRole("ADMIN"), and it cannot see a rule matched by the wrong HTTP method. These tests send
 * real requests with real signed tokens through the real chain.
 *
 * <p>The services are mocked rather than exercised. The only question here is whether a request
 * reaches the controller at all, and a mock keeps that answer independent of what is in the
 * database while also proving that a denial short-circuits before any service runs.
 */
@SpringBootTest(properties = {
        "spring.cache.type=simple",
        // The exposure list lives in the main application.yml, which the test application.yml
        // replaces on the classpath. Left out, /actuator/metrics is not mapped at all, and an
        // unmapped endpoint answers 404 to everyone -- an ADMIN included -- which reads as a
        // broken rule rather than as missing configuration.
        "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
        // The health *semantics* are not what this class is about; whether /actuator/health is
        // reachable without a credential is. Redis has no server here, so its contributor would
        // report DOWN and the aggregate would answer 503, which looks like a broken gate.
        "management.health.redis.enabled=false"
})
@AutoConfigureMockMvc
class HttpAuthorizationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private ProductService productService;

    @MockitoBean
    private OrderService orderService;

    @MockitoBean
    private ReviewService reviewService;

    // Revocation is Redis-backed and has its own coverage. Stubbing it keeps this class off the
    // network; the mock answers "not revoked" for every jti, which is what an unexpired token is.
    @MockitoBean
    private TokenBlacklistService blacklist;

    private String adminToken;
    private String shopperToken;

    /**
     * Signed by the real {@link JwtService} with the test secret, so the filter parses it exactly
     * as it parses one issued by POST /auth/login. Only the subject, uid and role claims are read
     * back, so no users row has to exist for either caller.
     */
    @BeforeEach
    void mintTokens() {
        adminToken = bearer(Role.ADMIN, 1L);
        shopperToken = bearer(Role.USER, 7L);
    }

    private String bearer(Role role, long id) {
        User user = User.builder()
                .email(role.name().toLowerCase() + "@example.test")
                .name(role.name().toLowerCase())
                .role(role)
                .build();
        user.setId(id);
        return "Bearer " + jwtService.generateToken(user);
    }

    /** Reads that belong to the back office, across three different surfaces. */
    private static final List<String> ADMIN_ONLY_READS = List.of(
            "/api/v1/admin/metrics",
            "/api/v1/admin/products",
            "/api/v1/admin/products/1",
            "/api/v1/admin/users",
            "/api/v1/admin/users/7",
            "/api/v1/orders",
            "/actuator/metrics");

    @Test
    void anonymousCallerIsRefusedEveryAdminRead() throws Exception {
        for (String path : ADMIN_ONLY_READS) {
            mvc.perform(get(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("Unauthorized!"));
        }
    }

    @Test
    void signedInShopperIsForbiddenFromEveryAdminRead() throws Exception {
        for (String path : ADMIN_ONLY_READS) {
            mvc.perform(get(path).header("Authorization", shopperToken))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.message").value("Forbidden!"));
        }
    }

    @Test
    void adminCallerReachesEveryAdminRead() throws Exception {
        mvc.perform(get("/api/v1/admin/metrics").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/products").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/products/1").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/admin/users/7").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders").header("Authorization", adminToken))
                .andExpect(status().isOk());
        // The actuator gate is the same rule on a surface with no controller to inspect, which is
        // why it is the easiest one to lose while editing the chain.
        mvc.perform(get("/actuator/metrics").header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void catalogueWritesAreAdminOnly() throws Exception {
        mvc.perform(delete("/api/v1/products/1")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/products/1").header("Authorization", shopperToken))
                .andExpect(status().isForbidden());

        mvc.perform(delete("/api/v1/categories/3")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/categories/3").header("Authorization", shopperToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewModerationIsAdminOnly() throws Exception {
        mvc.perform(delete("/api/v1/reviews/3")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/reviews/3").header("Authorization", shopperToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void denialStopsBeforeAnyServiceRuns() throws Exception {
        mvc.perform(get("/api/v1/admin/metrics").header("Authorization", shopperToken))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/reviews/3").header("Authorization", shopperToken))
                .andExpect(status().isForbidden());

        verify(adminService, never()).getMetrics();
        verify(reviewService, never()).deleteReview(3L);
    }

    @Test
    void browsingTheCatalogueNeedsNoTokenAtAll() throws Exception {
        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/search").param("keyword", "tee"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/facets/sizes")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/products/facets/colors")).andExpect(status().isOk());
        // The OpenAPI document stays open: the storefront's codegen pulls it without credentials.
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());

        verify(productService).search(any(ProductSearchRequestDto.class));
    }

    @Test
    void livenessProbeStaysOpenButRuntimeDetailDoesNot() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        // info is runtime detail like metrics, not a probe, so it is under the same rule.
        mvc.perform(get("/actuator/info")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("Authorization", shopperToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void aTokenThatDoesNotVerifyIsTreatedAsAnonymous() throws Exception {
        // Neither a 500 nor a grant: the filter leaves the context empty, so authorization answers
        // a forged or expired token the same way it answers no credentials at all.
        for (String path : ADMIN_ONLY_READS) {
            mvc.perform(get(path).header("Authorization", "Bearer not.a.real.jwt"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void guestCartReadBypassesTheChainButStillNeedsACredential() throws Exception {
        // /cart is permitAll so that a browser session without an account can reach it. The chain
        // therefore cannot decide this one: the service refuses a caller carrying neither a bearer
        // token nor a session header, and a session header alone is enough to read the cart.
        mvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials!"));

        // A token in the shape the store mints: the header is validated rather than trusted,
        // because it becomes part of a Redis key and of a database lookup.
        mvc.perform(get("/api/v1/cart").header(ShopperIdentity.HEADER, OpaqueToken.mint()))
                .andExpect(status().isOk());
    }
}
