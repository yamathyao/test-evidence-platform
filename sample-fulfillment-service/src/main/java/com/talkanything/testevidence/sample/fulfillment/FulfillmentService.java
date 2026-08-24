package com.talkanything.testevidence.sample.fulfillment;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
public class FulfillmentService {
    private final FulfillmentRepository repository;

    public FulfillmentService(FulfillmentRepository repository) {
        this.repository = repository;
    }

    public FulfillmentRecord fulfill(FulfillmentRequest request) {
        FulfillmentRequest validated = validateRequest(request);
        FulfillmentRecord record = new FulfillmentRecord(validated.orderNo(), validated.sku(), validated.quantity(),
                validated.note(), "FULFILLED", Instant.now());
        return repository.fulfill(record);
    }

    public void delete(String orderNo) {
        validateOrderNumber(orderNo);
        repository.delete(orderNo);
    }

    public Optional<FulfillmentRecord> find(String orderNo) {
        validateOrderNumber(orderNo);
        return repository.find(orderNo);
    }

    private FulfillmentRequest validateRequest(FulfillmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Missing fulfillment request");
        }
        String note = request.note() == null ? "" : request.note();
        validateOrderNumber(request.orderNo());
        validateSku(request.sku());
        validateQuantity(request.quantity());
        if (note.length() > 256) {
            throw new IllegalArgumentException("Invalid note");
        }
        return new FulfillmentRequest(request.orderNo(), request.sku(), request.quantity(), note);
    }

    static void validateOrderNumber(String orderNo) {
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
