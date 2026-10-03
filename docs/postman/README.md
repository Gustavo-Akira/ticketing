# Postman — Ticketing Core

Importe [ticketing-core.postman_collection.json](ticketing-core.postman_collection.json) no Postman. A coleção usa o formato v2.1 e cobre os 15 endpoints implementados, com 17 requisições (login e logout também têm variantes ADMIN).

1. Inicie a API conforme o [README principal](../../README.md).
2. Crie o ADMIN pelo comando `identity create-admin` documentado no README.
3. Nas variáveis da coleção, ajuste `baseUrl` (padrão `http://localhost:8080`), `name`, `email`, `password`, `adminEmail` e `adminPassword`. Senhas começam vazias; o cadastro exige 12–64 caracteres e até 72 bytes UTF-8.
4. Execute as pastas na ordem numérica, manualmente ou pelo Collection Runner.

O cadastro cria CUSTOMER. O login ADMIN usa tokens separados, concede ORGANIZER ao `userId` cadastrado e a renovação seguinte atualiza as roles do JWT do usuário. Assim, a mesma conta cria o evento e faz a reserva. A criação do evento define `startsAt` para daqui a 30 dias quando a variável está vazia; também é possível preencher um instante ISO-8601 manualmente.

Tokens, IDs e versão/localização do assento são capturados automaticamente em variáveis da coleção. Não é necessário importar environment; evite variáveis de mesmo nome em outros escopos, pois podem sobrepor as capturadas. A listagem de assentos seleciona o primeiro da página (use `page=0`). O PUT envia `expectedVersion` numérico; em conflito 409, consulte novamente e revise a edição antes de reenviar.

Publique somente depois de criar assentos. A reserva exige evento AVAILABLE e assento disponível. Cada requisição verifica o status HTTP esperado; as que criam ou consultam recursos alimentam as próximas. Uma execução completa altera o banco e não é idempotente: use e-mail novo para repetir; para conta existente, pule o cadastro e informe `userId` quando necessário. Após reservar, consulte o assento antes de tentar editá-lo novamente.

Refresh rotaciona os tokens: não reutilize valores antigos nem execute renovações em paralelo. Os logouts ao final revogam as sessões de refresh e limpam os tokens locais; JWTs já emitidos continuam válidos até expirar. Não versione nem compartilhe exportações contendo senhas ou tokens preenchidos.

A validação local da coleção não substitui a execução contra a API.
