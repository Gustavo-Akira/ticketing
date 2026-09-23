# Domain Model

## User e Role

Representam uma identidade que pode comprar ingressos, organizar eventos ou
administrar o sistema, conforme as roles explicitamente atribuídas.

```text
User
- id
- name
- email
- roles
- createdAt
- updatedAt
```

`Role` admite `CUSTOMER`, `ORGANIZER` e `ADMIN`. Um usuário possui pelo menos uma
role e pode acumular várias; não há hierarquia implícita. O conjunto exposto pelo
domínio é imutável.

Nome é obrigatório, com até 255 caracteres. E-mail é obrigatório, com até 254
caracteres após normalização: remover espaços externos e converter para minúsculas
com `Locale.ROOT`. O formato básico exige uma parte local e um domínio separados
por um único `@`, sem espaços; não há validação DNS. A unicidade é garantida no banco.

IDs seguem o UUID temporal usado por Event. Datas são geradas pelo banco na
inserção, e a reconstituição preserva ID e auditoria. O domínio exige ao menos uma
role; a tabela de associações garante valores válidos, ausência de duplicação e
referência a um usuário existente. Ela não impõe cardinalidade mínima por trigger.

Cadastro público atribui somente CUSTOMER. ADMIN concede ORGANIZER de forma
aditiva e idempotente; não existe hierarquia automática de roles. O comando
administrativo cria uma nova conta ADMIN e rejeita e-mail já existente.

Credenciais ficam em `user_credentials`, com hash BCrypt custo 12 e FK para User.
PasswordPolicy valida 12 a 64 caracteres Unicode e até 72 bytes UTF-8 sem trim.
Usuários legados podem existir sem credencial; login é negado nesse caso.

RefreshSession identifica uma sessão de login independente, com userId,
createdAt, expiresAt (limite absoluto de 7 dias) e revokedAt. RefreshToken
identifica um segredo pelo hash SHA-256, com sessionId e consumedAt. Renovação
consome o token anterior e cria seu sucessor atomicamente; reutilização revoga
a sessão. Tokens consumidos são preservados para detectar replay. Logout revoga
somente a sessão indicada. JWT de acesso tem duração de 15 minutos e permanece
válido até expirar. Não há vínculo User→Event nesta entrega.

---

## Event

Representa um evento comercializável.

```text
Event
- id
- name
- startsAt
- status
- createdAt
- updatedAt
```

Estados sugeridos:

```text
DRAFT
AVAILABLE
SALES_CLOSED
FINISHED
CANCELLED
```

---

## Seat

Representa um assento vendável dentro de um evento.

```text
Seat
- id
- eventId
- section
- row
- number
- price
- currency
- status
- version
```

Estados:

```text
AVAILABLE
RESERVED
SOLD
```

Fluxo:

```text
AVAILABLE
   |
   | reservation
   v
RESERVED
   |
   | payment confirmed
   v
SOLD
```

Expiração:

```text
RESERVED
   |
   | reservation expired
   v
AVAILABLE
```

---

## Reservation

Representa uma intenção temporária de aquisição.

```text
Reservation
- id
- eventId
- userId
- status
- createdAt
- expiresAt
- paymentProcessingUntil
```

Estados:

```text
PENDING
PAYMENT_PROCESSING
CONFIRMED
EXPIRED
CANCELLED
```

Transições:

```text
PENDING
  ├── PAYMENT_PROCESSING
  ├── EXPIRED
  └── CANCELLED

PAYMENT_PROCESSING
  ├── CONFIRMED
  ├── PENDING
  └── CANCELLED
```

---

## ReservationSeat

Relaciona a reserva aos assentos.

```text
ReservationSeat
- reservationId
- seatId
```

Uma reserva só pode conter assentos de um único evento.

---

## Payment

Representa o pagamento lógico da reserva.

```text
Payment
- id
- reservationId
- amount
- status
- idempotencyKey
- createdAt
```

Estados:

```text
CREATED
PROCESSING
CAPTURED
DECLINED
UNKNOWN
FAILED
```

`UNKNOWN` é usado quando não existe evidência suficiente para afirmar sucesso ou falha.

Exemplo:

```text
request enviada
    |
provider processa
    |
resposta se perde
    |
timeout
    |
UNKNOWN
```

---

## PaymentAttempt

Representa uma tentativa específica de comunicação com um fornecedor.

```text
PaymentAttempt
- id
- paymentId
- attemptNumber
- provider
- providerReference
- status
- fencingToken
- createdAt
```

Separar `Payment` de `PaymentAttempt` permite:

- múltiplas tentativas;
- reconciliação;
- troca futura de fornecedor;
- auditoria;
- análise de falhas.

---

## OutboxEvent

Evento persistido na mesma transação da alteração de domínio.

```text
OutboxEvent
- id
- aggregateType
- aggregateId
- eventType
- payload
- createdAt
- publishedAt
- attempts
```

Exemplos:

```text
ReservationExpirationScheduled
PaymentRequested
ReservationConfirmed
ReservationExpired
```
