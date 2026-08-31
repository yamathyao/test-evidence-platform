#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
helper_file=$(mktemp)
trap 'rm -f "$helper_file"' EXIT HUP INT TERM

sed -n '1,/^# Keep failed request URLs visible/p' "$script_dir/compose-smoke.sh" > "$helper_file"
. "$helper_file"

trace='{"roots":[{"traceId":"root","serviceName":"fulfillment","protocol":"HTTP","direction":"SERVER","target":"/internal/protocols/echo","children":[{"traceId":"jdbc","serviceName":"fulfillment","protocol":"JDBC","direction":"CLIENT","target":"mysql","children":[]}]}]}'
actual=$(http_client_evidence_count "$trace")
summary=$(http_client_evidence_summary "$trace" || true)

[ "$actual" = 0 ] || {
    printf 'Expected no HTTP CLIENT Evidence entry, got %s\n' "$actual" >&2
    exit 1
}

[ -z "$summary" ] || {
    printf 'Expected no HTTP CLIENT summary entry, got %s\n' "$summary" >&2
    exit 1
}

dubbo_trace='{"roots":[{"traceId":"root","serviceName":"order","protocol":"DUBBO","direction":"CLIENT","target":"FulfillmentProbeService#echo","children":[{"traceId":"provider","serviceName":"fulfillment","protocol":"DUBBO","direction":"SERVER","target":"FulfillmentProbeService#echo","children":[]}]}]}'
[ "$(protocol_direction_evidence_count "$dubbo_trace" DUBBO CLIENT)" = 1 ]
[ "$(protocol_direction_evidence_count "$dubbo_trace" DUBBO SERVER)" = 1 ]

fulfillment_server_trace='{"roots":[{"traceId":"root","serviceName":"sample-order-service","protocol":"HTTP","direction":"CLIENT","children":[{"traceId":"server","serviceName":"sample-fulfillment-service","protocol":"HTTP","direction":"SERVER","children":[]}]}]}'
[ "$(http_server_service_evidence_count "$fulfillment_server_trace" sample-fulfillment-service)" = 1 ]
[ "$(http_server_service_evidence_count "$dubbo_trace" sample-fulfillment-service)" = 0 ]

dubbo_payload='{"protocol":"DUBBO","requestStatus":"CAPTURED","responseStatus":"CAPTURED","requestBody":"[\"compose-1\"]","responseBody":"{\"orderNo\":\"compose-1\"}"}'
has_captured_dubbo_payload "$dubbo_payload" compose-1

if has_captured_dubbo_payload '{"protocol":"HTTP","requestStatus":"CAPTURED","responseStatus":"CAPTURED","requestBody":"compose-1"}' compose-1; then
    printf 'Expected HTTP payload not to satisfy Dubbo payload acceptance\n' >&2
    exit 1
fi

if has_captured_dubbo_payload '{"protocol":"DUBBO","requestStatus":"CAPTURED","responseStatus":"CAPTURED","requestBody":"[]","responseBody":"{\"orderNo\":\"compose-1\"}"}' compose-1; then
    printf 'Expected a Dubbo payload without the request order number to fail\n' >&2
    exit 1
fi
