package com.talkanything.testevidence.sample.fulfillment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FulfillmentController {
    private final FulfillmentService fulfillmentService;
    private final ProtocolProbeService protocolProbeService;

    public FulfillmentController(FulfillmentService fulfillmentService, ProtocolProbeService protocolProbeService) {
        this.fulfillmentService = fulfillmentService;
        this.protocolProbeService = protocolProbeService;
    }

    @PostMapping("/internal/fulfillments")
    public FulfillmentResponse fulfill(@RequestBody FulfillmentRequest request) {
        return FulfillmentResponse.from(fulfillmentService.fulfill(request));
    }

    @GetMapping("/internal/fulfillments/{orderNo}")
    public ResponseEntity<FulfillmentResponse> find(@PathVariable String orderNo) {
        return fulfillmentService.find(orderNo).map(FulfillmentResponse::from)
                .map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/internal/protocols/echo")
    public ProtocolEchoResponse protocolEcho(@RequestParam String orderNo) {
        return protocolProbeService.echo(orderNo);
    }

    @DeleteMapping("/internal/fulfillments/{orderNo}")
    public ResponseEntity<Void> delete(@PathVariable String orderNo) {
        fulfillmentService.delete(orderNo);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}