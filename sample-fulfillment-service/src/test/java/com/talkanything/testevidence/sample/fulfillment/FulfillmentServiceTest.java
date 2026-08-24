package com.talkanything.testevidence.sample.fulfillment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class FulfillmentServiceTest {
    @Test
    void rejectsBlankOrderNumber() {
        FulfillmentRepository repository = org.mockito.Mockito.mock(FulfillmentRepository.class);
        FulfillmentService service = new FulfillmentService(repository);

        assertThrows(IllegalArgumentException.class,
                () -> service.fulfill(new FulfillmentRequest("  ", "SKU-COFFEE", 1, "")));
    }

    @Test
    void rejectsOrderNumberLongerThan128Characters() {
        FulfillmentRepository repository = org.mockito.Mockito.mock(FulfillmentRepository.class);
        FulfillmentService service = new FulfillmentService(repository);

        assertThrows(IllegalArgumentException.class,
                () -> service.fulfill(new FulfillmentRequest("x".repeat(129), "SKU-COFFEE", 1, "")));
    }
}
