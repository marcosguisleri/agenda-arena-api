#!/usr/bin/env bash
# Cria dados de demonstração para desenvolver o front: um estabelecimento com admin, chave Pix, dois campos,
# horários de segunda a domingo e um organizador. É idempotente: pode rodar várias vezes (só cria o que falta).
#
# Uso:
#   PLATFORM_ADMIN_EMAIL=admin@example.com PLATFORM_ADMIN_PASSWORD=... ./scripts/seed-demo.sh
#
# Variáveis: API_URL (padrão http://localhost:8080), DEMO_PASSWORD (padrão demo-123456).
# Só use em desenvolvimento: as senhas dos usuários de demonstração são conhecidas.
set -uo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib.sh
source scripts/lib.sh

: "${PLATFORM_ADMIN_EMAIL:?Informe PLATFORM_ADMIN_EMAIL}"
: "${PLATFORM_ADMIN_PASSWORD:?Informe PLATFORM_ADMIN_PASSWORD}"
DEMO_PASSWORD="${DEMO_PASSWORD:-demo-123456}"
ESTABLISHMENT_NAME="Arena Demo"
ADMIN_EMAIL="admin.demo@agendaarena.local"
ORGANIZER_EMAIL="organizador.demo@agendaarena.local"

fail() { echo "ERRO: $*" >&2; echo "      resposta ($STATUS): $(head -c 300 "$BODY_FILE" 2>/dev/null)" >&2; exit 1; }
created() { echo "  criado   $*"; }
exists() { echo "  já existe $*"; }

login "$PLATFORM_ADMIN_EMAIL" "$PLATFORM_ADMIN_PASSWORD" || fail "login do admin da plataforma"
PLATFORM_TOKEN="$ACCESS_TOKEN"

echo "Estabelecimento"
api GET /establishments
EST="$(jq -r --arg n "$ESTABLISHMENT_NAME" '[.[] | select(.name == $n)][0].id // empty' "$BODY_FILE")"
if [ -z "$EST" ]; then
    api POST /establishments "$(jq -nc --arg n "$ESTABLISHMENT_NAME" '{name:$n}')" "$PLATFORM_TOKEN"
    [ "$STATUS" = "201" ] || fail "criar estabelecimento"
    EST="$(j .id)"
    created "$ESTABLISHMENT_NAME (id $EST)"
else
    exists "$ESTABLISHMENT_NAME (id $EST)"
fi

echo "Administrador do estabelecimento"
if login "$ADMIN_EMAIL" "$DEMO_PASSWORD"; then
    exists "$ADMIN_EMAIL"
else
    api POST "/establishments/$EST/admins" \
        "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$DEMO_PASSWORD" '{name:"Admin Demo",email:$e,phone:"19999990001",password:$p}')" "$PLATFORM_TOKEN"
    [ "$STATUS" = "201" ] || fail "criar admin (se o e-mail já existe com outra senha, ajuste DEMO_PASSWORD)"
    created "$ADMIN_EMAIL"
    login "$ADMIN_EMAIL" "$DEMO_PASSWORD" || fail "login do admin criado"
fi
ADMIN_TOKEN="$ACCESS_TOKEN"

echo "Chave Pix do estabelecimento"
api GET "/establishments/$EST/pix-key" "" "$ADMIN_TOKEN"
if [ "$STATUS" = "200" ]; then
    exists "chave $(j .keyType)"
else
    api PUT "/establishments/$EST/pix-key" \
        '{"keyType":"CNPJ","key":"11.222.333/0001-81","holderName":"Arena Demo Esportes Ltda","bankName":"Banco do Brasil"}' "$ADMIN_TOKEN"
    [ "$STATUS" = "200" ] || fail "configurar Pix"
    created "chave CNPJ (de demonstração)"
fi

echo "Campos e horários"
for FIELD_NAME in "Campo 1" "Campo 2"; do
    api GET "/establishments/$EST/sports-fields" "" "$ADMIN_TOKEN"
    FIELD="$(jq -r --arg n "$FIELD_NAME" '[.[] | select(.name == $n)][0].id // empty' "$BODY_FILE")"
    if [ -z "$FIELD" ]; then
        api POST "/establishments/$EST/sports-fields" "$(jq -nc --arg n "$FIELD_NAME" '{name:$n,sportType:"SOCIETY"}')" "$ADMIN_TOKEN"
        [ "$STATUS" = "201" ] || fail "criar $FIELD_NAME"
        FIELD="$(j .id)"
        created "$FIELD_NAME (id $FIELD)"
    else
        exists "$FIELD_NAME (id $FIELD)"
    fi

    api GET "/establishments/$EST/sports-fields/$FIELD/schedule-slots" "" "$ADMIN_TOKEN"
    cp "$BODY_FILE" "$TMP_DIR/slots.json"
    NEW_SLOTS=0
    for DAY in MONDAY TUESDAY WEDNESDAY THURSDAY FRIDAY SATURDAY SUNDAY; do
        case "$DAY" in
            SATURDAY | SUNDAY) TIMES="09:00 10:00 11:00" PRICE="180.00" ;;
            *) TIMES="19:00 20:00 21:00" PRICE="150.00" ;;
        esac
        for TIME in $TIMES; do
            if jq -e --arg d "$DAY" --arg t "$TIME" 'any(.[]; .dayOfWeek == $d and (.startTime | startswith($t)))' "$TMP_DIR/slots.json" >/dev/null; then
                continue
            fi
            api POST "/establishments/$EST/sports-fields/$FIELD/schedule-slots" \
                "$(jq -nc --arg d "$DAY" --arg t "$TIME" --argjson p "$PRICE" '{dayOfWeek:$d,startTime:$t,price:$p}')" "$ADMIN_TOKEN"
            [ "$STATUS" = "201" ] || fail "criar horário $DAY $TIME em $FIELD_NAME"
            NEW_SLOTS=$((NEW_SLOTS + 1))
        done
    done
    [ "$NEW_SLOTS" -gt 0 ] && created "$NEW_SLOTS horário(s) em $FIELD_NAME" || exists "horários de $FIELD_NAME"
done

echo "Organizador"
if login "$ORGANIZER_EMAIL" "$DEMO_PASSWORD"; then
    exists "$ORGANIZER_EMAIL"
else
    api POST /users "$(jq -nc --arg e "$ORGANIZER_EMAIL" --arg p "$DEMO_PASSWORD" '{name:"Maria Organizadora",email:$e,phone:"19988887777",password:$p}')"
    [ "$STATUS" = "201" ] || fail "criar organizador"
    created "$ORGANIZER_EMAIL"
fi

cat <<INFO

Pronto. Dados de demonstração (API em $API_URL):

  Estabelecimento : $ESTABLISHMENT_NAME (id $EST)
  Admin           : $ADMIN_EMAIL / $DEMO_PASSWORD
  Organizador     : $ORGANIZER_EMAIL / $DEMO_PASSWORD
  Admin plataforma: $PLATFORM_ADMIN_EMAIL (a senha que você configurou no .env)

  Dois campos (Campo 1 e Campo 2): seg a sex 19h, 20h e 21h (R\$ 150,00); sáb e dom 9h, 10h e 11h (R\$ 180,00).
  Swagger: $API_URL/swagger-ui.html
INFO
