# Event e Identity: propriedade de eventos

## Objetivo e decisões confirmadas

Vincular cada novo evento ao organizador autenticado que o criou. Somente esse
organizador pode editar o evento, publicá-lo e gerenciar seus assentos. O usuário
confirmou esse escopo e informou que os eventos existentes são dados de
desenvolvimento, sem necessidade de preservá-los como registros editáveis.

As decisões detalhadas abaixo são a proposta para revisão antes da implementação.

## Abordagem

Manter `ownerId` como UUID em Event e receber o ID do ator nos casos de uso de
escrita. A entrada HTTP extrai o UUID do subject do JWT já validado pelo Resource
Server. A autorização por ORGANIZER continua na configuração de segurança;
a aplicação verifica a propriedade antes de executar qualquer alteração.

Alternativas consideradas:

- Associação JPA entre Event e User: facilita navegação, mas acopla os modelos
  de persistência das features sem necessidade para esta regra.
- Verificação apenas nos controllers: exige menos mudanças de assinatura, mas
  deixa chamadas diretas aos casos de uso sem a regra de propriedade.
- UUID e validação na aplicação, abordagem escolhida: preserva as fronteiras
  existentes e permite testar a regra sem infraestrutura HTTP.

Não é necessário consultar Identity em cada alteração: o JWT identifica o ator,
e Event armazena o proprietário. As roles seguem a validade do token existente.

## Modelo e persistência

Adicionar `ownerId` ao domínio, ao mapeamento JPA e ao resultado de evento.
Novos eventos exigem UUID não nulo no construtor. Não haverá operação de
transferência de propriedade. O campo JPA será não atualizável; as queries de
atualização existentes continuarão alterando apenas seus campos específicos.

Criar `V6__add_event_owner.sql`, preservando V1 a V5, com `events.owner_id` UUID,
FK para `users(id)` sem exclusão em cascata e índice para a coluna.

Proposta de compatibilidade: permitir NULL no banco somente para acomodar os
eventos legados, sem inventar organizador, apagar dados ou criar usuário fictício.
A reconstituição aceita esses registros; a criação pela aplicação exige dono.
Eventos legados permanecem consultáveis, mas qualquer tentativa de gerenciá-los
recebe 403. Não haverá endpoint para assumir esses eventos. O teste de upgrade
deve demonstrar preservação de eventos e assentos e bloqueio de escrita.

A FK garante existência do usuário; a role ORGANIZER é uma regra de autorização,
não uma propriedade da FK. Não acrescentar dependência do domínio Event em User.

## Fluxo e contrato HTTP

No POST /events, o controller obtém `sub` do JWT e passa seu UUID ao caso de uso.
O proprietário não é escolhido pelo corpo HTTP. Rejeitar `ownerId` enviado em
requests de criação ou atualização com 400; não permitir sobrescrever o ator.
As respostas de evento passam a incluir `ownerId`, nulo em registros legados.

As operações abaixo recebem o ID do ator e exigem correspondência com ownerId:

| Operação | Caso de uso |
| --- | --- |
| Atualizar nome/data | UpdateEventUseCase |
| Publicar | ChangeEventStatusToAvailableUseCase |
| Criar assentos em lote | CreateSeatsBatchUseCase |
| Atualizar assento | UpdateSeatUseCase |

Centralizar a comparação e a exceção de acesso negado em uma política de
aplicação de Event. Nenhuma assinatura antiga de escrita deve permitir contornar
a verificação. A política recebe o evento já carregado e o UUID do ator.

Para requisições autenticadas, com role e entrada válidas: carregar o evento,
retornar 404 se ausente e verificar propriedade antes de avaliar regras de
status, consultar assentos ou escrever. Propriedade incorreta ou ausente retorna
403 em Problem Details. Ausência de autenticação retorna 401; ausência de
ORGANIZER retorna 403 pela segurança HTTP. Validação estrutural de entrada pode
retornar 400 antes da consulta ao evento.

ADMIN sozinho continua sem permissão de escrita. ADMIN com ORGANIZER também
precisa ser o proprietário. GETs mantêm acesso a qualquer usuário autenticado,
inclusive para DRAFT; filtragem de catálogo e listagem de meus eventos ficam
fora desta entrega.

## Transações e concorrência

Verificar propriedade dentro dos casos de uso transacionais existentes e antes
das gravações. UpdateEventUseCase passa a carregar o evento para autorizar antes
do update. Criação de assentos verifica o evento obtido pelo bloqueio existente.
Publicação e atualização de assentos verificam o evento já carregado.

Preservar atualização condicional de status, bloqueio do lote e versionamento
otimista de assentos. Como a propriedade é imutável pela aplicação, não criar
um novo mecanismo de lock para essa comparação. Uma operação negada não pode
alterar evento, assentos ou auditoria.

## Validação

- Domínio: novo evento exige dono; reconstituição preserva dono e aceita legado.
- Aplicação: proprietário autorizado, outro organizador negado, legado negado
  e evento ausente em cada uma das quatro operações de gerenciamento.
- HTTP: 401 sem token, 403 para CUSTOMER e ADMIN sem ORGANIZER; sucesso do dono
  e 403 para outro ORGANIZER, inclusive ADMIN com ORGANIZER não proprietário.
- Identidade: criar com JWT real, verificar ownerId persistido e devolvido;
  rejeitar tentativa de escolher ou trocar ownerId no corpo.
- Integridade: assento de outro evento não pode ser alterado usando o ID de um
  evento próprio; respostas de acesso negado não produzem efeitos persistidos.
- Persistência: FK, round-trip de ownerId e upgrade V5 para V6 preservando
  eventos/assentos legados, que permanecem sem dono e bloqueados para escrita.
- Adaptar fixtures existentes para usuários reais com IDs correspondentes aos
  JWTs de teste; manter testes anteriores de concorrência e leitura autenticada.
- Executar gradlew.bat check com PostgreSQL via Testcontainers e manter os
  limites existentes de 80% para linhas e branches, sem exclusões novas.

Atualizar README, arquitetura e invariantes com o contrato efetivamente entregue.

## Fora do escopo

Transferência de propriedade, múltiplos organizadores por evento, permissão
especial para ADMIN, recuperação de eventos legados, novas regras de leitura,
revogação imediata de JWT, reservas e refatorações não necessárias à integração.
