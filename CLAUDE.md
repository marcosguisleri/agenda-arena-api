# Agenda Arena API

API REST de agendamento de quadras/campos esportivos para estabelecimentos (início em Araras-SP), com
evolução planejada para SaaS. Backend Java; o front-end (Angular) é um cliente separado.

## Stack

- Java 25, Spring Boot 4.1.x, Maven (use sempre `./mvnw`)
- Spring Data JPA (Hibernate), Flyway, Bean Validation, Spring Web MVC
- PostgreSQL 17 (`docker-compose.yml`); testes com Testcontainers (exige Docker rodando)
- Spring Security (resource server, JWT HS256 emitido pela própria API) + BCrypt. Pacote `auth` (login/token) e `common/security`.
- Pacote raiz: `br.dev.guisleri.agendaarena`

## Comandos

```bash
docker compose up -d                       # Postgres local (usa .env; modelo em .env.example)
./mvnw spring-boot:run                     # sobe a API
./mvnw test -Dtest=ReservationIntegrationTests   # um arquivo de testes (preferir durante o trabalho)
./mvnw test                                # suíte completa (lenta: um container por classe de teste)
./mvnw checkstyle:check                    # estilo; avisos, não falha o build
```

## Arquitetura (não reescrever)

Pacotes **por funcionalidade**, cada um com as camadas `controller / dto / entity / exception / repository / service`
(e `model` para enums): `establishment`, `sportfield`, `schedule`, `user`, `reservation`, `availability`, `common`.

- **Controller**: fino. Recebe DTO validado (`@Valid`), chama o service, devolve `ResponseEntity` com DTO.
  URLs aninhadas: `/establishments/{id}/sports-fields/{id}/schedule-slots/{id}/reservations`.
- **DTOs**: `record`s. Resposta via `XxxResponseDTO.fromEntity(...)`. Nunca expor entidade JPA.
- **Entity**: construtor `protected` sem args (JPA) + construtor público ou *static factory* com regras
  (`Reservation.online(...)`, `Reservation.manual(...)`). Sem setters. `@ManyToOne(fetch = LAZY)`.
- **Service**: injeção por construtor, sem Lombok. `@Transactional` nas operações de escrita.
- **Exceções**: uma classe de domínio por situação, em `<feature>/exception`, traduzidas em
  `common/exception/GlobalExceptionHandler` para `ApiErrorResponseDTO` (404 não encontrado, 409 conflito,
  400 validação/regra). Nova exceção de domínio = novo `@ExceptionHandler` + teste do status HTTP.
- **Idioma**: código, mensagens de exceção e endpoints em inglês. Conversa e documentação em português.
- Estilo existente: 4 espaços, aspas de texto duplas, sem Lombok. Imports com `*` ocorrem (`jakarta.persistence.*`).

## Regras críticas de domínio e concorrência (NÃO alterar sem pedido explícito)

1. **Tempo**: injete o bean `Clock` (fuso `America/Sao_Paulo`, `ClockConfig`). Nunca use `LocalDate.now()` /
   `Instant.now()` sem `Clock`. Os testes substituem o relógio por `MutableClock`.
2. **Reserva de horário**: todo fluxo que cria reserva (avulsa online/manual ou recorrente) primeiro obtém o
   `ScheduleSlot` com **lock pessimista** (`ScheduleSlotService.findBookableScheduleSlotForUpdate`) e só então valida
   (data no passado, dia da semana igual ao do slot, ocupação). Não mover validações para antes do lock.
3. **O banco é a última barreira**: índice único parcial `idx_unique_active_reservation` (reservas CONFIRMED, CANCELLATION_REQUESTED, COMPLETED e NO_SHOW — V12) e
   `EXCLUDE USING gist` `ex_recurring_reservations_no_overlap` (recorrências). `DataIntegrityViolationException`
   é convertida em exceção de domínio (409) olhando o nome da constraint. Mantenha esses nomes estáveis.
4. **Ocupação** = reserva em `ReservationStatus.OCCUPYING` (CONFIRMED, CANCELLATION_REQUESTED, COMPLETED, NO_SHOW; CANCELLED libera) na data **ou** recorrência cobrindo a data (e não liberada) **ou** data bloqueada
   (`SlotBlock`) (`SlotOccupancyService`, `AvailabilityService`). Consultas em lote evitam N+1 (`@EntityGraph`, `IN`/JPQL).
5. **Liberação de ocorrência de recorrência** (`RecurringReservationRelease`; `POST .../recurring-reservations/{id}/releases`):
   uma data liberada
   deixa de contar como ocupada **apenas naquela data**; a recorrência em si continua válida e `existsOverlapping`
   (conflito entre recorrências) **não** deve considerar liberações.

6. **UMA RESERVA = UM SLOT (definitiva, escopo v2.0).** `Reservation` referencia exatamente um `ScheduleSlot`. Horários consecutivos
   são reservas **separadas** (ids, preços, estados e cancelamentos independentes). **Não criar** lista de slots na reserva,
   reserva de 120 min, entidade de agrupamento, cancelamento em cascata, nem unir páginas de partida de reservas consecutivas.
   O modelo atual (um slot por reserva) está correto: não é bug nem funcionalidade faltante.

## Banco de dados e migrations

- Flyway em `src/main/resources/db/migration` (V1…V13 existentes). **Nunca edite uma migration já existente**
  (nem formatação). Mudança de schema = nova migration `V<n+1>__descricao.sql`.
- `spring.jpa.hibernate.ddl-auto=validate`: nome de tabela/coluna/tipo da entidade precisa bater com a migration,
  senão a aplicação não sobe e os testes falham.
- `spring.jpa.open-in-view=false`: não dependa de lazy loading fora de transação/service.
- Atenção: o nome da tabela das liberações na V8 é `recurring_reservations_releases` (reservations no plural no meio); a
  entidade `RecurringReservationRelease` foi alinhada a ele. Não edite a V8; qualquer ajuste futuro vai em nova migration.

## Segurança

- Segredos só no `.env` (ignorado pelo Git). Nunca ler, imprimir ou commitar `.env`; use `.env.example` como modelo.
- Senhas: BCrypt via `PasswordEncoder`; limite de 72 bytes validado em `UserService`. Nunca logar nem devolver hash/senha.
- Valide toda entrada com Bean Validation nos DTOs; normalize telefone/e-mail no service como já é feito.
- **Autenticação**: `POST /auth/login` devolve JWT (HS256, `JWT_SECRET` ≥ 32 bytes no `.env`, 60 min, sem refresh token).
  O token leva `sub` (id do usuário) e `roles`; o vínculo com estabelecimentos NÃO vai no token, é consultado no banco a cada
  requisição (`establishment_admins`), então remover um admin vale na hora.
- **Papéis** (`users.role`): `ORGANIZER` (cadastro público `POST /users`), `ESTABLISHMENT_ADMIN` (criado pelo PLATFORM_ADMIN em
  `POST /establishments/{id}/admins`), `PLATFORM_ADMIN` (criado na subida a partir de `PLATFORM_ADMIN_EMAIL/PASSWORD`).
- **Autorização é negar por padrão** (`SecurityConfig`, `anyRequest().denyAll()`). Públicas: login, cadastro, e a leitura do
  catálogo (GET de estabelecimentos, quadras, horários, disponibilidade). Tudo sob `/establishments/{establishmentId}/**` é
  gestão: só admin vinculado àquele estabelecimento (ou PLATFORM_ADMIN). Estabelecimento de outro → **404** (não revela que
  existe); organizador em rota de gestão → 403; anônimo → 401. Criar estabelecimento, mudar status e criar admins: só PLATFORM_ADMIN.
- **Reserva online**: o `organizerId` vem do token (ORGANIZER não pode reservar para outro: 403). Admin do estabelecimento reserva
  em nome de um usuário informando `organizerId`. `/me/**` usa sempre o id do token, nunca da URL.
- Erros de segurança passam por `SecurityErrorHandler` → `GlobalExceptionHandler` (mesmo formato `ApiErrorResponseDTO`).
- **Toda rota nova de gestão precisa entrar em `EstablishmentAuthorizationIntegrationTests.managementEndpoints()`**
  (testes de outro estabelecimento = 404 sem efeito colateral, organizador = 403, anônimo = 401). Sem isso, a rota não está pronta.
- Variáveis de ambiente: `JWT_SECRET` (obrigatória; a API não sobe sem ela), `JWT_EXPIRATION_MINUTES`, `PLATFORM_ADMIN_NAME/EMAIL/PHONE/PASSWORD`.
- **CORS**: `CorsConfig` libera as origens de `CORS_ALLOWED_ORIGINS` (padrão `http://localhost:4200`), sem credenciais (auth é por header Bearer).
- **OpenAPI/Swagger** (springdoc 3.0.0): `/swagger-ui.html` e `/v3/api-docs`, públicos; desligar em produção com `API_DOCS_ENABLED=false`. Botão Authorize aceita o `accessToken` do login.
- Evite vazar detalhes internos em mensagens de erro (SQL, nomes de constraint) na resposta HTTP.

## Testes

- **Segurança nos testes**: o `MockMvc` autoconfigurado já chama a API como PLATFORM_ADMIN
  (`support/DefaultTestAuthenticationConfiguration`), para os testes de regra de negócio não repetirem login. Testes de
  autenticação/autorização montam o próprio `MockMvc` (`webAppContextSetup(...).apply(springSecurity())`) com tokens reais
  (`JwtTokenService.issue`) e **relógio real** (o decoder usa o relógio do sistema; calcule datas a partir de `LocalDate.now(clock)`).
  Segredo JWT dos testes: `src/test/resources/config/application.properties`.
- Padrão: `*IntegrationTests` com `@SpringBootTest`, `@AutoConfigureMockMvc`, `@Testcontainers`,
  `PostgreSQLContainer("postgres:17-alpine")` estático e `@DynamicPropertySource` (Flyway ligado, `ddl-auto=validate`).
- Preparação por repositórios e `JdbcTemplate`; limpeza em `@BeforeEach` (`deleteAllInBatch`, respeitando FKs).
- Agrupamento com `@Nested` + `@DisplayName`; casos de validação com `@ParameterizedTest`.
- Concorrência: `ExecutorService` com 2 threads + `CountDownLatch` para provocar a corrida no lock do slot
  (veja "Schedule slot lock races" em `RecurringReservationIntegrationTests`). Sem `Thread.sleep` como sincronização.
- Toda regra nova precisa de teste de sucesso, de conflito/erro e (se mexe em reserva) de corrida.
- Nunca remova nem enfraqueça testes existentes para fazer a build passar.
- Rode o menor conjunto possível primeiro (`-Dtest=Classe`); suíte completa só ao final.

## Fluxo de trabalho com IA

- Features novas: use `/desenvolver-feature` (analisa → planeja → **espera aprovação** → implementa → testa → revisa).
- Subagentes somente leitura: `backend-reviewer` (transações, concorrência, segurança, JPA) e `test-specialist`
  (lacunas e desenho de testes).
- Hooks: bloqueiam edição de migrations existentes e de `.env`; ao final do turno rodam Checkstyle (uma única
  rodada de correção). **Nenhum hook roda testes.**
- Nunca fazer `git commit`, `git push` nem deploy sem pedido explícito. Não adicionar dependências sem justificar.
- Não refatorar fora do escopo pedido; mudanças pequenas e revisáveis.

## Como colaborar com o dono do projeto

O dono é desenvolvedor Java júnior em transição de carreira e quer construir autonomia. Regra: **ele decide o nível de ajuda**.
- Pediu orientação/explicação/dica → explique raciocínio, conceitos e passos; não entregue a implementação pronta.
- Pediu código/classe/método/implementação → entregue completo e funcional, sem pseudocódigo e sem exigir que tente antes.
- Sempre explique brevemente as decisões técnicas relevantes e use boas práticas.

## Decisões de produto / SaaS

Fonte vigente: **"Agenda Arena — Escopo funcional consolidado do MVP v2.0" (30/10/2026)**, que substitui o PDF "Agenda Society"
(27/09/2026) nos pontos divergentes, mais o documento de decisões de arquitetura do dono. É especificação de produto, **não prova
de que algo já está implementado**: confira sempre o código. Se o escopo e o código divergirem, ou um ponto estiver pendente,
pergunte; não invente. Produto: SaaS multi-estabelecimento, foco em futebol society, extensível a outras modalidades,
monólito modular, Angular separado consumindo esta API REST.

### Decidido
- **Reserva online**: organizador **autenticado** (conta obrigatória) escolhe um slot livre e a reserva é **confirmada
  automaticamente**. **Não existe** estado normal "aguardando aprovação" nem painel de aprovação de reservas online. Registra
  organizador, estabelecimento, campo, **um** slot, preço efetivo na hora da reserva (nunca alterado retroativamente), origem
  online e estado confirmado. Concorrência: erro de domínio, sem duplicidade.
- **Reserva manual**: criada pelo administrador (canais externos), preserva a origem online/manual, pode ocorrer fora do horário
  comercial respeitando conflitos. **Bloqueios** administrativos sem cliente; ação "cancelar reserva e manter o horário bloqueado".
- **Recorrência**: semanal simples (estabelecimento, campo, dia, slot, início, fim opcional, responsável, estado). Liberar/cancelar
  **uma ocorrência** sem excluir a série. Conflitos com reservas/bloqueios **nunca** resolvidos por sobrescrita: apresentar ao admin.
- **Cancelamento/estados**: organizador **solicita**, admin confirma; admin cancela e pode bloquear o slot; reserva pode ser
  concluída, cancelada ou "não compareceu" (marcado pelo estabelecimento). Nunca cancelar automaticamente por poucos participantes.
  Estados antigos "aguardando aprovação" em bancos antigos: propor migração com cautela (nova migration).
- **Papéis**: visitante (consulta pública, sem conta), organizador, jogador convidado (sem conta, por link), administrador do
  estabelecimento. **MVP: uma conta administrativa por estabelecimento, possivelmente compartilhada pela equipe, sem seletor de
  estabelecimento no painel** (configuração operacional, não promessa de segurança). Futuro: funcionários com RBAC e admin global.
- **Multi-tenancy**: PostgreSQL e schema compartilhados, segregação lógica por estabelecimento (`ScheduleSlot → SportField →
  Establishment` já é indireta). Nem toda tabela precisa de `establishment_id` (usuários globais, associações). **Nunca** aceitar
  `establishmentId` do cliente/URL como prova de acesso: autorizar pelo usuário autenticado.
- **Partida** (uma página por reserva confirmada, link compartilhável; não unir reservas consecutivas): convidados sem cadastro,
  nomes duplicados permitidos (usar id interno do participante); linha ou goleiro; goleiros **opcionais**, até 4; goleiro isento
  do rateio por padrão; sem limite rígido de jogadores de linha; mudança linha↔goleiro permitida antes do fechamento (respeitando
  o limite); valor individual recalculado enquanto aberta (centavos, arredondamento consistente); fechar congela lista e valores,
  bloqueia inscrições, pode reabrir; sorteio aleatório simples de 2 times, refazível; finalizar e resumo básico.
- **Pix**: só exibe a chave (do estabelecimento ou organizador); pagamento fora da plataforma; "pago" é declaração manual, não
  confirmação bancária. Sem Pix automático, gateway, cartão ou integração bancária.
- **Infra**: Docker/Compose, PostgreSQL 17, Flyway dono do schema, `ddl-auto=validate`, OpenAPI/Swagger previsto. Sem config de
  provedor de nuvem específico.

### Recomendado, não definitivo
- Sessão: hoje JWT de 60 min sem refresh token nem revogação (decisão pendente nº 6). O front precisará de uma estratégia de renovação.

### Pendente (propor recomendação e aguardar; NÃO presumir)
1. Antecedência mínima/máxima para reservar e prazo de cancelamento. 2. Matriz final de estados/transições por perfil.
3. Reconfirmação pré-jogo e notificações/e-mail. 4. Hospedagem, monitoramento, backups. 5. Política comercial do SaaS (teste
gratuito, planos, preços, cobrança, suspensão). 6. Sessão, revogação e refresh tokens. 7. Fechar/reabrir organização com
pagamentos já marcados.

### Fora do escopo do MVP
Apps nativos; pagamentos integrados/Pix automático/cartão; cobrança de assinatura; WhatsApp Business API; chat; marketplace;
campeonatos; rankings/estatísticas; IA de balanceamento; white-label; fidelidade/avaliações; recorrência avançada; **exportação
PNG do resumo**; funcionários com RBAC detalhado; reserva multi-slot ou agrupamento de reservas.

### Matriz de estado (evidência: leitura de código em 30/10/2026; fatias 1–5 de reservas executadas e passando em 30/10/2026; reverifique antes de planejar)
| Item | Status | Evidência |
|---|---|---|
| Estabelecimento, campo, slot semanal com preço | implementado (campos de Pix/logo/funcionamento não verificados) | `establishment/`, `sportfield/`, `schedule/` + `*IntegrationTests` |
| Disponibilidade pública | implementado | `availability/`, `AvailabilityIntegrationTests` |
| Reserva online (confirmada na hora, preço copiado do slot, organizador ativo) | implementado | `ReservationService`, `Reservation.online`, `ReservationIntegrationTests` |
| Reserva manual | implementado | `ReservationService.createManualReservation` |
| Um slot por reserva | **conforme escopo** | `Reservation.scheduleSlot` único |
| Recorrência semanal + conflito + corridas | implementado | `RecurringReservationService`, V7 (EXCLUDE gist), `RecurringReservationIntegrationTests` |
| Liberação de ocorrência | **implementado e testado** (fatia 1, 30/10/2026): service com lock do slot, endpoint, ocupação com `NOT EXISTS` | `RecurringReservationService.releaseOccurrence`, `RecurringReservationReleaseIntegrationTests` |
| Bloqueios administrativos (uma data por vez) + cancelar e manter bloqueado (`?keepBlocked=true`) | **implementado e testado** (fatia 3, 30/10/2026): V9 `slot_blocks`, `SlotBlock` no pacote `reservation`, lock do slot, entra na ocupação; bloqueio não sobrescreve reserva/recorrência (409) | `SlotBlockService`, `SlotBlockController`, `SlotBlockIntegrationTests` |
| Cancelamento pelo admin (`CONFIRMED`→`CANCELLED`) | **implementado e testado** (fatia 2, 30/10/2026): `POST .../reservations/{id}/cancel`, lock do slot, sem exigir slot ativo | `ReservationService.cancelReservation`, `ReservationCancellationIntegrationTests` |
| Concluída (`COMPLETED`) e não compareceu (`NO_SHOW`) | **implementado e testado** (fatia 4, 30/10/2026): V10 recria CHECK e índice único; `POST .../reservations/{id}/complete` e `/no-show` só de `CONFIRMED` e só a partir do horário de início (`Clock`), sob lock do slot; continuam ocupando o horário; só `CONFIRMED` pode ser cancelada | `ReservationService.completeReservation/markReservationAsNoShow`, `ReservationStatusTransitionIntegrationTests` |
| Consulta de reserva por id e agenda do admin por campo/dia (`GET .../reservations/{id}`, `GET .../sports-fields/{id}/agenda?date=`) | **implementado e testado** (fatia 5, 30/10/2026): leitura sem lock; agenda com status FREE/RESERVED/RECURRING/BLOCKED (precedência nessa ordem), `recurring.released` indica ocorrência liberada; datas passadas permitidas | `AgendaService`, `ReservationService.findReservation`, `ReservationQueryIntegrationTests` |
| Solicitação de cancelamento pelo organizador (`CANCELLATION_REQUESTED`) | **implementado e testado** (30/10/2026): V12; `POST /me/reservations/{id}/cancellation-request` (só `CONFIRMED`, só antes do início — único ponto do prazo: `ReservationService.ensureCancellationCanBeRequested`); continua ocupando o horário; admin aprova com o `/cancel` existente ou recusa com `POST .../reservations/{id}/refuse-cancellation` (volta a `CONFIRMED`) | `Reservation.requestCancellation/refuseCancellationRequest`, `MyReservationIntegrationTests` |
| "Minhas reservas" (`GET /me/reservations`, `GET /me/reservations/{id}`) | **implementado e testado**: só as do usuário do token; de outro → 404; sem paginação ainda | `MeReservationController`, `MyReservationIntegrationTests` |
| Spring Security/JWT, papéis, vínculo admin↔estabelecimento, isolamento | **implementado e testado** (30/10/2026): V11 (`users.role`, `establishment_admins`), login, 401/403/404 conforme a seção Segurança, `GET /me/establishments` | `SecurityConfig`, `EstablishmentAccessAuthorization`, `AuthenticationIntegrationTests`, `EstablishmentAuthorizationIntegrationTests` |
| Partida: página, participantes, rateio, Pix, times, resumo | pendente | sem pacote |
| CORS e OpenAPI/Swagger | **implementado e testado** (30/10/2026) | `CorsConfig`, `OpenApiConfig`, `CorsAndApiDocsIntegrationTests` |
Antes de qualquer item "pendente/parcial": **plano técnico e aprovação** (mudança estrutural). Não recriar módulos, não reescrever
migrations aplicadas e não alterar concorrência sem análise e testes.
