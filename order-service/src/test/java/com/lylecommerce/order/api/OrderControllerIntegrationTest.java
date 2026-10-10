package com.lylecommerce.order.api;

import tools.jackson.databind.ObjectMapper;
import com.lylecommerce.order.OrderServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import com.lylecommerce.order.support.PostgresIntegrationTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class OrderControllerIntegrationTest extends PostgresIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldCreateAndRetrieveOrder() throws Exception {

        RestClient client = RestClient.create(
                "http://localhost:" + port
        );

        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        CreateOrderRequest request =
                new CreateOrderRequest(
                        customerId,
                        List.of(
                                new CreateOrderRequest.Item(
                                        productId,
                                        "Mechanical Keyboard",
                                        2,
                                        new BigDecimal("79.99")
                                )
                        )
                );

        ResponseEntity<String> createResponse =
                client.post()
                        .uri("/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .toEntity(String.class);

        assertEquals(
                HttpStatus.CREATED,
                createResponse.getStatusCode()
        );

        OrderResponse createdOrder =
                objectMapper.readValue(
                        createResponse.getBody(),
                        OrderResponse.class
                );

        assertNotNull(createdOrder.id());
        assertEquals(customerId, createdOrder.customerId());
        assertEquals(
                new BigDecimal("159.98"),
                createdOrder.total()
        );

        ResponseEntity<String> getResponse =
                client.get()
                        .uri("/orders/{id}", createdOrder.id())
                        .retrieve()
                        .toEntity(String.class);

        assertEquals(
                HttpStatus.OK,
                getResponse.getStatusCode()
        );

        OrderResponse retrievedOrder =
                objectMapper.readValue(
                        getResponse.getBody(),
                        OrderResponse.class
                );

        assertEquals(
                createdOrder.id(),
                retrievedOrder.id()
        );

        assertEquals(
                customerId,
                retrievedOrder.customerId()
        );

        assertEquals(
                new BigDecimal("159.98"),
                retrievedOrder.total()
        );


    }

    @Test
    void shouldReturnNotFoundWhenOrderDoesNotExist() {

        RestClient client = RestClient.create(
                "http://localhost:" + port
        );

        UUID missingOrderId =
                UUID.fromString(
                        "99999999-9999-9999-9999-999999999999"
                );

        HttpClientErrorException.NotFound exception =
                assertThrows(
                        HttpClientErrorException.NotFound.class,
                        () -> client.get()
                                .uri("/orders/{id}", missingOrderId)
                                .retrieve()
                                .toEntity(String.class)
                );

        assertEquals(
                HttpStatus.NOT_FOUND,
                exception.getStatusCode()
        );
    }
    @Test
    void shouldPriceOrdersFromTheBackendAndExposeHistory() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);
        String body = """
                {"customerId":"11111111-1111-1111-1111-111111111111","items":[{
                  "productId":"22222222-2222-2222-2222-222222222222",
                  "productName":"Tampered name","quantity":2,"unitPrice":0.01}]}
                """;
        String response = client.post().uri("/orders").contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class);
        OrderResponse order = objectMapper.readValue(response, OrderResponse.class);
        assertEquals(new BigDecimal("159.98"), order.total());
        assertEquals("Mechanical Keyboard", order.items().getFirst().productName());
        String details = client.get().uri("/orders/{id}/details", order.id()).retrieve().body(String.class);
        var parsed = objectMapper.readTree(details);
        assertEquals("NOT_REQUESTED", parsed.path("paymentStatus").asText());
        assertEquals("PENDING", parsed.path("transitions").get(0).path("status").asText());
        assertEquals("GBP", parsed.path("currency").asText());
        String listing = client.get().uri("/orders?customerId={id}", order.customerId()).retrieve().body(String.class);
        assertTrue(listing.contains(order.id().toString()));
    }

    @Test
    void shouldExposeACompleteCatalogueWithFeatureLists() throws Exception {
        RestClient client = RestClient.create("http://localhost:" + port);
        var products = objectMapper.readTree(client.get().uri("/products").retrieve().body(String.class));
        assertEquals(6, products.size());
        assertEquals("Mechanical Keyboard", products.get(0).path("name").asText());
        assertEquals(3, products.get(0).path("features").size());
        assertEquals("USB-C connection", products.get(0).path("features").get(1).asText());
    }

    @Test
    void shouldRejectInvalidAndUnknownProductsWithUsefulBadRequests() {
        RestClient client = RestClient.create("http://localhost:" + port);
        for (String items : List.of("null", "[]", "[null]",
                "[{\"productId\":\"22222222-2222-2222-2222-222222222222\",\"quantity\":0}]",
                "[{\"productId\":\"22222222-2222-2222-2222-222222222222\",\"quantity\":101}]",
                "[{\"productId\":\"22222222-2222-2222-2222-222222222222\",\"quantity\":1.5}]",
                "[{\"productId\":\"99999999-9999-9999-9999-999999999999\",\"quantity\":1}]")) {
            String body = "{\"customerId\":\"11111111-1111-1111-1111-111111111111\",\"items\":" + items + "}";
            var failure = assertThrows(HttpClientErrorException.BadRequest.class, () -> client.post().uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(String.class));
            assertTrue(failure.getResponseBodyAsString().contains("detail"));
        }
    }

}
