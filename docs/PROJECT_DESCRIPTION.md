# Descrição do projeto

O **VINSETT Budget API** é uma API inteligente de orçamento financeiro desenvolvida com Java, Spring Boot e Spring AI. A aplicação interpreta comandos em texto ou voz, transforma áudio em texto, utiliza IA Generativa com Tool Calling para acionar funções reais da aplicação e persiste receitas, despesas e limites mensais de orçamento.

A arquitetura separa controllers REST, orquestração de IA, regras de negócio, persistência, segurança e serviços de áudio. O fluxo completo permite receber um comando de voz, transcrevê-lo, enviá-lo ao `ChatClient`, executar ferramentas autorizadas, registrar as ações no banco e produzir uma resposta final em texto ou MP3.

O projeto também inclui autenticação por Bearer token, idempotência para evitar duplicidades, histórico de comandos, Flyway, H2 para execução local, PostgreSQL via Docker Compose, controle de concorrência das chamadas de IA, limites de Tool Calling, exemplos de requisição e testes de integração com provedor simulado.
