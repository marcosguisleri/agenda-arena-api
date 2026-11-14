#!/usr/bin/env bash
# Funções de apoio dos scripts (smoke-test.sh e seed-demo.sh). Requer: curl e jq.
# Não é para ser executado direto: os outros scripts fazem `source scripts/lib.sh`.

API_URL="${API_URL:-http://localhost:8080}"

need() {
    command -v "$1" >/dev/null 2>&1 || { echo "Faltou o comando '$1'. Instale e tente de novo." >&2; exit 2; }
}
need curl
need jq

TMP_DIR="$(mktemp -d)"
BODY_FILE="$TMP_DIR/body.json"
HEADERS_FILE="$TMP_DIR/headers.txt"
trap 'rm -rf "$TMP_DIR"' EXIT

STATUS=""

# api METHOD CAMINHO [JSON] [TOKEN]  -> guarda o status HTTP em $STATUS e o corpo em $BODY_FILE
api() {
    local method="$1" path="$2" body="${3-}" token="${4-}"
    local args=(-s -o "$BODY_FILE" -D "$HEADERS_FILE" -w '%{http_code}' -X "$method" "${API_URL}${path}")
    [ -n "$token" ] && args+=(-H "Authorization: Bearer ${token}")
    if [ -n "$body" ]; then
        args+=(-H 'Content-Type: application/json' --data "$body")
    fi
    STATUS="$(curl "${args[@]}")" || STATUS="000"
}

# j EXPRESSÃO_JQ  -> valor do corpo da última resposta (vazio se for null)
j() {
    jq -r "$1 // empty" "$BODY_FILE" 2>/dev/null
}

# header NOME -> valor do cabeçalho da última resposta
header() {
    grep -i "^$1:" "$HEADERS_FILE" | head -1 | sed 's/^[^:]*: *//' | tr -d '\r'
}

# Login: guarda ACCESS_TOKEN e REFRESH_TOKEN; devolve 1 se falhar
login() {
    api POST /auth/login "$(jq -nc --arg e "$1" --arg p "$2" '{email:$e,password:$p}')"
    [ "$STATUS" = "200" ] || return 1
    ACCESS_TOKEN="$(j .accessToken)"
    REFRESH_TOKEN="$(j .refreshToken)"
}

# Dia da semana (em inglês, maiúsculas, como a API espera) e data daqui a N dias, no fuso da API
day_of_week_in() { LC_ALL=C TZ=America/Sao_Paulo date -d "+$1 days" +%A | tr '[:lower:]' '[:upper:]'; }
date_in() { TZ=America/Sao_Paulo date -d "$1 days" +%F; }
