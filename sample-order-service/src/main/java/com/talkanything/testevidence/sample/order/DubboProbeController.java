package com.talkanything.testevidence.sample.order;

import com.talkanything.testevidence.sample.dubbo.FulfillmentProbeResponse;
import com.talkanything.testevidence.sample.order.protocol.ProtocolInvocation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DubboProbeController {
    private final DubboFulfillmentProbeClient client;

    public DubboProbeController(DubboFulfillmentProbeClient client) {
        this.client = client;
    }

    @PostMapping("/sample/dubbo")
    public DubboInvocationResult invoke(@RequestBody ProtocolInvocation request) {
        validate(request);
        FulfillmentProbeResponse response = client.echo(request.orderNo());
        return new DubboInvocationResult("DUBBO", response.orderNo(), response.statementValue());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Void> downstreamFailure() {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).build();
    }

    private void validate(ProtocolInvocation request) {
        if (request == null || request.orderNo() == null || request.orderNo().isBlank() || request.orderNo().length() > 128) {
            throw new IllegalArgumentException("Invalid order number");
        }
    }
}
