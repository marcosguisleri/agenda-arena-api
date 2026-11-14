# Guia para o front: Pix e sessão (refresh token)

Mudanças de contrato da API que o front precisa conhecer. Escrito em 14/11/2026; vale para as PRs
#11 (refresh token) e #13 (Pix). Todos os erros seguem o formato de sempre:
`{"status": 400, "error": "Bad Request", "message": "...", "errors": {"campo": "mensagem"}}`.

## 1. Pix

### 1.1 Resposta da partida (organizador e página pública)

O texto `pixKey` foi substituído pelo objeto `pix` (nulo quando a partida não tem Pix).

Antes:
```json
{ "pixKey": "organizador@example.com" }
```

Agora:
```json
{
  "pix": {
    "source": "ESTABLISHMENT",
    "keyType": "CNPJ",
    "key": "11222333000181",
    "holderName": "Arena Central Ltda",
    "bankName": "Banco do Brasil"
  },
  "pixKey": "11222333000181"
}
```

- `source`: `ESTABLISHMENT` (chave do estabelecimento, copiada) ou `ORGANIZER` (própria).
- `keyType`: `CPF`, `CNPJ`, `PHONE`, `EMAIL` ou `RANDOM` (chave aleatória).
- `key`: já normalizada pela API (CPF/CNPJ só com caracteres, telefone como `+5519999998888`,
  e-mail em minúsculas). Formate na tela como quiser.
- `holderName`: nome completo de quem recebe. Mostre ao pagador para conferir.
- `bankName`: opcional (pode vir nulo).
- **`pixKey` (texto) é obsoleto e temporário**: igual a `pix.key`, nulo sem Pix. Existe só para o
  front antigo continuar funcionando. Migre para `pix` e pare de usar `pixKey`; ele será removido.

### 1.2 Criar a partida com Pix (opcional)

`POST /me/reservations/{reservationId}/match` e
`POST /me/recurring-reservations/{id}/occurrences/{date}/match` agora aceitam um corpo opcional.
Sem corpo, tudo funciona como antes (partida sem Pix).

Chave do estabelecimento:
```json
{ "pix": { "source": "ESTABLISHMENT" } }
```

Chave do organizador:
```json
{
  "pix": {
    "source": "ORGANIZER",
    "keyType": "PHONE",
    "key": "(19) 99999-8888",
    "holderName": "Maria da Silva",
    "bankName": "Nubank"
  }
}
```

- Se a partida **já existir**, a API devolve 200 com a partida atual e **ignora o corpo**.
- `ESTABLISHMENT` com o estabelecimento sem chave configurada → **409**.
- Chave inválida (CPF/CNPJ com dígito errado, telefone fora do padrão, titular com uma palavra só...)
  → **400** com o campo em `errors` (`key`, `keyType`, `holderName`, `bankName`).
- Com `ESTABLISHMENT`, não envie os campos da chave (→ 400 em `errors.source`).

### 1.3 Trocar ou remover o Pix de uma partida existente

- `PUT /me/matches/{matchId}/pix-key` com o mesmo formato do item 1.2 (`source` e, para
  `ORGANIZER`, os dados da chave) → devolve a partida.
- `DELETE /me/matches/{matchId}/pix-key` → remove (200 com a partida; idempotente).
- Formato antigo `{"pixKey": ...}`, **temporário**: `null` ou vazio ainda remove a chave; texto
  preenchido dá 400 (`errors.pixKey`), porque agora a chave precisa de tipo e titular.
- A chave do estabelecimento é **copiada** para a partida. Se o estabelecimento trocar a chave
  depois, as partidas existentes continuam com a antiga até o organizador reescolher
  (`PUT` com `{"source": "ESTABLISHMENT"}`).

### 1.4 Pix do estabelecimento (painel do admin)

Só admin do estabelecimento ou admin da plataforma. Nunca aparece nas rotas públicas do catálogo.

- `PUT /establishments/{id}/pix-key` com `{"keyType", "key", "holderName", "bankName"}` (banco
  opcional) → 200 com a chave normalizada.
- `GET /establishments/{id}/pix-key` → 200, ou **404** se não houver chave configurada.
- `DELETE /establishments/{id}/pix-key` → 204 (idempotente).

## 2. Sessão: login, refresh e logout

### 2.1 Login

`POST /auth/login` continua igual; a resposta ganhou dois campos:
```json
{
  "accessToken": "...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "refreshToken": "...",
  "refreshExpiresIn": 2592000
}
```
- O **access token** (JWT) agora dura **15 minutos** por padrão (`expiresIn`, em segundos).
- O **refresh token** é opaco e dura 30 dias a partir do último uso. Trate como senha: guarde com
  cuidado e nunca em log ou URL.

### 2.2 Renovar: `POST /auth/refresh`

Corpo: `{"refreshToken": "..."}`. Resposta: o mesmo formato do login, com **um novo par**.

- **A cada renovação o refresh token muda.** Substitua o guardado pelo novo; o antigo deixa de valer.
- **Reusar um refresh token já trocado revoga a sessão inteira** (sinal de roubo). Por isso, **não
  renove em paralelo**: se duas abas ou duas requisições 401 disparam a renovação ao mesmo tempo
  com o mesmo token, uma delas derruba a sessão. Faça uma renovação por vez e faça as demais
  requisições esperarem o resultado (um "single-flight" no interceptor).
- 401 em `/auth/refresh` (mensagem `Invalid refresh token`): token desconhecido, expirado,
  revogado ou usuário inativo. Mande o usuário para o login.
- Fluxo sugerido no interceptor: ao receber 401 numa rota autenticada, renove uma vez, repita a
  requisição com o novo access token; se a renovação falhar, vá para o login.

### 2.3 Sair: `POST /auth/logout`

Corpo: `{"refreshToken": "..."}`. Responde 204 (idempotente, mesmo com token desconhecido). Encerra
só a sessão daquele token. Apague os dois tokens do armazenamento local.

Observação: o access token já emitido segue válido até expirar (no máximo 15 minutos) mesmo após o
logout.

### 2.4 Rotas públicas

`/auth/login`, `/auth/refresh` e `/auth/logout` não exigem `Authorization`.
