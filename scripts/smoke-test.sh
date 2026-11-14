#!/usr/bin/env bash
# Teste de fumaça: percorre o fluxo completo da API rodando de verdade (login, estabelecimento, Pix,
# reserva, e-mails, partida pelo link, visão do admin, cancelamento, sessão e limite de requisições).
#
# Uso:
#   PLATFORM_ADMIN_EMAIL=admin@example.com PLATFORM_ADMIN_PASSWORD=... ./scripts/smoke-test.sh
#
# Variáveis:
#   API_URL                   padrão http://localhost:8080
#   PLATFORM_ADMIN_EMAIL/PASSWORD   (obrigatórias) o admin da plataforma criado na subida
#   MAILPIT_URL               ex.: http://localhost:8025 — se informado, confere também os e-mails
#   SMOKE_SKIP_RATE_LIMIT=1   pula a parte do limite de requisições (use se RATE_LIMIT_ENABLED=false)
#
# Cria dados novos a cada execução (nomes com sufixo único); não apaga nada. Use um banco de desenvolvimento.
# O limite de requisições bloqueia o seu IP por cerca de 1 minuto no final (parte do próprio teste).
set -uo pipefail
cd "$(dirname "$0")/.."
# shellcheck source=scripts/lib.sh
source scripts/lib.sh

: "${PLATFORM_ADMIN_EMAIL:?Informe PLATFORM_ADMIN_EMAIL}"
: "${PLATFORM_ADMIN_PASSWORD:?Informe PLATFORM_ADMIN_PASSWORD}"
MAILPIT_URL="${MAILPIT_URL:-}"

PASS=0
FAIL=0
RUN="$(date +%s)"
PASSWORD="senha-segura-123"

step() { echo; echo "== $*"; }
ok() { PASS=$((PASS + 1)); echo "  ok   $*"; }
bad() { FAIL=$((FAIL + 1)); echo "  FALHA $*"; echo "        resposta: $(head -c 300 "$BODY_FILE" 2>/dev/null)"; }

# expect_status DESCRIÇÃO STATUS_ESPERADO
expect_status() {
    if [ "$STATUS" = "$2" ]; then ok "$1 ($STATUS)"; else bad "$1: esperado $2, veio $STATUS"; fi
}
# expect_json DESCRIÇÃO EXPRESSÃO_JQ VALOR_ESPERADO
expect_json() {
    local actual
    actual="$(j "$2")"
    if [ "$actual" = "$3" ]; then ok "$1"; else bad "$1: esperado '$3', veio '$actual'"; fi
}
# expect_true DESCRIÇÃO EXPRESSÃO_JQ_BOOLEANA
expect_true() {
    if jq -e "$2" "$BODY_FILE" >/dev/null 2>&1; then ok "$1"; else bad "$1"; fi
}

# wait_mail DESTINATÁRIO TRECHO_DO_ASSUNTO  -> aguarda até 15 s o e-mail aparecer no Mailpit
wait_mail() {
    local to="$1" subject="$2" i count
    for i in $(seq 1 15); do
        count="$(curl -s "${MAILPIT_URL}/api/v1/messages?limit=200" \
            | jq --arg to "$to" --arg s "$subject" \
                '[.messages[] | select(any(.To[]; .Address == $to)) | select(.Subject | contains($s))] | length' 2>/dev/null)"
        [ "${count:-0}" -ge 1 ] && return 0
        sleep 1
    done
    return 1
}
expect_mail() {
    [ -z "$MAILPIT_URL" ] && return 0
    if wait_mail "$1" "$2"; then ok "e-mail '$2' chegou para $1"; else bad "e-mail '$2' não chegou para $1"; fi
}

DOW="$(day_of_week_in 3)"
DATE="$(date_in +3)"
PAST_DATE="$(date_in -4)"
ORG_EMAIL="organizador.${RUN}@example.com"
ADMIN_EMAIL="admin.${RUN}@example.com"

# ---------------------------------------------------------------------------------------------
step "1. Admin da plataforma: login e renovação da sessão"
login "$PLATFORM_ADMIN_EMAIL" "$PLATFORM_ADMIN_PASSWORD" || { echo "Não consegui logar como admin da plataforma ($STATUS)."; exit 1; }
PLATFORM_TOKEN="$ACCESS_TOKEN"
ok "login do admin da plataforma"
OLD_REFRESH="$REFRESH_TOKEN"
api POST /auth/refresh "$(jq -nc --arg t "$REFRESH_TOKEN" '{refreshToken:$t}')"
expect_status "renovar a sessão" 200
[ "$(j .refreshToken)" != "$OLD_REFRESH" ] && ok "o refresh token mudou (rotação)" || bad "o refresh token deveria mudar"
PLATFORM_TOKEN="$(j .accessToken)"

# ---------------------------------------------------------------------------------------------
step "2. Estabelecimento, admin dele, campo, horário e chave Pix"
api POST /establishments "$(jq -nc --arg n "Arena Smoke $RUN" '{name:$n}')" "$PLATFORM_TOKEN"
expect_status "criar estabelecimento" 201
EST="$(j .id)"

api POST "/establishments/$EST/admins" "$(jq -nc --arg e "$ADMIN_EMAIL" --arg p "$PASSWORD" \
    '{name:"Admin Smoke",email:$e,phone:"19999990000",password:$p}')" "$PLATFORM_TOKEN"
expect_status "criar admin do estabelecimento" 201
login "$ADMIN_EMAIL" "$PASSWORD" && ok "login do admin do estabelecimento" || bad "login do admin do estabelecimento"
ADMIN_TOKEN="$ACCESS_TOKEN"
api GET /me/establishments "" "$ADMIN_TOKEN"
expect_true "o admin enxerga o próprio estabelecimento" ".[] | select(.id == $EST)"

api POST "/establishments/$EST/sports-fields" '{"name":"Campo 1","sportType":"SOCIETY"}' "$ADMIN_TOKEN"
expect_status "criar campo" 201
FIELD="$(j .id)"
api POST "/establishments/$EST/sports-fields/$FIELD/schedule-slots" \
    "$(jq -nc --arg d "$DOW" '{dayOfWeek:$d,startTime:"19:00",price:150.00}')" "$ADMIN_TOKEN"
expect_status "criar horário ($DOW 19:00)" 201
SLOT="$(j .id)"

api PUT "/establishments/$EST/pix-key" \
    '{"keyType":"CNPJ","key":"11.222.333/0001-81","holderName":"Arena Smoke Ltda","bankName":"Banco do Brasil"}' "$ADMIN_TOKEN"
expect_status "configurar Pix do estabelecimento" 200
expect_json "a chave Pix foi normalizada" .key "11222333000181"
api PUT "/establishments/$EST/pix-key" '{"keyType":"CPF","key":"111.111.111-11","holderName":"Maria da Silva"}' "$ADMIN_TOKEN"
expect_status "CPF inválido é recusado" 400

api GET "/establishments/$EST"
expect_status "catálogo público do estabelecimento" 200
if grep -qi "pix" "$BODY_FILE"; then bad "o catálogo público NÃO pode expor o Pix"; else ok "o catálogo público não expõe o Pix"; fi

# ---------------------------------------------------------------------------------------------
step "3. Organizador: cadastro, disponibilidade e reserva online"
api POST /users "$(jq -nc --arg e "$ORG_EMAIL" --arg p "$PASSWORD" \
    '{name:"Maria Organizadora",email:$e,phone:"19988887777",password:$p}')"
expect_status "cadastro do organizador" 201
login "$ORG_EMAIL" "$PASSWORD" && ok "login do organizador" || bad "login do organizador"
ORG_TOKEN="$ACCESS_TOKEN"

api GET "/establishments/$EST/sports-fields/$FIELD/availability?date=$DATE"
expect_status "disponibilidade pública" 200
expect_true "o horário está livre" ".[] | select(.scheduleSlotId == $SLOT and .available == true)"

RES_PATH="/establishments/$EST/sports-fields/$FIELD/schedule-slots/$SLOT/reservations"
api POST "$RES_PATH" "$(jq -nc --arg d "$DATE" '{reservationDate:$d}')" "$ORG_TOKEN"
expect_status "reservar online (confirmada na hora)" 201
RES="$(j .id)"
expect_json "reserva confirmada" .status CONFIRMED
api POST "$RES_PATH" "$(jq -nc --arg d "$DATE" '{reservationDate:$d}')" "$ORG_TOKEN"
expect_status "segunda reserva do mesmo horário é recusada" 409
api GET "/establishments/$EST/sports-fields/$FIELD/availability?date=$DATE"
expect_true "o horário ficou ocupado" ".[] | select(.scheduleSlotId == $SLOT and .available == false)"
expect_mail "$ORG_EMAIL" "Reserva confirmada"
expect_mail "$ADMIN_EMAIL" "Novo agendamento online"

# ---------------------------------------------------------------------------------------------
step "4. Partida: Pix do estabelecimento, inscrição pelo link, pagamento, fechamento e times"
api POST "/me/reservations/$RES/match" '{"pix":{"source":"ESTABLISHMENT"}}' "$ORG_TOKEN"
expect_status "criar a partida com o Pix do estabelecimento" 201
MATCH="$(j .id)"
TOKEN="$(j .publicToken)"
expect_json "Pix copiado do estabelecimento" .pix.key "11222333000181"
expect_json "titular do Pix" .pix.holderName "Arena Smoke Ltda"

for guest in "Joao:FIELD" "Pedro:FIELD" "Lucas:FIELD" "Goleiro:GOALKEEPER"; do
    api POST "/matches/$TOKEN/participants" "$(jq -nc --arg n "${guest%%:*}" --arg p "${guest##*:}" '{name:$n,position:$p}')"
    expect_status "convidado ${guest%%:*} se inscreve pelo link" 201
done
api GET "/matches/$TOKEN"
expect_status "página pública da partida" 200
expect_json "3 jogadores de linha" .fieldPlayerCount 3
expect_json "1 goleiro" .goalkeeperCount 1
expect_json "a página pública mostra o titular do Pix" .pix.holderName "Arena Smoke Ltda"
if grep -q "organizer\|passwordHash" "$BODY_FILE"; then bad "a página pública não pode expor dados do organizador"; else ok "a página pública não expõe dados do organizador"; fi

api GET "/me/matches/$MATCH" "" "$ORG_TOKEN"
FIRST_PARTICIPANT="$(j '.participants[0].id')"
expect_true "cota de cada jogador de linha (150 / 3 = 50,00)" '.participants[0].shareAmount == 50'
api PUT "/me/matches/$MATCH/participants/$FIRST_PARTICIPANT/payment" '{"paid":true}' "$ORG_TOKEN"
expect_status "declarar pagamento" 200
api POST "/me/matches/$MATCH/close" "" "$ORG_TOKEN"
expect_status "fechar a partida" 200
api POST "/me/matches/$MATCH/team-draw" "" "$ORG_TOKEN"
expect_status "sortear os times" 200
api GET "/me/matches/$MATCH/summary" "" "$ORG_TOKEN"
expect_status "resumo da partida" 200

# ---------------------------------------------------------------------------------------------
step "5. Visão do admin: reservas, séries e partidas"
api GET "/establishments/$EST/reservations" "" "$ADMIN_TOKEN"
expect_status "listagem de reservas" 200
expect_true "a reserva aparece, com o contato do organizador" \
    ".content[] | select(.id == $RES and .customerName == \"Maria Organizadora\" and .customerPhone == \"19988887777\")"
api GET "/establishments/$EST/recurring-reservations" "" "$ADMIN_TOKEN"
expect_status "listagem de séries recorrentes" 200
api GET "/establishments/$EST/matches" "" "$ADMIN_TOKEN"
expect_status "listagem de partidas" 200
expect_true "a partida aparece com as contagens" \
    ".content[] | select(.id == $MATCH and .fieldPlayerCount == 3 and .goalkeeperCount == 1 and .paidCount == 1)"
api GET "/establishments/$EST/matches/$MATCH" "" "$ADMIN_TOKEN"
expect_status "detalhe da partida para o admin" 200
if grep -q "publicToken" "$BODY_FILE"; then bad "o admin não deve ver o token público"; else ok "o admin não vê o token público"; fi

# ---------------------------------------------------------------------------------------------
step "6. Cancelamento: pedido recusado, pedido aprovado e a partida cancelada"
api POST "/me/reservations/$RES/cancellation-request" "" "$ORG_TOKEN"
expect_status "organizador pede o cancelamento" 200
expect_json "reserva com pedido de cancelamento" .status CANCELLATION_REQUESTED
expect_mail "$ORG_EMAIL" "Pedido de cancelamento recebido"
expect_mail "$ADMIN_EMAIL" "Pedido de cancelamento"
api POST "$RES_PATH/$RES/refuse-cancellation" "" "$ADMIN_TOKEN"
expect_status "admin recusa o pedido" 200
expect_json "a reserva voltou a confirmada" .status CONFIRMED
expect_mail "$ORG_EMAIL" "Pedido de cancelamento recusado"
api POST "/me/reservations/$RES/cancellation-request" "" "$ORG_TOKEN"
expect_status "organizador pede de novo" 200
api POST "$RES_PATH/$RES/cancel" "" "$ADMIN_TOKEN"
expect_status "admin aprova cancelando a reserva" 200
expect_json "reserva cancelada" .status CANCELLED
expect_mail "$ORG_EMAIL" "Reserva cancelada"
api GET "/establishments/$EST/sports-fields/$FIELD/availability?date=$DATE"
expect_true "o horário voltou a ficar livre" ".[] | select(.scheduleSlotId == $SLOT and .available == true)"
api GET "/establishments/$EST/matches/$MATCH" "" "$ADMIN_TOKEN"
expect_json "a partida aparece como cancelada para o admin" .cancelled true

# ---------------------------------------------------------------------------------------------
step "7. Reserva manual retroativa, concluir e corrigir (reopen)"
api POST "$RES_PATH" "$(jq -nc --arg d "$PAST_DATE" '{reservationDate:$d}')" "$ORG_TOKEN"
expect_status "reserva online no passado é recusada" 400
api POST "$RES_PATH/manual" "$(jq -nc --arg d "$PAST_DATE" '{reservationDate:$d,customerName:"Joao do WhatsApp",customerPhone:"19977776666"}')" "$ADMIN_TOKEN"
expect_status "reserva manual retroativa (agendada por WhatsApp)" 201
MANUAL="$(j .id)"
api POST "$RES_PATH/$MANUAL/complete" "" "$ADMIN_TOKEN"
expect_status "concluir a reserva" 200
api POST "$RES_PATH/$MANUAL/reopen" "" "$ADMIN_TOKEN"
expect_status "corrigir a marcação (reopen)" 200
expect_json "voltou a confirmada" .status CONFIRMED

# ---------------------------------------------------------------------------------------------
step "8. Sessão: rotação e detecção de reuso do refresh token"
login "$ORG_EMAIL" "$PASSWORD" && ok "nova sessão do organizador" || bad "nova sessão do organizador"
FIRST_REFRESH="$REFRESH_TOKEN"
api POST /auth/refresh "$(jq -nc --arg t "$FIRST_REFRESH" '{refreshToken:$t}')"
expect_status "renovar" 200
SECOND_REFRESH="$(j .refreshToken)"
api POST /auth/refresh "$(jq -nc --arg t "$FIRST_REFRESH" '{refreshToken:$t}')"
expect_status "reusar um refresh token já trocado é recusado" 401
api POST /auth/refresh "$(jq -nc --arg t "$SECOND_REFRESH" '{refreshToken:$t}')"
expect_status "a sessão inteira foi revogada" 401
api POST /auth/logout "$(jq -nc --arg t "$SECOND_REFRESH" '{refreshToken:$t}')"
expect_status "logout é idempotente" 204

# ---------------------------------------------------------------------------------------------
if [ "${SMOKE_SKIP_RATE_LIMIT:-0}" != "1" ]; then
    step "9. Limite de requisições (login)"
    LIMITED=0
    for i in $(seq 1 12); do
        api POST /auth/login "$(jq -nc --arg e "alvo.${RUN}@example.com" '{email:$e,password:"errada-123"}')"
        if [ "$STATUS" = "429" ]; then LIMITED=1; break; fi
    done
    [ "$LIMITED" = "1" ] && ok "o login foi limitado (429) depois de várias tentativas" || bad "esperava 429 depois de várias tentativas de login"
    [ -n "$(header Retry-After)" ] && ok "a resposta traz o Retry-After ($(header Retry-After) s)" || bad "faltou o cabeçalho Retry-After"
fi

echo
echo "=============================="
echo " Resultado: $PASS ok, $FAIL falha(s)"
echo "=============================="
[ "$FAIL" -eq 0 ]
