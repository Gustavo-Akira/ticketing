# Validação de Event/Seat

- `gradlew.bat clean check --no-daemon`: 64 testes, zero falhas e zero ignorados.
- JaCoCo: 50/52 linhas (96,15%) e 12/12 branches (100%), sem exclusões.
- Prova negativa: `gradlew.bat clean test --tests '*EventDomainTest' jacocoTestCoverageVerification --no-daemon`
  falhou nos dois limites, com razões de cobertura reportadas de 0,37 e 0,66.
  A suíte completa foi executada novamente após essa verificação.
- `docker compose -p ticketing-event-validation up --build -d`: imagem construída,
  PostgreSQL saudável e aplicação iniciada pelo usuário sem privilégios `ticketing`.
- Flyway aplicou V1; PostgreSQL confirmou `events`, `seats` e `flyway_schema_history`.
- Containers temporários encerrados com `docker compose -p ticketing-event-validation down`.
- Revisão independente das entidades, repositories, migration, testes e configuração
  de cobertura não encontrou problemas de correção.
- Follow-up: UUIDv7 nas novas entidades, timestamps de Event preenchidos na inserção,
  moeda ISO obrigatória no domínio e migration V2. Teste de upgrade executa a V1,
  insere dados, aplica a V2 e confirma preservação de IDs/preços e backfill.
- V3: remoção do trigger, função e constraint de ordem temporal da V2.
  Updates atribuem `updated_at = statement_timestamp()` explicitamente.
  Testes verificam o instante da própria instrução e ausência de efeitos implícitos
  quando uma query omite a atribuição; não exigem monotonicidade.
- EXPLAIN confirma que `WHERE event_id = ?` pode usar `uk_seats_event_location`.
  O teste desativa sequential scan apenas na própria transação para verificar
  elegibilidade; não representa comparação de desempenho sob carga.

Escopo corresponde ao PR 1 do roadmap: bootstrap e persistência. Regras de reserva,
transições de venda e APIs são entregas futuras. A checagem de cobertura executa
em CI; sua obrigatoriedade para merge depende da proteção de branch do GitHub.
