#!/bin/sh
set -eu

platform_base_url=${PLATFORM_BASE_URL:-http://127.0.0.1:8080}
order_base_url=${ORDER_BASE_URL:-http://127.0.0.1:19120}
agent_token=${TEST_EVIDENCE_AGENT_TOKEN:-local-test-agent-token-change-before-shared-use}
timeout_seconds=${TIMEOUT_SECONDS:-120}
agent_rule_refresh_seconds=${AGENT_RULE_REFRESH_SECONDS:-6}

fail() {
    printf '\n[FAILED] %s\n' "$1" >&2
    exit 1
}

progress() {
    printf '\n==> [%s/7] %s\n' "$1" "$2"
}

# Extract fixed fields from Platform responses; this is not a general JSON parser.
json_string() {
    value=$(printf '%s' "$1" | tr -d '\r\n' | sed -n 's/.*"'$2'"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)
    [ -n "$value" ] || fail "Missing JSON field: $2"
    printf '%s' "$value"
}

# API create responses serialize their resource ID as the first JSON field.
# Do not use the generic field extractor: Run snapshots contain nested profile IDs.
json_root_id() {
    value=$(printf '%s' "$1" | tr -d '\r\n' | sed -n 's/^[[:space:]]*{"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
    [ -n "$value" ] || fail 'Missing top-level JSON id'
    printf '%s' "$value"
}
json_number() {
    value=$(printf '%s' "$1" | tr -d '\r\n' | sed -n 's/.*"'$2'"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -n 1)
    [ -n "$value" ] || fail "Missing JSON number: $2"
    printf '%s' "$value"
}

has_json_value() {
    printf '%s' "$1" | tr -d '\r\n' | grep -Eq '"'$2'"[[:space:]]*:[[:space:]]*"'$3'"'
}

protocol_count() {
    printf '%s' "$1" | tr -d '\r\n' \
        | grep -o '"protocol"[[:space:]]*:[[:space:]]*"'$2'"' \
        | wc -l | tr -d ' '
}

# Evidence JSON writes protocol before direction, so count only matching protocol records.
protocol_direction_evidence_count() {
    printf '%s' "$1" | tr -d '\r\n' \
        | grep -o '"protocol"[[:space:]]*:[[:space:]]*"'$2'"[^{}]*"direction"[[:space:]]*:[[:space:]]*"'$3'"' \
        | wc -l | tr -d ' '
}

http_client_evidence_count() {
    protocol_direction_evidence_count "$1" HTTP CLIENT
}

http_server_service_evidence_count() {
    printf '%s' "$1" | tr -d '\r\n' \
        | grep -o '"serviceName"[[:space:]]*:[[:space:]]*"'$2'"[^{}]*"protocol"[[:space:]]*:[[:space:]]*"HTTP"[^{}]*"direction"[[:space:]]*:[[:space:]]*"SERVER"' \
        | wc -l | tr -d ' '
}

# Keep the missing client protocol visible without requiring a general JSON parser.
http_client_evidence_summary() {
    printf '%s' "$1" | tr -d '\r\n' \
        | sed 's/{"traceId"/\n{"traceId"/g' \
        | grep '"protocol"[[:space:]]*:[[:space:]]*"HTTP"' \
        | grep '"direction"[[:space:]]*:[[:space:]]*"CLIENT"' \
        | sed -n 's/.*"serviceName":"\([^"]*\)".*"target":"\([^"]*\)".*/  \1 -> \2/p'
}

empty_jdbc_parameter_count() {
    printf '%s' "$1" | tr -d '\r\n' \
        | grep -o '"jdbcParameters"[[:space:]]*:[[:space:]]*\[\]' \
        | wc -l | tr -d ' '
}

has_captured_dubbo_payload() {
    payload=$1
    expected_order_no=$2
    request_marker='"requestBody"'
    response_marker='"responseBody"'
    request_segment=${payload#*"$request_marker"}
    request_segment=${request_segment%%"$response_marker"*}
    response_segment=${payload#*"$response_marker"}
    has_json_value "$payload" protocol DUBBO \
        && has_json_value "$payload" requestStatus CAPTURED \
        && has_json_value "$payload" responseStatus CAPTURED \
        && printf '%s' "$request_segment" | grep -Fq "$expected_order_no" \
        && printf '%s' "$response_segment" | grep -Fq "$expected_order_no"
}

# Keep failed request URLs visible in external test logs.
get_json() {
    url=$1
    if ! response=$(curl --fail --silent --show-error --max-time 10 "$url"); then
        printf 'GET request failed: %s\n' "$url" >&2
        return 1
    fi
    printf '%s' "$response"
}

post_json() {
    url=$1
    body=$2
    if ! response=$(curl --fail --silent --show-error --max-time 20 -X POST \
        -H 'Content-Type: application/json' --data "$body" "$url"); then
        printf 'POST request failed: %s\n' "$url" >&2
        [ -z "$response" ] || printf 'POST response: %s\n' "$response" >&2
        return 1
    fi
    printf '%s' "$response"
}

# Wait for services and Agent rule distribution instead of sleeping a fixed time.
wait_health() {
    deadline=$(( $(date +%s) + timeout_seconds ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if response=$(get_json "$1/actuator/health" 2>/dev/null) \
            && has_json_value "$response" status UP; then
            return
        fi
        sleep 2
    done
    fail "Health check timed out: $1"
}

wait_rule() {
    deadline=$(( $(date +%s) + timeout_seconds ))
    while [ "$(date +%s)" -lt "$deadline" ]; do
        if response=$(curl --fail --silent --show-error --max-time 5 \
            -H "X-Test-Agent-Token: $agent_token" \
            "$platform_base_url/internal/v1/blackbox-correlation-rules?service=sample-order-service" \
            2>/dev/null) && [ -n "$response" ]; then
            return
        fi
        sleep 2
    done
    fail 'Black-box correlation rule was not distributed to sample-order-service'
}

case "$timeout_seconds" in
    ''|*[!0-9]*) fail 'TIMEOUT_SECONDS must be a positive integer' ;;
esac
[ "$timeout_seconds" -ge 30 ] || fail 'TIMEOUT_SECONDS must be at least 30'
case "$agent_rule_refresh_seconds" in
    ''|*[!0-9]*) fail 'AGENT_RULE_REFRESH_SECONDS must be a positive integer' ;;
esac
[ "$agent_rule_refresh_seconds" -ge 5 ] || fail 'AGENT_RULE_REFRESH_SECONDS must be at least 5'
command -v curl >/dev/null 2>&1 || fail 'Required command is not available: curl'

printf '\n=== Docker Compose smoke: Browser -> HTTP -> Dubbo -> JDBC evidence ===\n'
printf 'Platform: %s\nOrder:    %s\n' "$platform_base_url" "$order_base_url"
progress 1 'Waiting for Platform and order health checks'
wait_health "$platform_base_url"
wait_health "$order_base_url"

progress 2 'Creating capture profile and Browser test case'
order_no="compose-$(date +%s)-$$"
name="compose smoke $order_no"
profile_body=$(printf '{"name":"%s","version":1,"definition":{"blackboxCorrelation":{"defaultTtlSeconds":90,"retentionSeconds":300,"singleUse":true,"targetServices":["sample-order-service"],"matchGroups":[{"name":"order","matchers":[{"field":"orderNo","locations":["JSON_BODY"],"match":"EXACT"}]}]}}}' "$name")
profile=$(post_json "$platform_base_url/api/capture-profiles" "$profile_body")
profile_id=$(json_root_id "$profile")

test_case_body=$(printf '{"name":"%s","profileId":"%s","triggerType":"BROWSER","triggerConfig":{},"timeoutSeconds":90,"httpPayloadCaptureEnabled":true,"assertions":[]}' "$name" "$profile_id")
test_case=$(post_json "$platform_base_url/api/test-cases" "$test_case_body")
test_case_id=$(json_root_id "$test_case")

progress 3 'Starting Browser black-box run'
run_body=$(printf '{"correlationData":{"orderNo":"%s"},"ttlSeconds":90}' "$order_no")
run=$(post_json "$platform_base_url/api/test-cases/$test_case_id/blackbox-runs" "$run_body")
run_id=$(json_root_id "$run")
wait_rule
# The Platform has published the rule. Allow the sample Agent's 5-second refresh loop to receive it.
printf 'Rule published. Waiting %s seconds for the order Agent to refresh it.\n' "$agent_rule_refresh_seconds"
sleep "$agent_rule_refresh_seconds"

progress 4 'Calling the sample order service'
order_body=$(printf '{"orderNo":"%s","sku":"SKU-COMPOSE","quantity":1,"note":"compose smoke"}' "$order_no")
order=$(post_json "$order_base_url/sample/orders" "$order_body")
has_json_value "$order" status FULFILLED || fail "Unexpected fulfillment response: $order"

progress 5 'Calling the Dubbo demo and protocol client matrix'
dubbo=$(post_json "$order_base_url/sample/dubbo" "{\"orderNo\":\"$order_no\"}")
has_json_value "$dubbo" protocol DUBBO || fail "Unexpected Dubbo response: $dubbo"
has_json_value "$dubbo" orderNo "$order_no" || fail "Dubbo response lost orderNo: $dubbo"
for client in apache4 apache5 okhttp feign webclient; do
    protocol=$(post_json "$order_base_url/sample/protocols/$client" \
        "{\"orderNo\":\"$order_no\"}")
    has_json_value "$protocol" client "$client" || fail "Unexpected $client protocol response: $protocol"
done

progress 6 'Completing the run and waiting for evidence to settle'
post_json "$platform_base_url/api/runs/$run_id/complete" '{}' >/dev/null
deadline=$(( $(date +%s) + timeout_seconds ))
while :; do
    sleep 2
    result=$(get_json "$platform_base_url/api/runs/$run_id")
    status=$(json_string "$result" status)
    [ "$status" != RUNNING ] && [ "$status" != DRAINING ] && break
    [ "$(date +%s)" -lt "$deadline" ] || fail "Run $run_id did not settle within $timeout_seconds seconds"
done
[ "$status" = SUCCEEDED ] || fail "Run $run_id ended as $status: $result"
[ "$(json_number "$result" rootTraceCount)" -ge 1 ] || fail "Run $run_id has no root traces: $result"

progress 7 'Checking Trace, HTTP payloads, Dubbo, and JDBC evidence'
trace=$(get_json "$platform_base_url/api/runs/$run_id/trace")
http_count=$(protocol_count "$trace" HTTP)
client_count=$(http_client_evidence_count "$trace")
dubbo_client_count=$(protocol_direction_evidence_count "$trace" DUBBO CLIENT)
dubbo_server_count=$(protocol_direction_evidence_count "$trace" DUBBO SERVER)
fulfillment_server_count=$(http_server_service_evidence_count "$trace" sample-fulfillment-service)
jdbc_count=$(protocol_count "$trace" JDBC)
statement_count=$(empty_jdbc_parameter_count "$trace")
printf 'HTTP CLIENT evidence: %s (expected at least 5)\n' "$client_count"
printf 'HTTP CLIENT service -> target:\n'
http_client_evidence_summary "$trace" || true
[ "$http_count" -ge 2 ] || fail "Trace has insufficient HTTP evidence: $http_count"
[ "$client_count" -ge 5 ] || fail "Trace has insufficient HTTP CLIENT evidence: $client_count"
printf 'Fulfillment HTTP SERVER evidence: %s (expected at least 1)\n' "$fulfillment_server_count"
[ "$fulfillment_server_count" -ge 1 ] || fail 'Trace has no sample-fulfillment-service HTTP SERVER evidence'
printf 'DUBBO evidence: client=%s server=%s (expected at least 1 each)\n' "$dubbo_client_count" "$dubbo_server_count"
[ "$dubbo_client_count" -ge 1 ] || fail "Trace has insufficient DUBBO CLIENT evidence: $dubbo_client_count"
[ "$dubbo_server_count" -ge 1 ] || fail "Trace has insufficient DUBBO SERVER evidence: $dubbo_server_count"
[ "$jdbc_count" -ge 5 ] || fail "Trace has insufficient JDBC evidence: $jdbc_count"
[ "$statement_count" -ge 5 ] || fail "Trace has insufficient JDBC Statement evidence: $statement_count"

payload_found=false
for span_id in $(printf '%s' "$trace" | tr -d '\r\n' | grep -o '"spanId"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"spanId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/'); do
    if payload=$(get_json "$platform_base_url/api/runs/$run_id/trace/http-payloads/$span_id" 2>/dev/null) \
        && has_json_value "$payload" requestStatus CAPTURED \
        && has_json_value "$payload" responseStatus CAPTURED; then
        payload_found=true
        break
    fi
done
[ "$payload_found" = true ] || fail 'No captured HTTP payload was returned by the Trace API'

dubbo_payload_found=false
for span_id in $(printf '%s' "$trace" | tr -d '\r\n' | grep -o '"spanId"[[:space:]]*:[[:space:]]*"[^"]*"' | sed 's/.*"spanId"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/'); do
    if payload=$(get_json "$platform_base_url/api/runs/$run_id/trace/payloads/$span_id" 2>/dev/null) \
        && has_captured_dubbo_payload "$payload" "$order_no"; then
        dubbo_payload_found=true
        break
    fi
done
[ "$dubbo_payload_found" = true ] || fail 'No captured Dubbo arguments and return value were returned by the Trace API'
printf '\n[PASSED] Compose smoke completed. Run ID: %s\n' "$run_id"
