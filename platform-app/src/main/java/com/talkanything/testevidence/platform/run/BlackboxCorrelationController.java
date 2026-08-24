package com.talkanything.testevidence.platform.run;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/blackbox-correlation-rules")
class BlackboxCorrelationController {
    private final String agentToken;
    private final BlackboxCorrelationService service;

    BlackboxCorrelationController(@Value("${test-evidence.agent-token}") String agentToken,
                                  BlackboxCorrelationService service) {
        this.agentToken = agentToken;
        this.service = service;
    }

    @GetMapping(produces = MediaType.TEXT_PLAIN_VALUE)
    String active(@RequestHeader(value = "X-Test-Agent-Token", required = false) String token,
                  @RequestParam("service") String serviceName) {
        if (!agentToken.equals(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized agent");
        return service.activeProtocol(serviceName);
    }
}
