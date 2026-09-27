# VINSETT Budget API

API inteligente de orçamento financeiro construída com **Java 17**, **Spring Boot 4.1.1** e **Spring AI 2.0.1**. O projeto recebe comandos em texto ou voz, interpreta a intenção com IA Generativa, executa ações reais por meio de Tool Calling, persiste lançamentos e limites de orçamento e pode devolver a resposta em áudio MP3.

## Fluxo principal

```text
Áudio do usuário
   ↓
Speech-to-Text
   ↓
Spring AI ChatClient
   ↓
Tool Calling
   ↓
Serviços de orçamento / persistência
   ↓
Resposta em texto
   ↓
Text-to-Speech (opcional)
   ↓
MP3
```

## Funcionalidades

- Entrada por texto (`POST /api/v1/assistant/text`).
- Entrada por voz (`POST /api/v1/assistant/voice`).
- Transcrição de áudio (`POST /api/v1/audio/transcriptions`).
- Síntese de voz em MP3 (`POST /api/v1/audio/speech`).
- Spring AI `ChatClient` com `ToolCallingAdvisor`.
- Tools para registrar receitas/despesas, definir orçamento mensal, listar lançamentos e consultar resumo financeiro.
- Persistência com Spring Data JPA + Flyway.
- H2 em execução local e PostgreSQL via Docker Compose.
- Autenticação das rotas `/api/v1/**` por Bearer token (`APP_API_TOKEN`).
- Idempotência com `Idempotency-Key` para evitar operações duplicadas.
- Histórico de comandos e ações executadas.
- Limite de concorrência de requisições de IA.
- Limite de Tool Calling por comando: 6 chamadas totais e 3 chamadas por ferramenta.
- Health check em `/actuator/health`.
- Testes de integração com provedor HTTP simulado, sem consumo real de API durante os testes.

## Stack

- Java 17+
- Spring Boot 4.1.1
- Spring AI 2.0.1
- Spring Web MVC
- Spring Security
- Spring Data JPA
- Flyway
- PostgreSQL
- H2
- Docker / Docker Compose
- JUnit 5 / AssertJ
- OpenAI-compatible Chat, Transcription e Speech APIs

## Estrutura

```text
src/main/java/com/vinsett/budget/
├── ai/        # ChatClient, Tool Calling e orquestração da IA
├── api/       # Controllers REST
├── audio/     # Speech-to-Text e Text-to-Speech
├── command/   # Idempotência, histórico e ações dos comandos
├── config/    # Configuração da aplicação
├── ledger/    # Regras de negócio financeiras
├── security/  # Bearer token e identidade da conta
└── shared/    # Erros e utilitários
```

## Configuração

Copie `.env.example` para `.env` e preencha:

```env
OPENAI_API_KEY=
APP_API_TOKEN=
DB_PASSWORD=
APP_ZONE=America/Manaus
OPENAI_CHAT_MODEL=gpt-4.1-mini
OPENAI_TRANSCRIPTION_MODEL=gpt-4o-mini-transcribe
OPENAI_TTS_MODEL=gpt-4o-mini-tts
OPENAI_TTS_VOICE=alloy
```

> Nunca publique o arquivo `.env` nem credenciais reais no GitHub.

## Executar com Docker Compose

```bash
docker compose up --build
```

A API será exposta em `http://localhost:8080` e utilizará PostgreSQL persistente.

## Executar com Maven

Com JDK 17+ e Maven instalados:

```bash
mvn spring-boot:run
```

Neste modo, a configuração padrão usa H2 persistente em `./data`.

## Testes

```bash
mvn test
```

A suíte atual possui **19 testes de integração**. Eles usam um servidor HTTP simulado para Chat, STT e TTS, portanto não consomem uma API externa durante a execução.

Veja `docs/QA_REPORT.md`.

## Exemplos

### Comando por texto

```http
POST /api/v1/assistant/text
Authorization: Bearer SEU_APP_API_TOKEN
Idempotency-Key: 550e8400-e29b-41d4-a716-446655440000
Content-Type: application/json

{
  "text": "Registre uma despesa de 42,90 reais com almoço hoje.",
  "includeAudio": true
}
```

### Comando por voz

Envie `multipart/form-data` com o áudio no campo `file` para:

```text
POST /api/v1/assistant/voice
```

O cliente `examples/voice_client.py` demonstra o envio do áudio e salva a resposta em `response.mp3`.

## Endpoints principais

| Método | Endpoint | Função |
|---|---|---|
| POST | `/api/v1/assistant/text` | Interpreta comando textual e pode executar tools |
| POST | `/api/v1/assistant/voice` | Transcreve áudio, interpreta comando e pode responder em áudio |
| GET | `/api/v1/assistant/commands/{id}` | Consulta um comando processado |
| POST | `/api/v1/assistant/commands/{id}/speech` | Gera áudio da resposta de um comando |
| POST | `/api/v1/audio/transcriptions` | Transcreve áudio isoladamente |
| POST | `/api/v1/audio/speech` | Converte texto em MP3 |
| POST | `/api/v1/entries` | Cria receita ou despesa |
| GET | `/api/v1/entries/{id}` | Consulta lançamento |
| GET | `/api/v1/entries` | Lista lançamentos |
| GET | `/api/v1/summary` | Retorna resumo financeiro mensal |
| PUT | `/api/v1/budgets/{month}/{category}` | Define/substitui limite mensal por categoria |
| GET | `/actuator/health` | Health check |

## Segurança e consistência

A aplicação não permite que o usuário forneça o `accountId`; a identidade autenticada é injetada pelo servidor no `ToolContext`. Operações de escrita devem ser explícitas. O assistente usa os totais calculados pela aplicação, em vez de inventar valores financeiros.

`Idempotency-Key` é obrigatório em operações de comando/escrita para impedir que uma mesma intenção seja registrada duas vezes quando há repetição de requisição.

## Estado da entrega

- Código-fonte: concluído.
- Migração de banco: concluída.
- Testes automatizados: 19 cenários implementados e previamente validados.
- Docker/PostgreSQL: configuração incluída.
- Integração de voz: implementada; chamadas reais dependem de chave, acesso aos modelos e cota do provedor.
- Deploy externo: não incluído nesta versão.

## Projeto

**VINSETT Budget API v1.0.0** — backend de orçamento financeiro com IA, voz e Tool Calling, preparado como base extensível para novos fluxos de automação financeira.
