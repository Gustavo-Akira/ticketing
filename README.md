# Ticketing Core

Persistência de eventos e assentos e API de eventos em um monólito Spring Boot,
organizado por feature conforme ADR-0001.

## Executar

Requisitos: Docker com Compose. A partir desta pasta:

```sh
docker compose up --build -d
docker compose logs -f app
docker compose down
```

A aplicação usa a porta 8080 e o PostgreSQL a 5432, ambas limitadas ao localhost.
O volume `postgres-data` preserva os dados. As credenciais padrão são apenas para
desenvolvimento local; `DB_PASSWORD` permite alterá-las antes de criar o banco.
Para executar fora do Compose, use JDK 25, inicie `docker compose up -d postgres`
e execute `./gradlew bootRun` (`gradlew.bat bootRun` no Windows).
Configure `DB_URL`, `DB_USERNAME` e `DB_PASSWORD` conforme o ambiente.

Spring Security mantém a configuração padrão do bootstrap: autenticação obrigatória
e CSRF ativo. O usuário local padrão é `user`, com senha gerada no log de inicialização;
fora do Compose, `SPRING_SECURITY_USER_NAME` e `SPRING_SECURITY_USER_PASSWORD` permitem
configurá-los. Escritas exigem uma sessão e seu token CSRF válido no header
`X-CSRF-TOKEN`; autenticação Basic sozinha não remove essa exigência.
Os testes da API exercitam requisições autenticadas com e sem CSRF.

## API de eventos

| Operação | Endpoint | Resposta |
| --- | --- | --- |
| Criar | `POST /events` | `201`, evento criado e header `Location` |
| Consultar | `GET /events/{id}` | `200` ou `404` |
| Listar | `GET /events?page=0&size=20` | `200`, página de eventos |
| Atualizar | `PUT /events/{id}` | `200` ou `404` |

Criação e atualização recebem `Content-Type: application/json`:

```json
{
  "name": "Concert",
  "startsAt": "2027-01-10T20:00:00Z"
}
```

`name` deve conter de 1 a 255 caracteres, sem ser apenas espaços; `startsAt`
é obrigatório e inclui fuso horário. Ambos são obrigatórios no PUT.
O evento nasce em `DRAFT`; a atualização altera somente nome e data.
ID, status e timestamps são definidos pelo servidor. A resposta contém
`id`, `name`, `startsAt`, `status`, `createdAt` e `updatedAt`.

A listagem retorna `content`, `page`, `size`, `totalElements` e `totalPages`,
ordenada por ID crescente. A página começa em zero, com tamanho de 1 a 100
(padrão 20). O deslocamento `page * size` deve caber em um inteiro de 32 bits.
Página além do resultado retorna `content: []`.
Erros de entrada retornam `400`; ID inexistente retorna `404`, com corpo
`application/problem+json` (`status`, `title`, `detail`). Falhas de autenticação
e CSRF continuam sob responsabilidade do Spring Security.

Cada escrita é transacional. O PUT atribui `updated_at = statement_timestamp()`
explicitamente e preserva `created_at`. Atualizações concorrentes seguem
last-write-wins; os timestamps não funcionam como controle de concorrência.

Publicação e cancelamento terão use cases próprios em uma etapa futura.
Criação de assentos, reservas e pagamentos permanecem no roadmap.

## API de assentos

| Operação | Endpoint | Resposta |
| --- | --- | --- |
| Consultar | `GET /events/{eventId}/seats/{id}` | `200` ou `404` |
| Listar | `GET /events/{eventId}/seats?page=0&size=20` | `200`, página de assentos, ou `404` para evento inexistente |
| Atualizar | `PUT /events/{eventId}/seats/{id}` | `200`, `400`, `404` ou `409` |

O PUT exige todos os campos abaixo:

```json
{
  "section": "Floor",
  "row": "A",
  "number": "15",
  "price": 120.50,
  "currency": "BRL"
}
```

Setor, fila e número só podem mudar quando o evento está em `DRAFT`.
Nos demais estados, envie a localização atual para atualizar preço e moeda.
Uma tentativa de mudar a localização retorna `409`, sem salvar nenhum campo.
Localização duplicada no mesmo evento ou conflito de atualização concorrente
também retornam `409`. ID, vínculo com evento e status do assento são preservados.
A resposta contém `id`, `eventId`, `section`, `row`, `number`, `price`,
`currency` e `status`.

A listagem segue a paginação de eventos, ordenada por ID e restrita ao evento
informado. Assento inexistente ou pertencente a outro evento retorna `404`.
Setor, fila e número exigem texto não vazio com até 100, 50 e 20 caracteres,
respectivamente. Preço e moeda seguem as validações do modelo abaixo.
Autenticação e CSRF seguem a configuração existente; erros usam Problem Details.

Cada atualização é transacional e consulta o evento sem bloqueio pessimista.
A regra de localização usa o status lido nessa consulta, sem serializar a escrita
com alterações simultâneas do evento. Edições administrativas concorrentes são
consideradas raras nesta etapa; a estratégia de controle de concorrência será
avaliada separadamente após reproduzir o cenário de lost update.
O `@Version` já existente no assento permanece. Não há endpoint de criação de assentos.

## Modelo

- `event/domain`: Event, Seat e seus estados; validações na criação.
- `event/application`: criação, consulta, listagem e atualização de eventos.
- `event/presentation`: endpoints, entrada JSON e tratamento de erros.
- `event/infrastructure`: repositories JPA, incluindo consulta de assentos por evento.
- `db/migration/`: schema e evoluções gerenciados por Flyway.

Eventos começam em `DRAFT`; assentos começam em `AVAILABLE`. Um assento pertence a
um evento existente, e sua combinação de setor, fila e número é única nesse evento.
Preços usam `NUMERIC(12,2)` e nunca são arredondados silenciosamente pelo domínio.
Cada assento exige `currency` explícita: um código ISO 4217 em maiúsculas, validado
pelo catálogo de moedas do JDK (por exemplo, `BRL`, `USD` ou `EUR`). A precisão
continua limitada a duas casas decimais; não há conversão automática de moedas.
No banco, a constraint valida presença e formato de três letras maiúsculas;
a validação de pertencimento ao catálogo ISO ocorre no domínio Java.
`Seat.version` é gerenciada por `@Version` (zero após persistir). O schema aceita
os estados documentados; as transições serão implementadas com os respectivos casos
de uso. `@Version` não substitui o UPDATE condicional planejado para reservas.

Hibernate apenas valida o schema. Migrations criam as tabelas e suas restrições.
Kafka não é necessário nesta fase.

### Identificadores, índice e auditoria

Novos Event e Seat recebem UUIDv7 via `uuid-creator`, com prefixo temporal para
melhor localidade das inserções nos índices B-tree. IDs anteriores são preservados.
UUID não substitui `ORDER BY` nem representa uma ordem global de commits.

A constraint `uk_seats_event_location` cria um índice B-tree único iniciado por
`event_id`, utilizável por `WHERE event_id = ?`. O teste de integração usa EXPLAIN
para verificar essa possibilidade. Não se mantém um segundo índice redundante
somente em `event_id`; sua necessidade deve ser demonstrada por carga real.

Event possui `created_at` e `updated_at` em UTC (`TIMESTAMP WITH TIME ZONE` / `Instant`).
Defaults do banco preenchem ambos na inserção, e Hibernate lê os valores gerados.
Cada query de alteração deve preservar `created_at` e atribuir explicitamente
`updated_at`, por exemplo `UPDATE events SET name = ?, updated_at = statement_timestamp() WHERE id = ?`.
Não há trigger de atualização nem garantia de monotonicidade. Uma query que omite
a atribuição mantém o timestamp anterior. Antes de persistir, os timestamps da
entidade são nulos. São metadados de criação e última alteração, não um histórico
completo nem um mecanismo de controle de concorrência.

A V2 preserva a V1: registros anteriores recebem timestamps da execução da migration
(o instante histórico não pode ser recuperado) e moeda `BRL`, conforme a suposição
explícita para os dados do draft. A migration remove o default de moeda em seguida,
obrigando novas escritas a informá-la. O upgrade com dados V1 tem teste próprio.
A V3 remove o trigger e a constraint de ordenação temporal introduzidos na V2,
preservando o histórico de migrations para bancos que já aplicaram aquela versão.

## Testes e cobertura

Com JDK 25 e Docker disponíveis:

```sh
./gradlew clean check
```

No Windows: `gradlew.bat clean check`. A suíte usa PostgreSQL real via Testcontainers;
Docker indisponível causa falha, sem ignorar testes de integração.

JaCoCo exige **80% de linhas e 80% de branches** no conjunto completo de classes de
produção, sem exclusões. `check` depende de `jacocoTestCoverageVerification` e
falha abaixo de qualquer limite. `test` gera os relatórios; execute `check` para
aplicar obrigatoriamente o gate.

- Testes: `build/reports/tests/test/index.html`
- Cobertura: `build/reports/jacoco/test/html/index.html`
- XML: `build/reports/jacoco/test/jacocoTestReport.xml`

O workflow `Core CI` executa o mesmo comando em pushes e pull requests, inclusive
drafts, e publica os relatórios como artifacts. Para impedir merge quando o gate
falha, configure `Tests and coverage (80%)` como check obrigatório nas regras de
proteção da branch `main` no GitHub.

O diretório local `core` é a raiz deste repositório Git: `.github/workflows/ci.yml`
fica na raiz esperada pelo GitHub. O plano da entrega está em
`docs/plans/event-seat.md`; os documentos de referência do projeto estão em `docs/`.
