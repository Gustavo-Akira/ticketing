# ADR-0007: Optimistic Concurrency for Seat Metadata Updates

## Status

Accepted

## Context

A issue [#4](https://github.com/Gustavo-Akira/ticketing/issues/4) trata de edições
administrativas concorrentes de setor, fila, número, preço e moeda de um assento.

SeatJpaEntity já possui @Version, e o adaptador preserva a versão ao converter
entre entidade e domínio. Isso protege a gravação de snapshots concorrentes.
Entretanto, o PUT carregava um snapshot novo e aplicava todos os campos enviados,
sem conhecer a versão consultada pelo cliente.

Exemplo: A e B consultam preço 100 e fila A. A salva preço 150. Depois do commit,
B envia seu formulário antigo com fila B e preço 100. O backend carrega a versão
mais recente, aplica o formulário de B e restaura silenciosamente o preço 100.
Não é necessário sobrepor as transações para reproduzir essa perda.

## Decision

Manter o optimistic locking JPA existente e transportar a precondição de versão
no contrato JSON:

- GET individual, listagem e resposta do PUT expõem version.
- PUT exige expectedVersion, um Long não negativo correspondente à versão lida.
- expectedVersion ausente, nulo ou negativo retorna 400, sem escrita.
- O campo aceita apenas um número inteiro JSON dentro do intervalo de Long;
  decimais, notação exponencial, strings, booleanos e overflow retornam 400.
  A desserialização estrita é local ao campo, sem alterar o mapper global.
- Após localizar o assento no evento informado, o caso de uso compara a versão
  esperada com a versão carregada. Qualquer divergência retorna 409.
- A comparação ocorre antes de alterar os metadados. A versão da entidade não é
  substituída pelo valor recebido do cliente.
- O save preserva o @Version e o saveAndFlush existentes. Uma disputa entre a
  leitura/comparação e a gravação também retorna 409, com rollback da transação.
- Ambos os conflitos de concorrência retornam Problem Details orientando o
  cliente a reler o assento antes de editar.
- A resposta de sucesso usa a versão retornada pela persistência. Não se presume
  que um PUT sem mudanças efetivas incremente a versão.
- Não há retry automático nem merge silencioso. O cliente deve reler, revisar
  suas alterações e enviar uma nova edição com a versão atual.

A comparação no caso de uso e o @Version são complementares: a primeira detecta
o formulário obsoleto; o segundo protege a janela entre a comparação e o commit.
A coluna version já existe, portanto esta decisão não exige migration.

## Alternatives

| Alternativa | Avaliação |
| --- | --- |
| Apenas @Version | Já protege snapshots concorrentes na persistência, mas não identifica a versão originalmente consultada pelo cliente. |
| UPDATE condicional por expectedVersion | Pode implementar a mesma garantia atômica com WHERE version = :expectedVersion e incremento da versão. Não foi escolhido porque o fluxo JPA já oferece essa proteção e preserva as regras de domínio existentes. |
| ETag / If-Match | É uma alternativa válida para transportar a precondição no HTTP. Exigiria definir o contrato de headers e retornar 412 quando a precondição falhar; ainda precisaria da proteção atômica na persistência. O JSON mantém a alteração próxima do contrato atual. |
| Lock pessimista | Pode serializar transações, com espera e contenção adicionais. Sozinho não identifica um formulário antigo enviado após o commit de outra edição. Não é necessário para a baixa concorrência administrativa esperada. |

## Scope: reservation concurrency is deferred

**Esta ADR cobre exclusivamente a edição administrativa dos metadados de Seat.
A race condition do fluxo de reservation será implementada e validada em outro
momento, na etapa de reservas. Esta entrega não resolve nem certifica a disputa
de dois compradores pelo mesmo assento.**

A [ADR-0002](0002-atomic-seat-reservation.md) continua sendo a decisão de referência
para a futura reserva atômica: UPDATE condicional por disponibilidade, verificação
da quantidade de linhas alteradas e rollback quando não for possível reservar
todos os assentos solicitados. O expectedVersion deste PUT não substitui essa
operação nem implementa transições AVAILABLE -> RESERVED.

Quando reservas ou outros fluxos passarem a escrever diretamente na tabela seats,
eles deverão manter a consistência da versão (como o incremento já previsto na
ADR-0002). A integração entre reservas e edição administrativa, incluindo seus
testes de concorrência, também pertence àquela entrega futura.

A disputa entre publicação do evento e alteração da localização do assento não
é resolvida por esta ADR. A validação de localização continua usando o status do
evento lido pela transação, sem serializar ambas as operações.

## Validation

- SeatApiTest.staleAdministrativeEditCannotOverwriteCommittedPriceChange:
  dois leitores usam o mesmo estado; uma edição salva o preço; a edição com
  formulário antigo deve retornar 409 e preservar integralmente o primeiro commit.
  Antes da correção, o teste falhou porque o segundo PUT retornou 200.
- Testes de API verificam versão ausente, nula, negativa, fracionária e outros
  formatos inválidos ou fora do intervalo de Long (400), versão futura
  (409), exposição de version nas consultas e atualização após releitura.
- SeatConcurrencyApiTest sincroniza duas requisições após as leituras reais da
  mesma versão, em transações independentes no PostgreSQL. Exige um sucesso,
  um 409 e somente os dados da edição vencedora no banco. Usa uma barreira com
  timeout, sem sleeps para induzir a disputa.
- O teste existente de snapshot obsoleto continua cobrindo a preservação da
  versão na conversão entre domínio e entidade JPA.

## Consequences

### Positive

- Evita sobrescrita silenciosa tanto por formulário antigo quanto por disputa
  durante a gravação de alterações.
- Reutiliza a coluna e o mecanismo de versionamento já existentes.
- Mantém o domínio independente de JPA e os limites transacionais atuais.

### Negative

- Altera o contrato do PUT: clientes antigos sem expectedVersion passam a receber
  400 e devem ser atualizados junto com a API.
- Os clientes precisam tratar 409 com releitura e reconciliação explícita.
- A granularidade é o assento inteiro: edições em campos diferentes também podem
  conflitar. Não há merge automático por campo.
- Outros caminhos de escrita deverão respeitar o versionamento.

## References

- [Jakarta Persistence 3.2: optimistic locking](https://jakarta.ee/specifications/persistence/3.2/jakarta-persistence-spec-3.2)
- [RFC 9110: If-Match](https://www.rfc-editor.org/rfc/rfc9110.html#section-13.1.1)
