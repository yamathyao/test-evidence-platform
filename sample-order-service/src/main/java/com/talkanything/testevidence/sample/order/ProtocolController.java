package com.talkanything.testevidence.sample.order;

import com.talkanything.testevidence.sample.order.protocol.ProtocolClientRegistry;
import com.talkanything.testevidence.sample.order.protocol.ProtocolEchoResponse;
import com.talkanything.testevidence.sample.order.protocol.ProtocolInvocation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProtocolController {
    private final ProtocolClientRegistry registry;

    public ProtocolController(ProtocolClientRegistry registry) {
        this.registry = registry;
    }

    @PostMapping("/sample/protocols/{client}")
    public ProtocolInvocationResult invoke(@PathVariable String client, @RequestBody ProtocolInvocation request) {
        validate(request);
        ProtocolEchoResponse response = registry.require(client).invoke(request);
        return new ProtocolInvocationResult(client, response.orderNo(), response.statementValue());
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