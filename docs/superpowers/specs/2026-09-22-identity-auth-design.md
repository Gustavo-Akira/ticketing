# Identity: autenticação e autorização

## Decisões aprovadas

Segundo PR de Identity, sobre a base integrada no PR #12. O usuário aprovou:

- Cadastro público exclusivamente com `CUSTOMER`.
- Autenticação com JWT de acesso e refresh token, com renovação e logout.
- Concessão de `ORGANIZER` exclusivamente por `ADMIN`, preservando outras roles.
- Primeiro ADMIN criado por comando administrativo no servidor.
- Autorização de eventos por role; propriedade de eventos será outro PR.
- JWT com duração de 15 minutos; sessão de renovação com limite de 7 dias.
- Tokens transportados em JSON; acesso à API por `Authorization: Bearer`.
- Logout revoga renovação; JWT já emitido permanece válido até expirar.

Os detalhes técnicos abaixo concretizam essas decisões para revisão antes do plano.

## Arquitetura e alternativas

Manter o monólito organizado por feature. `identity/domain` permanece independente
de Spring e JPA. Casos de uso em `identity/application` dependem de portas para
credenciais, sessões, hashing de senha e emissão de JWT. Adaptadores em
`identity/infrastructure` implementam persistência e segurança; HTTP e comando
administrativo são entradas distintas para os casos de uso.

Escolha: emitir JWT localmente com `JwtEncoder` e validar com o Resource Server
do Spring Security. Evita um filtro JWT próprio e acompanha as abstrações do
framework. Um servidor OAuth/OIDC separado adicionaria protocolos e operação que
este PR não exige. Refresh JWT ainda exigiria persistência para rotação e revogação;
por isso o refresh será um segredo opaco aleatório.

## Contrato HTTP

| Método e rota | Entrada | Resultado |
| --- | --- | --- |
| `POST /auth/register` | `name`, `email`, `password` | `201`, perfil sem credenciais; não inicia sessão |
| `POST /auth/login` | `email`, `password` | `200`, par de tokens |
| `POST /auth/refresh` | `refreshToken` | `200`, novo par de tokens |
| `POST /auth/logout` | `refreshToken` | `204`, sessão de renovação revogada |
| `PUT /users/{id}/roles/organizer` | Sem corpo; JWT com `ADMIN` | `204`, concessão idempotente |

O perfil retorna `id`, `name`, `email`, `roles`, `createdAt`, `updatedAt`.
O par retorna `accessToken`, `tokenType: "Bearer"`, `expiresIn: 900`,
`refreshToken` e `refreshExpiresAt`. Respostas com tokens usam `Cache-Control: no-store`.
Cadastro não aceita atribuição de roles; campo `roles` enviado pelo cliente deve
ser rejeitado com `400`. Não há endpoint de concessão de ADMIN.

Erros seguem Problem Details: entrada inválida `400`, credenciais ou refresh
inválidos `401`, role insuficiente `403`, usuário alvo inexistente `404`, e-mail
duplicado no cadastro `409`. Login usa a mesma mensagem para e-mail inexistente,
usuário sem credencial e senha incorreta. Logout com token bem formado desconhecido,
expirado ou já revogado também retorna `204`; corpo ausente ou inválido retorna `400`.
Tokens e senhas nunca aparecem nas respostas de erro ou logs.

## Credenciais e cadastro

Preservar normalização e validação de nome/e-mail do domínio existente. Senha não
é normalizada nem aparada: de 12 a 64 caracteres Unicode e no máximo 72 bytes UTF-8,
sem regras de composição. Usar BCrypt com custo 12 e prefixo `{bcrypt}` para futura
migração de algoritmo. O limite em bytes evita truncamento pelo BCrypt.

Persistir credencial separadamente em `user_credentials`, com `user_id` como PK/FK
para `users` e `password_hash` obrigatório. Usuários existentes não recebem senha
inventada e não conseguem fazer login enquanto não tiverem credencial; recuperação
e migração de contas ficam fora deste PR.

Cadastro grava usuário, CUSTOMER e credencial atomicamente. A constraint de e-mail
do PostgreSQL continua sendo a garantia contra duplicidade concorrente. Converter
somente a violação específica dessa constraint em `409`. No login inexistente,
executar verificação contra hash fictício pré-calculado para evitar retorno imediato.

## JWT e configuração

Assinar com RS256, usando chave privada e pública PEM externas ao repositório.
Validar algoritmo, assinatura, issuer, audience, subject UUID e expiração.
Claims: `sub` (ID), `iss`, `aud`, `iat`, `exp`, `jti` e `roles`; não incluir senha,
hash, refresh token ou dados de perfil. Converter roles explicitamente para as
authorities do Spring Security. Não existe hierarquia implícita de roles.

Configurar caminhos das chaves, issuer e audience por propriedades externas.
Documentar geração de chaves para desenvolvimento e montagem no Compose; ignorar
chaves locais no Git. A aplicação HTTP falha ao iniciar se as chaves/configuração
obrigatórias forem inválidas. Testes usam par de chaves próprio.

API sem sessão HTTP, form login, Basic ou autenticação por cookie. CSRF desabilitado
para este contrato exclusivamente Bearer/JSON; nenhuma política CORS permissiva
será adicionada. O transporte em produção requer HTTPS.

## Sessões e rotação de refresh

Gerar 32 bytes com `SecureRandom` e codificar em Base64 URL sem padding. Persistir
somente SHA-256 do token, nunca o segredo. Cada login cria uma sessão independente
com limite absoluto de 7 dias; renovar não estende esse limite.

Criar `refresh_sessions` (`id`, `user_id`, `created_at`, `expires_at`, `revoked_at`)
e `refresh_tokens` (`id`, `session_id`, `token_hash`, `created_at`, `consumed_at`).
IDs são UUID; datas usam TIMESTAMPTZ. Hash é único; FKs têm índices para consultas
por usuário/sessão e exclusão em cascata. Preservar tokens consumidos durante a
validade da sessão para detectar reutilização. Limpeza periódica fica fora do PR.

Renovação resolve o hash, bloqueia a sessão com `SELECT FOR UPDATE` e relê o token
dentro da transação. Se sessão válida e token não consumido, marca consumo e grava
sucessor atomicamente. Emite JWT com roles atuais do usuário. Se token já consumido
for reutilizado, revoga toda aquela sessão, inclusive o sucessor.

A revogação por reutilização precisa ser confirmada no banco antes de produzir
o `401`: usar resultado de aplicação para representar rejeição após commit, sem
lançar exceção que desfaça a revogação dentro da transação.

Duas renovações concorrentes do mesmo token produzem no máximo uma rotação; a
segunda detecta reutilização e revoga a sessão. Clientes não devem renovar em
paralelo. JWT eventualmente emitido pela primeira continua válido por até 15 minutos.
Tokens inválidos, sessões expiradas/revogadas e usuários ausentes recebem `401`.

Logout localiza inclusive um token consumido e revoga sua sessão usando o mesmo
bloqueio da renovação. Outras sessões do usuário permanecem válidas. Nenhuma
operação de logout exige JWT de acesso ainda válido.

## Autorização

| Operação | Permissão |
| --- | --- |
| Cadastro, login, refresh, logout | Sem exigir access token |
| GETs existentes de eventos e assentos | Qualquer usuário autenticado |
| POST, PUT e PATCH existentes de eventos e assentos | `ORGANIZER` |
| Conceder ORGANIZER | `ADMIN` |
| Rotas não previstas | Negar por padrão |

Abranger criação de eventos, atualização, publicação (`status/available`), criação
em lote de assentos (`create-seats`) e atualização de assentos. ADMIN sozinho não
pode escrever eventos; pode conceder ORGANIZER ao próprio usuário e renovar/login
para obter um JWT com a role. Concessão usa inserção idempotente na associação,
sem substituir o conjunto de roles de um snapshot antigo, e atualiza auditoria
somente quando houver mudança. Roles novas aparecem no próximo JWT emitido.

ORGANIZER pode gerenciar qualquer evento neste PR. Não adicionar `owner_id`, regras
de propriedade ou restrições às consultas baseadas em proprietário/status.

## Comando administrativo

Entrada proposta: `java -jar app.jar identity create-admin --name <nome> --email <email>`.
Selecionar execução sem servidor HTTP antes de iniciar o contexto Spring. O modo
administrativo carrega banco e hashing, sem exigir chaves JWT ou abrir a porta 8080.
Sem argumentos de comando, iniciar normalmente a aplicação HTTP.

Ler e confirmar senha com `Console.readPassword`; ausência de console interativo
causa falha explícita, sem fallback para argumentos, logs ou entrada com eco.
Limpar buffers de caracteres após uso. Criar usuário com somente ADMIN e credencial
na mesma transação. E-mail existente causa erro sem modificar senha ou roles.
Não promover contas existentes silenciosamente. Retornar código zero no sucesso
e não zero em erro, encerrando o contexto. Documentar uso via terminal interativo
e `docker compose exec` com TTY.

## Persistência e compatibilidade

Adicionar `V5__create_identity_credentials_and_refresh_sessions.sql`; não modificar
V1 a V4. Não exigir credencial para cada registro legado em `users`. Datas de
inserção continuam com defaults do banco; atualizações são explícitas, sem triggers.
Usar Clock injetável para expiração e testes determinísticos.

Manter Java 25, Spring Boot e PostgreSQL existentes. Acrescentar suporte oficial
OAuth2 Resource Server/Jose compatível com o BOM do projeto. Nenhum Redis ou serviço
externo é necessário. Adaptar testes HTTP existentes ao novo contrato de segurança.

## Validação

- Domínio/entrada: normalização de e-mail, limites de senha incluindo Unicode,
  rejeição de roles no cadastro e ausência de dados sensíveis em retornos.
- PostgreSQL real: cadastro atômico e duplicado concorrente; constraints; upgrade
  preservando usuários legados; hashes em vez de segredos persistidos.
- Login/JWT: credenciais incorretas e ausentes; assinatura, algoritmo, issuer,
  audience e expiração inválidos; roles independentes; JWT real no caminho HTTP.
- Refresh: rotação, limite absoluto, reutilização, revogação persistida após `401`,
  concorrência, isolamento entre sessões e disputa refresh/logout.
- Autorização: `401` sem autenticação, `403` sem role, sucesso com role correta
  em cada endpoint existente, incluindo publicação e criação em lote de assentos.
- Concessão: idempotência, preservação de roles concorrentes, alvo ausente e
  role visível somente em token novo.
- CLI: criação ADMIN, duplicidade sem alteração, falha de confirmação/console,
  código de saída e execução sem HTTP nem configuração JWT.
- Executar `gradlew.bat check`: suíte completa e cobertura mínima de 80% de linhas
  e branches, sem exclusões novas. Docker obrigatório para Testcontainers.

## Fora do escopo

Propriedade de eventos, recuperação/troca de senha, verificação de e-mail, MFA,
login social, gestão/revogação de roles, logout global, revogação imediata de JWT,
interface frontend e servidor OAuth/OIDC completo.

## Referências

- [Base de Identity](2026-09-22-identity-base-design.md)
- [Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [RFC 9700, rotação e revogação](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)
