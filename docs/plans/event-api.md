# Event use cases e API

## Escopo aprovado

Criar, consultar por ID, listar e atualizar eventos. Publicação e cancelamento
ficam para uma entrega futura. A implementação usa a organização existente por
feature e mantém a segurança padrão do bootstrap.

## Contrato

| Operação | Endpoint | Resultado |
| --- | --- | --- |
| Criar | POST /events | 201, Location e evento DRAFT |
| Consultar | GET /events/{id} | 200 ou 404 |
| Listar | GET /events?page=0&size=20 | 200, página ordenada por ID |
| Atualizar | PUT /events/{id} | 200 ou 404; substitui nome e data |

Nome obrigatório com até 255 caracteres e startsAt obrigatório como Instant.
Página não negativa; tamanho de 1 a 100; offset até Integer.MAX_VALUE.
Entrada inválida retorna 400 com
ProblemDetail. Respostas expõem ID, nome, data, status e timestamps.
Status, ID e timestamps não são editáveis pelo cliente. Updates preservam
created_at e atribuem updated_at = statement_timestamp() explicitamente.

## Implementação e validação

- [x] Escrever EventApiTest com MockMvc e PostgreSQL real: criação e Location,
  consulta, atualização persistida, auditoria, paginação, erros e segurança.
- [x] Executar `gradlew.bat test --tests '*EventApiTest' --no-daemon` e confirmar
  falha pela ausência dos endpoints.
- [x] Criar quatro classes em event/application: CreateEventUseCase,
  GetEventUseCase, ListEventsUseCase e UpdateEventUseCase; usar transações nas
  escritas e consultas read-only. Centralizar resultado em EventResult.
- [x] Compartilhar validação de nome/data no domínio e adicionar update explícito
  ao EventRepository, sem setter de estado ou migration adicional.
- [x] Criar EventController, DTO de entrada e advice de erros em
  event/presentation. Manter o contrato HTTP separado da entidade JPA.
- [x] Executar a suíte direcionada, depois `gradlew.bat clean check --no-daemon`.
  Cobertura mínima existente: 80% de linhas e branches, sem exclusões.
- [x] Atualizar README e roadmap com esta etapa e os use cases futuros.

## Limites

Não inclui filtros, remoção, endpoints de Seat ou transições de estado.
Atualizações concorrentes mantêm last-write-wins; auditoria não é controle de
concorrência. Autenticação/autorização definitiva continua fora desta entrega.
