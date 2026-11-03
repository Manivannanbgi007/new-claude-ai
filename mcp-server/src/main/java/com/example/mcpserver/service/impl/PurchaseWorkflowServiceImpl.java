package com.example.mcpserver.service.impl;

import com.example.mcpserver.client.OrderApiClient;
import com.example.mcpserver.client.ProductApiClient;
import com.example.mcpserver.client.StockApiClient;
import com.example.mcpserver.config.PurchaseProperties;
import com.example.mcpserver.dto.api.ApiAvailability;
import com.example.mcpserver.dto.api.ApiCreateOrderRequest;
import com.example.mcpserver.dto.api.ApiOrder;
import com.example.mcpserver.dto.api.ApiPage;
import com.example.mcpserver.dto.api.ApiProduct;
import com.example.mcpserver.dto.tool.PurchaseResult;
import com.example.mcpserver.dto.tool.StockCheckResult;
import com.example.mcpserver.exception.InvalidToolArgumentException;
import com.example.mcpserver.exception.ProductNotFoundException;
import com.example.mcpserver.mapper.OrderToolMapper;
import com.example.mcpserver.service.PurchaseWorkflowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Orchestrates EXISTING APIs through the gateway - no business logic is duplicated:
 *
 *   0. Identify       product-service  GET  /api/v1/products?q=<name>   (unless a valid id is given)
 *   1. Get Product    product-service  GET  /api/v1/products/{id}
 *   2. Check Stock    stock-service    GET  /api/v1/stock/{sku}/availability
 *   3. Create Order   order-service    POST /api/v1/orders
 *   4. Wait briefly   order-service    GET  /api/v1/orders/{id}   (saga answer)
 *
 * The steps are fixed Java code, so the LLM cannot skip the stock check or order
 * a product that does not exist. If step 0, 1 or 2 fails, NO order is created.
 *
 * Why step 0? Chat memory keeps the user's messages and the bot's answers, but NOT
 * earlier tool results - so in a later turn ("yes, buy it") the LLM knows the product
 * NAME but no longer its id. The tool therefore identifies the product by name itself.
 *
 * Step 2 is a fast pre-check with a clear answer for the user. The binding stock
 * reservation still happens in the order saga (step 3/4), so even if another
 * customer takes the last unit in between, the order is REJECTED - never oversold.
 */
@Service
public class PurchaseWorkflowServiceImpl implements PurchaseWorkflowService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseWorkflowServiceImpl.class);

    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}$");
    private static final int MAX_CANDIDATES = 5;

    /** Outcome of step 0: exactly one product, or a reason why not. */
    private record Identification(ApiProduct product, String failureReason, List<String> candidates) {

        static Identification found(ApiProduct product) {
            return new Identification(product, null, List.of());
        }

        static Identification failed(String reason, List<String> candidates) {
            return new Identification(null, reason, candidates);
        }
    }

    private final ProductApiClient productApiClient;
    private final StockApiClient stockApiClient;
    private final OrderApiClient orderApiClient;
    private final OrderToolMapper mapper;
    private final PurchaseProperties properties;

    public PurchaseWorkflowServiceImpl(ProductApiClient productApiClient, StockApiClient stockApiClient,
                                       OrderApiClient orderApiClient, OrderToolMapper mapper,
                                       PurchaseProperties properties) {
        this.productApiClient = productApiClient;
        this.stockApiClient = stockApiClient;
        this.orderApiClient = orderApiClient;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public PurchaseResult purchase(String productName, String productId, Integer quantity) {
        if (!StringUtils.hasText(productName) && !StringUtils.hasText(productId)) {
            throw new InvalidToolArgumentException("productName is required: the product the customer wants to buy.");
        }
        int requested = ToolArguments.quantity(quantity, properties.maxQuantity());

        // ---- Step 0 + 1: Identify and Get Product ----
        Identification identification = identify(productName, productId);
        if (identification.product() == null) {
            return switch (identification.failureReason()) {
                case "AMBIGUOUS_PRODUCT" -> notPurchased("AMBIGUOUS_PRODUCT", null, null, requested, null,
                        identification.candidates(),
                        "Several products match '" + productName + "'. No order was created. "
                                + "Ask the customer which one they want: " + identification.candidates());
                default -> notPurchased("PRODUCT_NOT_FOUND", null, null, requested, null, List.of(),
                        "No product matching '" + productName + "' exists. No order was created.");
            };
        }
        ApiProduct product = identification.product();

        // ---- Step 2: Check Stock ----
        Optional<ApiAvailability> availability = stockApiClient.checkAvailability(product.sku(), requested);
        if (availability.isEmpty()) {
            log.info("Purchase stopped: {} has no stock record", product.sku());
            return notPurchased("NOT_STOCKED", product.name(), product.sku(), requested,
                    mapper.notStocked(product.sku(), requested), List.of(),
                    product.name() + " is not stocked. No order was created.");
        }
        StockCheckResult stock = mapper.toStockCheck(availability.get());
        if (!availability.get().available()) {
            log.info("Purchase stopped: {} insufficient stock ({} of {})", product.sku(),
                    stock.availableQuantity(), requested);
            return notPurchased("INSUFFICIENT_STOCK", product.name(), product.sku(), requested, stock, List.of(),
                    "Not enough stock for " + product.name() + ": " + stock.message() + " No order was created.");
        }

        // ---- Step 3: Create Order ----
        ApiCreateOrderRequest request = new ApiCreateOrderRequest(properties.customerId(),
                List.of(new ApiCreateOrderRequest.Item(product.sku(), requested)));
        ApiOrder order = orderApiClient.create(request, idempotencyKey(product.id(), requested));
        log.info("Purchase: order {} created for {} x {}", order.id(), requested, product.sku());

        // ---- Step 4: wait briefly for the saga's answer ----
        ApiOrder result = awaitSagaResult(order);
        return switch (result.status()) {
            case "CONFIRMED" -> new PurchaseResult("ORDER_CONFIRMED", null, product.name(), product.sku(), requested,
                    stock, mapper.toDetails(result), List.of(),
                    "Order " + result.id() + " is confirmed. Total " + result.totalAmount() + " " + result.currency() + ".");
            case "REJECTED" -> new PurchaseResult("ORDER_REJECTED", null, product.name(), product.sku(), requested,
                    stock, mapper.toDetails(result), List.of(),
                    "Order " + result.id() + " was rejected: " + result.failureReason());
            default -> new PurchaseResult("ORDER_PENDING", null, product.name(), product.sku(), requested,
                    stock, mapper.toDetails(result), List.of(),
                    "Order " + result.id() + " was placed and is still being processed. "
                            + "Call getOrderDetails with this orderId in a moment to see the result.");
        };
    }

    /** Order confirmation is asynchronous (Kafka saga): poll for a few seconds. */
    private ApiOrder awaitSagaResult(ApiOrder order) {
        ApiOrder current = order;
        Instant deadline = Instant.now().plus(properties.confirmationWait());
        while ("PENDING".equals(current.status()) && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(properties.pollInterval().toMillis());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return current;
            }
            current = orderApiClient.getById(order.id());
        }
        return current;
    }

    /**
     * If the LLM calls the tool twice for the same request (it happens), the second call
     * must not create a second order. order-service returns the existing order for a
     * repeated Idempotency-Key; the key covers customer + product + quantity + current minute.
     */
    private String idempotencyKey(String productId, int quantity) {
        String source = properties.customerId() + "|" + productId + "|" + quantity + "|"
                + (Instant.now().getEpochSecond() / 60);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            return "mcp-" + HexFormat.of().formatHex(hash).substring(0, 40);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    /**
     * Step 0: find exactly ONE product - and never guess.
     *  1. A valid UUID id (from a search in the same reply) that exists -> that product.
     *  2. Otherwise search by name through product-service. The search is FUZZY (it also
     *     returns loosely related products), so a result only counts as a match if EVERY
     *     word the customer used appears in the product name:
     *       - name equals the requested name  -> that product
     *       - exactly one matching product    -> that product
     *       - several matching products       -> AMBIGUOUS_PRODUCT (the customer must choose)
     *       - no matching product             -> PRODUCT_NOT_FOUND
     *     Example: "Nokia 3310 Ultra" must NOT match "Bose QuietComfort Ultra Headphones".
     */
    private Identification identify(String productName, String productId) {
        if (StringUtils.hasText(productId) && UUID_PATTERN.matcher(productId.trim()).matches()) {
            try {
                return Identification.found(productApiClient.getById(productId.trim()));
            } catch (ProductNotFoundException ex) {
                log.info("Purchase: id {} not found, identifying by name '{}'", productId, productName);
            }
        } else if (StringUtils.hasText(productId)) {
            log.info("Purchase: '{}' is not a product id, identifying by name '{}'", productId, productName);
        }
        if (!StringUtils.hasText(productName)) {
            return Identification.failed("PRODUCT_NOT_FOUND", List.of());
        }

        String wanted = normalize(productName);
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("q", productName.trim());
        query.put("size", 10);
        ApiPage<ApiProduct> page = productApiClient.search(query);
        List<ApiProduct> results = page.content() == null ? List.of() : page.content();

        Optional<ApiProduct> exact = results.stream().filter(p -> normalize(p.name()).equals(wanted)).findFirst();
        if (exact.isPresent()) {
            return Identification.found(exact.get());
        }
        List<ApiProduct> matching = results.stream().filter(p -> containsAllWords(p.name(), wanted)).toList();
        if (matching.size() == 1) {
            return Identification.found(matching.getFirst());
        }
        if (matching.isEmpty()) {
            return Identification.failed("PRODUCT_NOT_FOUND", List.of());
        }
        return Identification.failed("AMBIGUOUS_PRODUCT",
                matching.stream().limit(MAX_CANDIDATES).map(ApiProduct::name).toList());
    }

    /** True if every word of "wanted" is a word of the product name ("macbook air" in "apple macbook air 13 m3"). */
    private static boolean containsAllWords(String productName, String wanted) {
        Set<String> nameWords = Set.of(normalize(productName).split(" "));
        return Arrays.stream(wanted.split(" ")).allMatch(nameWords::contains);
    }

    /** "  Apple  MacBook-Air 13 " -> "apple macbook air 13" (case, spaces, hyphens ignored) */
    private static String normalize(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replace('-', ' ').replaceAll("\\s+", " ").trim();
    }

    private static PurchaseResult notPurchased(String reason, String productName, String sku, int quantity,
                                               StockCheckResult stock, List<String> candidates, String message) {
        return new PurchaseResult("NOT_PURCHASED", reason, productName, sku, quantity, stock, null, candidates, message);
    }
}
