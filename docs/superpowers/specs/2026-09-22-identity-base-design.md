# Base de identity

## Objetivo e escopo aprovado

Criar a base do módulo `identity` seguindo a organização por feature do projeto.
Uma mesma identidade pode acumular as roles `CUSTOMER`, `ORGANIZER` e `ADMIN`.
O trabalho será dividido em dois PRs:

1. Base: domínio de usuário, roles, porta de repositório, persistência, migration e testes.
2. Autenticação e autorização: cadastro, credenciais, login e integração com Spring Security.

Este documento especifica o primeiro PR. Não cria endpoints, contas administrativas,
senhas, tokens, hierarquia de permissões ou vínculos entre usuários e eventos.

## Padrões observados

- `Event` gera UUID temporal com `UuidCreator.getTimeOrderedEpoch()` e possui uma
  fábrica `restore` para reconstituir dados persistidos.
- Entidades JPA ficam em `infrastructure/persistence`, separadas do domínio.
- Portas usam tipos do domínio e da biblioteca padrão, sem tipos do Spring Data.
- Adaptadores fazem o mapeamento explícito com `fromDomain` e `toDomain`.
- Datas de auditoria são geradas pelo banco na inserção; atualizações são explícitas,
  sem triggers de auditoria.
- Testes de integração usam PostgreSQL real via Testcontainers e migrations Flyway.

## Organização

```text
identity/
  domain/
    User.java
    UserDetails.java
    Role.java
  port/
    UserRepository.java
  infrastructure/persistence/
    UserJpaEntity.java
    SpringDataUserRepository.java
    JpaUserRepository.java
```

`application` e `presentation` serão introduzidos quando houver casos de uso e
endpoints no segundo PR. Não serão criadas classes ou pastas vazias.

## Modelo e invariantes

`User` contém `id`, `name`, `email`, `roles`, `createdAt` e `updatedAt`.

- ID gerado na construção, usando o mesmo gerador do módulo de eventos.
- Nome obrigatório, não vazio, com até 255 caracteres.
- E-mail obrigatório, com até 254 caracteres; remover espaços externos e converter
  para minúsculas com `Locale.ROOT` antes de armazenar e consultar.
- Validar formato básico do e-mail no domínio: uma parte local e um domínio não
  vazios separados por um único `@`, sem espaços. Não exigir consulta DNS nem
  implementar toda a especificação de endereços de e-mail.
- Roles obrigatórias, com pelo menos um elemento e sem elementos nulos.
- Armazenar e expor cópia imutável das roles para impedir alteração externa.
- `Role` é um enum com exatamente `CUSTOMER`, `ORGANIZER` e `ADMIN`.
- As roles são explícitas e independentes: `ADMIN` não implica outras roles.
- `UserDetails` concentra a validação dos dados de criação e reconstituição.
- `restore` preserva ID e datas e valida campos obrigatórios.
- Antes da persistência, as datas permanecem sem valor, como em `Event`.

O primeiro PR não expõe operações para alterar perfil ou conceder/revogar roles.
A atribuição de roles na construção é uma operação interna de domínio, não uma
permissão de autocadastro. A política de atribuição pública pertence ao segundo PR.

## Persistência

Adicionar `V4__create_users_and_user_roles.sql`, sem alterar migrations existentes.

Tabela `users`:

- `id UUID PRIMARY KEY`;
- `name VARCHAR(255) NOT NULL`, com check para nome não vazio;
- `email VARCHAR(254) NOT NULL`, com checks de normalização e formato básico,
  e constraint de unicidade;
- `created_at` e `updated_at` como `TIMESTAMPTZ NOT NULL`, com valor inicial
  `statement_timestamp()`.

Tabela `user_roles`:

- `user_id UUID NOT NULL`, referenciando `users(id)`;
- `role VARCHAR(20) NOT NULL`, limitado aos três valores do enum por check;
- chave primária composta por `(user_id, role)`;
- exclusão em cascata das associações quando um usuário for excluído.

Persistir roles por `@ElementCollection` e `EnumType.STRING`, sem uma entidade
independente para cada perfil. A exigência de ao menos uma role é garantida pelo
domínio; não haverá trigger para impor cardinalidade mínima entre tabelas.

`UserRepository` oferece `save(User)`, `findById(UUID)` e `findByEmail(String)`.
Consultas ausentes retornam `Optional.empty()`. A consulta por e-mail aplica a
mesma normalização utilizada na criação.

O adaptador salva usuário e roles na mesma transação e devolve um snapshot com
as datas geradas pelo banco. O mapeamento das coleções ocorre dentro da transação,
para não depender de Open Session in View nem de transações abertas pelo chamador.
A unicidade é garantida pelo banco inclusive em inserções concorrentes; uma
consulta prévia não substitui essa constraint.

## Alternativas consideradas

- Um usuário com várias roles: escolhido, pois mantém a mesma identidade para
  quem organiza eventos e também compra ingressos.
- Entidades separadas para Customer, Organizer e Admin: rejeitadas neste escopo,
  pois não existem atributos específicos de perfil que justifiquem duplicação.
- Uma única role por usuário: não atende ao requisito confirmado.

## Validação e critérios de aceite

- Testes unitários para campos inválidos, normalização de e-mail, múltiplas roles,
  coleção imutável, geração de ID e reconstituição sem regeneração de identidade.
- Teste de isolamento do domínio em relação a Spring e JPA, conforme a abordagem
  já usada pelo projeto.
- Testes de persistência para round-trip completo, todas as roles, consultas por
  ID/e-mail, ausência de usuário e auditoria gerada pelo banco.
- Testar consulta e conversão de roles sem uma transação externa ao adaptador.
- Testar rejeição de e-mails duplicados, inclusive diferenças de caixa e espaços
  externos nas entradas de domínio.
- Testar constraints do banco por SQL direto: role inválida, associação duplicada,
  usuário inexistente e dados obrigatórios inválidos.
- Confirmar atomicidade: uma falha de persistência não deixa usuário parcial.
- Executar `gradlew.bat check`, incluindo a suíte existente e a verificação de
  cobertura configurada em 80% para linhas e branches.

## Continuidade no segundo PR

O segundo PR parte desta base para definir cadastro, credenciais, mecanismo de
autenticação e autorização. Fluxos de atribuição de Organizer/Admin e gestão de
contas serão detalhados nessa etapa. A base não altera a configuração de segurança
nem o comportamento atual das APIs de eventos e assentos.
