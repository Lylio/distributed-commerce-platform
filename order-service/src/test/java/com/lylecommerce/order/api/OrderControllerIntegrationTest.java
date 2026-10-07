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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = OrderServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
class OrderControllerIntegrationTest {

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
        UUID productId = UUID.randomUUID();

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
}
