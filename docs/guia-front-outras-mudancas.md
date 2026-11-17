# Guia para o front: outras mudanças de contrato

Complementa `docs/guia-front-pix-e-sessao.md`. Escrito em 14/11/2026. Erros seguem o formato
`{"status": 400, "error": "Bad Request", "message": "...", "errors": {"campo": "mensagem"}}`.

## 1. Limite de requisições (429)

As rotas **públicas** têm limite por IP: login (10 por minuto, e 5 a cada 5 minutos por conta), refresh e logout
(30 por minuto), cadastro (10 por hora), página da partida pelo link (60 por minuto), inscrição na partida (10 por minuto
por IP e 60 por hora por link) e catálogo público (120 por minuto). Rotas autenticadas não têm limite.

Ao estourar, a resposta é **429** com o cabeçalho `Retry-After` (segundos) e `message` "Too many requests. Try again in N
seconds". Mostre a mensagem ao usuário e não repita a requisição antes do prazo. O 429 leva os cabeçalhos CORS, então o
front consegue lê-lo.

## 2. Janela de datas para reservar

- **Reserva online** (organizador): de hoje até **14 dias** à frente, e nunca em horário que já começou. Data além de 14
  dias → 400 (`Reservation date is too far in the future`).
- **Disponibilidade pública:** consulta de data além de 14 dias → 400 (`Availability date is too far in the future`). O
  front deve limitar o seletor de datas a 14 dias.
- **Reserva manual** (admin): pode ser retroativa, inclusive hoje em horário que já passou (agendamento feito por WhatsApp
  e esquecido), até 30 dias atrás; sem limite à frente. Mais antigo que 30 dias → 400 (`Reservation date is too far in the
  past`).
- **Corrigir uma marcação:** `POST .../reservations/{id}/reopen` (admin) leva `COMPLETED` ou `NO_SHOW` de volta a
  `CONFIRMED`; qualquer outro estado → 409.

## 3. Listagens do admin (paginadas)

Todas sob `/establishments/{establishmentId}/...`, só para o admin daquele estabelecimento (ou da plataforma). Formato de
página: `{"content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3}`. `page` começa em 0, `size`
padrão 20 e máximo 100. A ordem é fixa (data, horário, id) e não vem do cliente.

| Rota | Filtros (todos opcionais) |
|---|---|
| `GET /reservations` | `from`, `to` (padrão hoje a +30 dias; máximo 92 dias), `sportFieldId`, `status` (repetível), `origin` (`ONLINE`/`MANUAL`) |
| `GET /recurring-reservations` | `status` (`ACTIVE`/`UPCOMING`/`ENDED`, repetível; padrão ativas e futuras), `sportFieldId`, `hasOrganizer` |
| `GET /matches` | `from`, `to` (como em reservas), `sportFieldId`, `status` (`OPEN`/`CLOSED`, repetível) |
| `GET /matches/{matchId}` | detalhe somente leitura: participantes, cotas, pagamentos, times e totais |

- Para mostrar os **pedidos de cancelamento pendentes**: `GET /reservations?status=CANCELLATION_REQUESTED`.
- Em cada reserva, `customerName` e `customerPhone` são o contato de quem reservou (o organizador, nas reservas online).
- Em partidas, `cancelled=true` indica que o horário de origem deixou de existir (reserva cancelada ou ocorrência
  liberada). A visão do admin nunca traz o link público da partida nem a chave Pix.
- Erros: período inválido (`from` depois de `to`, ou mais de 92 dias), `size` fora de 1 a 100 ou `page` negativa → 400 com o
  campo em `errors`; campo de outro estabelecimento → 404.

## 3b. "Minhas reservas" (organizador) agora é paginada

`GET /me/reservations` deixou de devolver um array e passa a devolver uma página, no mesmo formato das listagens do admin:
`{"content": [...], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3}`. Parâmetros opcionais: `page` (começa em 0) e
`size` (padrão 20, máximo 100). A ordem é fixa: data e horário mais recentes primeiro (o `id` desempata). Valores inválidos de
`page`/`size` → 400. `GET /me/reservations/{id}` não mudou.

## 4. E-mails

Os e-mails de reserva (confirmação, cancelamento, pedido de cancelamento e recusa) são enviados pela API; o front não
precisa fazer nada. A reserva manual não envia e-mail (o cliente não tem conta).

Todo e-mail traz uma linha `Ver a reserva: <url>`. **O front precisa ter estas duas rotas** (a base vem de `FRONT_URL` na API):

- organizador: `/me/reservations/{id}` (detalhe da reserva dele);
- administrador: `/admin/reservations/{id}` (detalhe da reserva no painel).

Se preferir outros caminhos, avise: eles ficam num só lugar da API (`FrontLinks`).
