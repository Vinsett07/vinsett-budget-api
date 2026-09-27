# QA Report — VINSETT Budget API 1.0.0

## Resultado

A suíte do projeto contém **19 testes de integração** (`@Test`) em `BudgetApiIntegrationTest`.

Na validação realizada durante a construção original do projeto, os **19 testes foram aprovados**. Também foram verificadas persistência e proteção contra duplicidade após reinício da aplicação.

## Cobertura funcional validada

A suíte cobre os principais fluxos do backend, incluindo:

- autenticação por token;
- criação e consulta de lançamentos;
- resumo mensal;
- definição de orçamento por categoria;
- idempotência por `Idempotency-Key`;
- integração do `ChatClient` com Tool Calling;
- execução e registro de ações do comando;
- transcrição de voz com provedor simulado;
- síntese de voz MP3 com provedor simulado;
- tratamento de indisponibilidade do provedor;
- limites de execução das tools;
- prevenção de gravações indevidas em comandos ambíguos.

## Ambiente de teste

Os testes usam:

- banco H2 em memória compatível com PostgreSQL;
- servidor HTTP simulado para endpoints de Chat, Speech-to-Text e Text-to-Speech;
- credenciais fictícias de teste;
- porta aleatória do Spring Boot.

Com isso, a suíte pode ser executada sem consumir créditos de um provedor de IA.

## Limites desta validação

As chamadas reais de IA não fazem parte dos testes automatizados porque dependem de credenciais, disponibilidade, acesso aos modelos e cota do provedor. O Docker Compose e o PostgreSQL estão configurados no projeto, mas a validação original registrada para esta entrega concentrou-se no backend e nos testes automatizados.
