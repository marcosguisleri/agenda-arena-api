# Agenda Arena API

API REST de agendamento de quadras e campos esportivos para estabelecimentos (começando em Araras-SP), pensada para
evoluir para SaaS multi-estabelecimento. O front-end (Angular) é um projeto separado que consome esta API.

Foco do MVP: futebol society. O organizador (com conta) reserva um horário; o estabelecimento gerencia reservas,
bloqueios e recorrências; os jogadores entram na partida por um link, sem cadastro, e o rateio e os times são
organizados na própria página da partida.

## Requisitos

- Java 25
- Docker (Postgres e Mailpit em desenvolvimento; os testes usam Testcontainers e também precisam do Docker rodando)
- `curl` e `jq` (só para os scripts em `scripts/`)

O Maven vem junto: use sempre `./mvnw`.

## Subindo localmente

```bash
cp .env.example .env          # edite: JWT_SECRET (mín. 32 bytes), senhas e o admin da plataforma
docker compose up -d          # Postgres + Mailpit
./mvnw spring-boot:run        # API em http://localhost:8080
```

- **Swagger UI:** http://localhost:8080/swagger-ui.html (botão *Authorize* aceita o `accessToken` do login)
- **E-mails de desenvolvimento (Mailpit):** http://localhost:8025. Com `MAIL_ENABLED=true` no `.env`, os e-mails da API
  chegam lá e nada sai de verdade. Com `MAIL_ENABLED=false` (padrão), a API só registra no log que não enviou.
- Se a porta 5432/5433 do Postgres estiver ocupada por outro projeto, mude `POSTGRES_PORT` no `.env`.
- O `.env` nunca vai para o Git. Modelo e comentários de cada variável: `.env.example`.

O administrador da plataforma é criado na subida a partir de `PLATFORM_ADMIN_EMAIL` e `PLATFORM_ADMIN_PASSWORD`. É ele
que cria estabelecimentos e seus administradores.

## Dados de demonstração e teste de fumaça

Com a API no ar:

```bash
export PLATFORM_ADMIN_EMAIL=...   PLATFORM_ADMIN_PASSWORD=...   # os mesmos do .env

./scripts/seed-demo.sh        # cria estabelecimento, admin, Pix, 2 campos, horários e um organizador (idempotente)
./scripts/smoke-test.sh       # percorre o fluxo completo e confere cada passo
```

- `seed-demo.sh` imprime os logins de demonstração (admin e organizador) para você usar no front.
- `smoke-test.sh` exercita a API de verdade: login e renovação de sessão, estabelecimento e Pix, reserva online,
  partida pelo link, visão do admin, cancelamento, reserva manual retroativa, reuso de refresh token e limite de
  requisições. Com `MAILPIT_URL=http://localhost:8025` ele também confere os e-mails. Ele cria dados novos a cada
  execução (use um banco de desenvolvimento) e, no fim, esgota o limite de login do seu IP por cerca de 1 minuto.

Durante o desenvolvimento do front, se o limite de requisições atrapalhar, use `RATE_LIMIT_ENABLED=false` no `.env`.

## Testes

```bash
./mvnw test -Dtest=ReservationIntegrationTests   # um arquivo (rápido, recomendado no dia a dia)
./mvnw test                                      # suíte completa (lenta: um container por classe de teste)
./mvnw checkstyle:check                          # estilo (avisos, não falha o build)
```

Se der erro estranho de classes, rode `./mvnw clean test`. Não compile pelo IntelliJ enquanto a suíte roda pelo Maven
(os dois escrevem em `target/`).

## Autenticação em 30 segundos

```bash
curl -s localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"organizador.demo@agendaarena.local","password":"demo-123456"}'
# -> {"accessToken": "...", "tokenType": "Bearer", "expiresIn": 900, "refreshToken": "...", "refreshExpiresIn": 2592000}
```

O `accessToken` (JWT, 15 min) vai no cabeçalho `Authorization: Bearer ...`. Antes de expirar, troque o `refreshToken`
por um par novo em `POST /auth/refresh`. Papéis: `ORGANIZER` (cadastro público), `ESTABLISHMENT_ADMIN` (criado pela
plataforma) e `PLATFORM_ADMIN`.

## Para quem vai fazer o front

- Contrato completo e navegável: Swagger UI (`/swagger-ui.html`) e `/v3/api-docs`.
- Mudanças que exigem atenção (Pix, sessão com refresh token, limite de requisições, janela de datas, listagens do
  admin): `docs/guia-front-pix-e-sessao.md` e `docs/guia-front-outras-mudancas.md`.
- CORS: libera as origens de `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`), sem credenciais (a autenticação é
  por cabeçalho Bearer).
- Todos os erros têm o mesmo formato: `{"status": 400, "error": "...", "message": "...", "errors": {"campo": "mensagem"}}`.

## Estrutura e regras do projeto

Spring Boot 4, Java 25, PostgreSQL 17, Flyway (dono do schema), Spring Security (resource server com JWT próprio).
Pacotes por funcionalidade (`establishment`, `sportfield`, `schedule`, `user`, `reservation`, `availability`, `match`,
`notification`, `pix`, `auth`, `common`), cada um com `controller / dto / entity / exception / repository / service`.

As regras de domínio e de concorrência (lock do horário na reserva, lock da partida, índices únicos como última barreira,
autorização por estabelecimento, migrations imutáveis) e o estado de cada funcionalidade estão no
[`CLAUDE.md`](CLAUDE.md). Leia antes de mexer em reserva, partida ou segurança.

## Variáveis de ambiente principais

| Variável | Para quê |
|---|---|
| `POSTGRES_*` | banco (usado pelo `docker-compose.yml` e pela API) |
| `JWT_SECRET` | segredo do JWT (obrigatória, mínimo 32 bytes) |
| `JWT_EXPIRATION_MINUTES`, `REFRESH_EXPIRATION_DAYS` | duração do access token (15) e da sessão renovável (30 dias) |
| `PLATFORM_ADMIN_NAME/EMAIL/PHONE/PASSWORD` | admin da plataforma criado na subida |
| `CORS_ALLOWED_ORIGINS` | origens do front |
| `API_DOCS_ENABLED` | Swagger e `/v3/api-docs` (desligue em produção) |
| `MAIL_ENABLED`, `MAIL_*` | e-mails transacionais (SMTP) |
| `RATE_LIMIT_ENABLED`, `RATE_LIMIT_TRUST_FORWARDED_HEADERS` | limite de requisições nas rotas públicas |
| `RESERVATION_MAX_ADVANCE_DAYS`, `RESERVATION_MANUAL_MAX_PAST_DAYS` | janela de datas para reservar (14 e 30) |
