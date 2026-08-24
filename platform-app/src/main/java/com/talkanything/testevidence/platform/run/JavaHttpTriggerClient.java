package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
class JavaHttpTriggerClient implements HttpTriggerClient {
    private static final int MAX_RESPONSE_BODY_BYTES = 1_048_576;
    private final HttpClient client = HttpClient.newHttpClient();

    @Override
    public HttpTriggerResponse execute(JsonNode config, TriggerContext context) throws IOException, InterruptedException {
        String method = config.path("method").asText("GET");
        String url = config.path("url").asText();
        if (url.isBlank()) throw new IOException("Trigger URL is required");
        String body = config.path("body").asText("");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(context.timeoutSeconds()))
                .header("X-Test-Run-Id", context.runId().toString())
                .header("X-Test-Case-Id", context.caseId().toString())
                .header("X-Test-Profile-Id", context.profileId().toString())
                .header("X-Test-Profile-Version", Integer.toString(context.profileVersion()));
        if (context.payloadCaptureEnabled()) request.header("X-Test-Capture-Http-Payload", "true");
        Iterator<Map.Entry<String, JsonNode>> headers = config.path("headers").fields();
        while (headers.hasNext()) { Map.Entry<String, JsonNode> header = headers.next(); request.header(header.getKey(), header.getValue().asText()); }
        request.method(method, body.isEmpty() ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<InputStream> response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream responseBody = response.body()) {
            return new HttpTriggerResponse(response.statusCode(),
                    new String(responseBody.readNBytes(MAX_RESPONSE_BODY_BYTES), StandardCharsets.UTF_8));
        }
    }
}
