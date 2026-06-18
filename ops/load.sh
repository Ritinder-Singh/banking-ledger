#!/usr/bin/env bash
# Generates synthetic ledger traffic so the Grafana dashboard has interesting
# shape: mostly successful transfers, occasional overdrafts, occasional
# idempotent retries. Run while the app is up to populate the dashboard.
set -euo pipefail

base="${BASE:-http://localhost:8080}"
duration_seconds="${DURATION:-90}"

j() { curl -sf "$@"; }

create_account() {
  local owner=$1 currency=$2
  j -X POST "$base/accounts" -H 'Content-Type: application/json' \
    -d "{\"owner\":\"$owner\",\"currency\":\"$currency\"}" \
    | python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])'
}

alice=$(create_account alice USD)
bob=$(create_account bob USD)
carol=$(create_account carol USD)
eve=$(create_account eve EUR)

echo "alice=$alice"
echo "bob=$bob"
echo "carol=$carol"
echo "eve=$eve  (different currency, used to drive currency_mismatch failures)"

# Seed funds
j -X POST "$base/accounts/$alice/deposit" -H 'Content-Type: application/json' -d '{"amount":1000000}' > /dev/null
j -X POST "$base/accounts/$bob/deposit"   -H 'Content-Type: application/json' -d '{"amount":1000000}' > /dev/null
j -X POST "$base/accounts/$carol/deposit" -H 'Content-Type: application/json' -d '{"amount":50000}'   > /dev/null

end=$(($(date +%s) + duration_seconds))
i=0
while [ "$(date +%s)" -lt "$end" ]; do
  i=$((i+1))
  # Mostly successful round-trip transfers
  j -X POST "$base/transfers" -H 'Content-Type: application/json' \
    -d "{\"fromAccountId\":\"$alice\",\"toAccountId\":\"$bob\",\"amount\":100}" > /dev/null &
  j -X POST "$base/transfers" -H 'Content-Type: application/json' \
    -d "{\"fromAccountId\":\"$bob\",\"toAccountId\":\"$carol\",\"amount\":50}" > /dev/null &

  # Every 7th iteration: an idempotent retry
  if [ $((i % 7)) -eq 0 ]; then
    key="key-$i"
    j -X POST "$base/transfers" -H 'Content-Type: application/json' -H "Idempotency-Key: $key" \
      -d "{\"fromAccountId\":\"$alice\",\"toAccountId\":\"$bob\",\"amount\":25}" > /dev/null
    j -X POST "$base/transfers" -H 'Content-Type: application/json' -H "Idempotency-Key: $key" \
      -d "{\"fromAccountId\":\"$alice\",\"toAccountId\":\"$bob\",\"amount\":25}" > /dev/null
  fi

  # Every 11th iteration: an overdraft attempt (carol withdrawing way too much)
  if [ $((i % 11)) -eq 0 ]; then
    curl -s -X POST "$base/accounts/$carol/withdraw" -H 'Content-Type: application/json' \
      -d '{"amount":999999999}' > /dev/null || true
  fi

  # Every 13th iteration: a cross-currency transfer (USD -> EUR), should fail
  if [ $((i % 13)) -eq 0 ]; then
    curl -s -X POST "$base/transfers" -H 'Content-Type: application/json' \
      -d "{\"fromAccountId\":\"$alice\",\"toAccountId\":\"$eve\",\"amount\":10}" > /dev/null || true
  fi

  # Small jitter between iterations
  sleep 0.1
done
wait
echo "done; ran $i iterations"
