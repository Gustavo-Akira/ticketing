# Ticketing Core

Persistência de eventos, assentos e usuários, com API de eventos em um monólito Spring Boot,
organizado por feature conforme ADR-0001.

## Executar

Requisitos: Docker com Compose e OpenSSL para gerar as chaves locais. A partir desta pasta,
gere uma vez as chaves de desenvolvimento (diretório ignorado pelo Git):

```sh
mkdir -p .local/identity
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out .local/identity/private.pem
openssl pkey -in .local/identity/private.pem -pubout -out .local/identity/public.pem
```

No PowerShell, crie o diretório com `New-Item -ItemType Directory -Force .local/identity`.
Proteja a chave privada e permita sua leitura pelo usuário `ticketing` do container.
O Compose monta as chaves somente para leitura. Em produção, use chaves próprias,
issuer/audience do ambiente e HTTPS. Em seguida:

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

Fora do Compose, configure `IDENTITY_JWT_PRIVATE_KEY` e `IDENTITY_JWT_PUBLIC_KEY`
com recursos `file:/caminho/arquivo.pem`, além de `IDENTITY_JWT_ISSUER` e
`IDENTITY_JWT_AUDIENCE`. Chaves inválidas/ausentes impedem iniciar o servidor HTTP.
O comando administrativo descrito abaixo usa somente a configuração de banco.
As chaves em `src/test/resources/identity` são fixtures públicas exclusivas dos testes
e não devem ser utilizadas para executar a aplicação.

Spring Security usa JWT RS256 via `Authorization: Bearer <accessToken>`, sem Basic,
form login ou sessão HTTP. Não existe usuário padrão. Como a autenticação não usa
cookies, esta API não exige token CSRF. Consultas exigem autenticação; escritas em
eventos e assentos exigem `ORGANIZER`.

## Identity e autenticação

O módulo `identity` contém o domínio de usuário e sua persistência. Cada usuário
possui UUID temporal, nome, e-mail único, datas de auditoria e uma ou mais roles:
`CUSTOMER`, `ORGANIZER` e `ADMIN`. As roles são independentes: um organizador pode
também ser cliente, e `ADMIN` não adiciona outras roles automaticamente.

O e-mail é armazenado em minúsculas, sem espaços externos. A unicidade é garantida
pelo PostgreSQL, inclusive em gravações concorrentes. Usuário e roles são gravados
na mesma transação nas tabelas `users` e `user_roles`, criadas pela migration V4.
O repositório permite salvar e consultar por ID ou e-mail, retornando snapshots
de domínio com roles imutáveis e auditoria gerada pelo banco.

O cadastro público cria somente CUSTOMER. ADMIN concede ORGANIZER de forma
idempotente e preserva as roles anteriores. ADMIN sozinho não pode escrever eventos;
pode conceder ORGANIZER ao próprio usuário. Propriedade de eventos será outro PR:
nesta entrega, um ORGANIZER pode gerenciar qualquer evento.

| Método e rota | Entrada JSON | Resultado |
| --- | --- | --- |
| `POST /auth/register` | `name`, `email`, `password` | `201`, perfil sem credenciais |
| `POST /auth/login` | `email`, `password` | `200`, accessToken e refreshToken |
| `POST /auth/refresh` | `refreshToken` | `200`, novo par de tokens |
| `POST /auth/logout` | `refreshToken` | `204`, revoga a sessão informada |
| `PUT /users/{id}/roles/organizer` | Sem corpo, Bearer ADMIN | `204`, concede ORGANIZER |

Cadastro não inicia sessão; envie os dados ao login após cadastrar. Campos devem
ser strings; campos desconhecidos (incluindo roles) são rejeitados. A senha possui
12 a 64 caracteres e até 72 bytes UTF-8, sem trim ou regras de composição. Apenas
seu hash BCrypt custo 12 é persistido, separado do perfil.

```sh
curl -X POST http://localhost:8080/auth/register -H 'Content-Type: application/json' \
  -d '{"name":"Ana","email":"ana@example.com","password":"uma senha de exemplo"}'
curl -X POST http://localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"ana@example.com","password":"uma senha de exemplo"}'
curl http://localhost:8080/events -H 'Authorization: Bearer <accessToken>'
curl -X POST http://localhost:8080/auth/refresh -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'
curl -X POST http://localhost:8080/auth/logout -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<refreshToken>"}'
curl -X PUT 'http://localhost:8080/users/<id>/roles/organizer' -H 'Authorization: Bearer <adminAccessToken>'
```

O par contém `accessToken`, `tokenType: "Bearer"`, `expiresIn: 900`, `refreshToken`
e `refreshExpiresAt`; respostas com tokens usam `Cache-Control: no-store`.
JWT dura 15 minutos. A sessão de renovação dura no máximo 7 dias desde o login.
Cada renovação substitui o refresh, sem estender esse limite. O banco guarda somente
SHA-256 do segredo aleatório de 32 bytes. Não faça renovações paralelas nem repita
automaticamente o refresh antigo: reutilização revoga toda a cadeia daquela sessão.
Um novo login cria uma sessão independente.

Logout pode usar refresh já consumido e é idempotente; não exige access token válido.
JWTs emitidos continuam válidos até expirar, inclusive após logout/reutilização.
Roles concedidas aparecem somente no próximo JWT, obtido por login ou renovação.
Usuários legados sem credenciais não podem fazer login; este PR não cria senhas
para eles. Recuperação de senha, verificação de e-mail e limpeza periódica de sessões
expiradas ficam fora deste escopo.

Erros usam Problem Details: entrada inválida `400`, login/refresh inválido `401`,
role insuficiente `403`, usuário alvo ausente `404` e e-mail duplicado `409`.

### Criar o primeiro ADMIN

No servidor, com as variáveis de banco configuradas, execute em terminal interativo:

```sh
java -jar build/libs/core-0.0.1-SNAPSHOT.jar identity create-admin --name Admin --email admin@example.com
```

Com o serviço Compose em execução:

```sh
docker compose exec app java -jar app.jar identity create-admin --name Admin --email admin@example.com
```

Para criar o ADMIN antes de iniciar o serviço HTTP, suba apenas `postgres` e use
`docker compose run --rm app identity create-admin --name Admin --email admin@example.com`
em terminal com TTY. O comando não abre servidor HTTP nem carrega chaves JWT.
Ele solicita e confirma a senha sem eco, cria somente ADMIN e encerra. Sem console,
com senha inválida ou e-mail existente, falha sem promover/resetar a conta. Não aceita
senha por argumento. Código de saída: `0` sucesso, `2` entrada/conta inválida,
`1` falha de execução/inicialização. Depois, faça login pela API.

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
e autorização retornam `401` e `403` pelo Spring Security.

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
  "currency": "BRL",
  "expectedVersion": 0
}
```

Setor, fila e número só podem mudar quando o evento está em `DRAFT`.
Nos demais estados, envie a localização atual para atualizar preço e moeda.
Uma tentativa de mudar a localização retorna `409`, sem salvar nenhum campo.
Localização duplicada no mesmo evento ou conflito de atualização concorrente
também retornam `409`. ID, vínculo com evento e status do assento são preservados.
A resposta contém `id`, `eventId`, `section`, `row`, `number`, `price`,
`currency`, `status` e `version` (também na listagem).

A listagem segue a paginação de eventos, ordenada por ID e restrita ao evento
informado. Assento inexistente ou pertencente a outro evento retorna `404`.
Setor, fila e número exigem texto não vazio com até 100, 50 e 20 caracteres,
respectivamente. Preço e moeda seguem as validações do modelo abaixo.
Consultas exigem JWT; escritas exigem ORGANIZER; erros usam Problem Details.

O cliente deve enviar em `expectedVersion` a `version` recebida na consulta do
assento, como número inteiro JSON não negativo dentro do intervalo de `Long`.
Versão ausente, nula, negativa ou em formato inválido retorna `400`; versão divergente ou
disputa durante a gravação retorna `409`, sem sobrescrever a edição vencedora.
Após um conflito, releia o assento, revise a edição e envie a versão atual;
não repita automaticamente o formulário antigo. Clientes anteriores precisam
passar a enviar esse campo obrigatório.

Cada atualização é transacional, mantém o `@Version` na persistência e consulta
o evento sem bloqueio pessimista. A regra de localização usa o status lido nessa
consulta, sem serializar a escrita com alterações simultâneas do evento.
A [ADR-0007](docs/adr/0007-seat-metadata-optimistic-concurrency.md) documenta a
proteção de metadados. A race condition de reservation será tratada em uma etapa
futura, conforme a ADR-0002; a proteção deste PUT não implementa reserva atômica.

## Modelo

- `event/domain`: Event, Seat e seus estados; validações na criação.
- `event/application`: criação, consulta, listagem e atualização de eventos.
- `event/presentation`: endpoints, entrada JSON e tratamento de erros.
- event/port: contratos de repositório e paginação independentes de Spring Data.
- event/infrastructure/persistence: entidades JPA, repositories Spring Data e
  adaptadores que convertem explicitamente entre persistência e domínio.
- `db/migration/`: schema e evoluções gerenciados por Flyway.

Eventos começam em `DRAFT`; assentos começam em `AVAILABLE`. Um assento pertence a
um evento existente, e sua combinação de setor, fila e número é única nesse evento.
Preços usam `NUMERIC(12,2)` e nunca são arredondados silenciosamente pelo domínio.
Cada assento exige `currency` explícita: um código ISO 4217 em maiúsculas, validado
pelo catálogo de moedas do JDK (por exemplo, `BRL`, `USD` ou `EUR`). A precisão
continua limitada a duas casas decimais; não há conversão automática de moedas.
No banco, a constraint valida presença e formato de três letras maiúsculas;
a validação de pertencimento ao catálogo ISO ocorre no domínio Java.
Seat.version é um Long no domínio. O @Version de SeatJpaEntity gerencia a versão no banco (zero após persistir). O schema aceita
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
Defaults do banco preenchem ambos na inserção; o adaptador JPA lê os valores gerados e retorna um novo snapshot de domínio.
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

### Separação de domínio e persistência

Event e Seat não possuem anotações JPA ou tipos Hibernate. Os casos de uso
dependem de event/port; PageResult<T> transporta conteúdo e totais, e os
adaptadores mantêm a ordenação por ID. Transações continuam nos casos de uso.

Cada leitura retorna um snapshot independente. Alterar um Seat não dispara
dirty checking: é necessário chamar SeatRepository.save. O retorno de save
contém a versão persistida; o snapshot recebido não é atualizado implicitamente.
O adaptador preserva a versão original ao salvar, rejeitando snapshots obsoletos.
O mapeamento de leitura usa restore, preservando IDs, estados e metadados.

A atualização de evento conserva seu SQL explícito de auditoria. A criação de
assentos em lote mantém o bloqueio do evento até o fim da transação.
