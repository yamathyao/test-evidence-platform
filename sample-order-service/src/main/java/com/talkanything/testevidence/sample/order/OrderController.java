package com.talkanything.testevidence.sample.order;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrderController {
    private final FulfillmentClient fulfillmentClient;

    public OrderController(FulfillmentClient fulfillmentClient) {
        this.fulfillmentClient = fulfillmentClient;
    }

    @PostMapping("/sample/orders")
    public ResponseEntity<OrderResponse> process(@RequestBody OrderRequest request) {
        return ResponseEntity.ok(OrderResponse.from(fulfillmentClient.fulfill(validateRequest(request))));
    }

    @GetMapping("/sample/orders/{orderNo}")
    public ResponseEntity<OrderStatusResponse> find(@PathVariable String orderNo) {
        validateOrderNumber(orderNo);
        return fulfillmentClient.find(orderNo)
                .map(OrderStatusResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(org.springframework.web.client.RestClientException.class)
    public ResponseEntity<Void> fulfillmentUnavailable() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }

    private OrderRequest validateRequest(OrderRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing order request");
        }
        String note = request.note() == null ? "" : request.note();
        validateOrderNumber(request.orderNo());
        validateSku(request.sku());
        validateQuantity(request.quantity());
        if (note.length() > 256) {
            throw new IllegalArgumentException("Invalid note");
        }
        return new OrderRequest(request.orderNo(), request.sku(), request.quantity(), note);
    }

    private void validateOrderNumber(String orderNo) {
        if (orderNo == null || orderNo.isBlank() || orderNo.length() > 128) {
            throw new IllegalArgumentException("Invalid order number");
        }
    }

    private void validateSku(String sku) {
        if (sku == null || sku.isBlank() || sku.length() > 64) {
            throw new IllegalArgumentException("Invalid sku");
        }
    }

    private void validateQuantity(Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > 99) {
            throw new IllegalArgumentException("Invalid quantity");
        }
    }
}
