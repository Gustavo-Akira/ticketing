# Separação de domínio e persistência — issue #6

Design aprovado na conversa, incluindo `event/port` e versão `Long` no domínio.

`Event` e `Seat` conservam regras, UUIDv7, estados e metadados como tipos Java.
Métodos `restore` reconstroem o estado persistido sem gerar novos IDs nem aplicar
os estados iniciais de criação. As anotações JPA/Hibernate ficam exclusivamente
em `EventJpaEntity` e `SeatJpaEntity`, em `event/infrastructure/persistence`.

As interfaces `EventRepository` e `SeatRepository` ficam em `event/port`.
Expõem apenas as operações necessárias e modelos de domínio; `PageResult<T>`
representa conteúdo e totais sem Spring Data. Os casos de uso mantêm suas
transações Spring e passam a depender dessas interfaces.

Adaptadores `JpaEventRepository` e `JpaSeatRepository` delegam consultas aos
repositórios Spring Data e fazem conversão explícita através das entidades JPA.
Escritas individuais retornam o estado após flush, inclusive auditoria e versão.
O update de evento mantém o SQL com `statement_timestamp()` e last-write-wins.
A criação em lote mantém o lock do evento até o término da transação.
O update de assento usa a versão lida originalmente, preservando conflitos
otimistas mesmo quando o domínio já não é uma entidade gerenciada.

Não há alteração de endpoints, respostas, regras de negócio ou migrations.
Testes existentes de API e PostgreSQL devem continuar passando. Testes novos
verificam isolamento de JPA, snapshots independentes e rejeição de versão antiga.
O gate existente permanece em 80% de linhas e branches, sem exclusões.
