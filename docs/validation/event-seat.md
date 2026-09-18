# Validação de Event/Seat

- `gradlew.bat clean check --no-daemon`: 44 testes, zero falhas e zero ignorados.
- JaCoCo: 46/48 linhas (95,83%) e 12/12 branches (100%), sem exclusões.
- Prova negativa: `gradlew.bat clean test --tests '*EventDomainTest' jacocoTestCoverageVerification --no-daemon`
  falhou nos dois limites, com razões de cobertura reportadas de 0,37 e 0,66.
  A suíte completa foi executada novamente após essa verificação.
- `docker compose -p ticketing-event-validation up --build -d`: imagem construída,
  PostgreSQL saudável e aplicação iniciada pelo usuário sem privilégios `ticketing`.
- Flyway aplicou V1; PostgreSQL confirmou `events`, `seats` e `flyway_schema_history`.
- Containers temporários encerrados com `docker compose -p ticketing-event-validation down`.
- Revisão independente das entidades, repositories, migration, testes e configuração
  de cobertura não encontrou problemas de correção.

Escopo corresponde ao PR 1 do roadmap: bootstrap e persistência. Regras de reserva,
transições de venda e APIs são entregas futuras. A checagem de cobertura executa
em CI; sua obrigatoriedade para merge depende da proteção de branch do GitHub.
