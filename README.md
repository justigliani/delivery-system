# DeliverySystem — Projeto Diamante

Sistema de pedidos de delivery construído com arquitetura de microsserviços, para a disciplina de Java Advanced.

## Integrantes

| Nome | RM |
|---|---|
| Erik Naoki Miyasato | RM565771 |
| Juliana da Silva Stigliani | RM561171 |

## Sumário

1. [Tecnologias](#tecnologias)
2. [Arquitetura](#arquitetura)
3. [Pré-requisitos](#pré-requisitos)
4. [Como executar](#como-executar)
5. [Como testar cada critério](#como-testar-cada-critério)
6. [App do professor](#app-do-professor-react-native--expo)
7. [Contrato da API](#contrato-da-api)
8. [Decisões técnicas](#decisões-técnicas)
9. [Problemas conhecidos](#problemas-conhecidos)
10. [macOS e Linux](#macos-e-linux)

## Tecnologias

- Java 25
- Spring Boot 4.1.1
- Spring Cloud 2025.1.3 (Eureka e LoadBalancer)
- Spring Data JPA com H2 em memória
- Spring AMQP com RabbitMQ 4 (Docker)
- Spring AI 2.0.1 (cliente OpenAI, compatível com Groq e modelos locais)
- Gradle, em monorepo com 4 módulos

## Arquitetura

| Serviço | Porta | Responsabilidade |
|---|---|---|
| `eureka-server` | 8761 | Service discovery. Todos os serviços se registram nele. |
| `payment-service` | 8081 e 8082 | Pagamento simulado, instável de propósito (falha em ~50% das chamadas). Roda em duas instâncias. |
| `order-service` | 8080 | Cardápio, pedidos com lock de estoque, chamada ao pagamento com load balance e retry, publicação de avaliações, assistente com IA e rate limit. |
| `review-service` | 8083 | Consome as avaliações da fila, acumula em buffer, grava a cada 5 s e serve o ranking. |
| RabbitMQ (Docker) | 5672 e 15672 | Mensageria: `delivery.exchange` → `reviews.queue`. Painel em 15672. |

Nenhum serviço chama outro por `localhost`. O `order-service` chama o pagamento em `http://PAYMENT-SERVICE/payments`, e o load balancer escolhe a instância pelo registro no Eureka.

```
delivery-system/
├── docker-compose.yml     RabbitMQ
├── settings.gradle        inclui os 4 módulos
├── eureka-server/
├── payment-service/
├── order-service/
└── review-service/
```

## Pré-requisitos

- **Java 25**
- **Docker** (Docker Desktop aberto), para o RabbitMQ
- **Uma chave de API** de um provedor compatível com a OpenAI, para o assistente (veja [Spring AI](#spring-ai-assistente)). Sem ela, todo o resto funciona normalmente.
- **Node.js 20 ou superior**, apenas para rodar o app do professor

## Como executar

Os comandos abaixo usam o PowerShell do Windows. Para macOS e Linux, veja [macOS e Linux](#macos-e-linux).

Todos os comandos são rodados a partir da pasta raiz `delivery-system`, cada serviço em um terminal separado. Suba na ordem abaixo e espere a mensagem `Started ...Application` de cada um antes de subir o próximo.

**1. RabbitMQ**

```powershell
docker compose up -d
```

**Opcional: compilar os 4 módulos de uma vez**

```powershell
.\gradlew.bat build -x test
```

Não é obrigatório, porque o `bootRun` já compila cada serviço. Serve para encontrar um erro de compilação antes de abrir os terminais. O `-x test` pula os testes, que são executados à parte (veja [Race condition](#race-condition)).

**2. Terminal 1: Eureka Server (porta 8761)**

```powershell
.\gradlew.bat :eureka-server:bootRun
```

**3. Terminal 2: Payment Service, instância 1 (porta 8081)**

```powershell
.\gradlew.bat :payment-service:bootRun
```

**4. Terminal 3: Payment Service, instância 2 (porta 8082)**

```powershell
.\gradlew.bat :payment-service:bootRun --args='--server.port=8082'
```

**5. Terminal 4: Order Service (porta 8080)**

Para o assistente funcionar, defina as variáveis do provedor de IA **no mesmo terminal**, antes do `bootRun`. Exemplo com o Groq (plano gratuito):

```powershell
$env:OPENAI_API_KEY  = "sua-chave"
$env:OPENAI_BASE_URL = "https://api.groq.com/openai/v1"
$env:OPENAI_MODEL    = "openai/gpt-oss-120b"
.\gradlew.bat :order-service:bootRun
```

Com a OpenAI, basta definir `OPENAI_API_KEY`, porque a URL e o modelo padrão já são os dela. Detalhes em [Spring AI](#spring-ai-assistente).

**6. Terminal 5: Review Service (porta 8083)**

```powershell
.\gradlew.bat :review-service:bootRun
```

**7. Conferência**

Abra http://localhost:8761. Em *Instances currently registered with Eureka* devem aparecer:

- `PAYMENT-SERVICE` com 2 instâncias (`payment-service:8081` e `payment-service:8082`)
- `ORDER-SERVICE` com 1 instância
- `REVIEW-SERVICE` com 1 instância

O `order-service` atualiza a lista de instâncias do Eureka a cada 30 segundos. Se ele acabou de subir, espere esse tempo antes de testar os pedidos.

**Para encerrar**

```powershell
.\gradlew.bat --stop
docker compose down
```

O primeiro comando para todos os serviços iniciados com `bootRun`. O segundo desliga o RabbitMQ.

## Como testar cada critério

Rode os comandos num terminal separado, com todos os serviços no ar. Os resultados esperados consideram o `order-service` recém-iniciado, com o prato da promoção (House Burger, id 1) em estoque 10.

O H2 é em memória: ao reiniciar um serviço, os dados dele voltam ao estado inicial.

### Eureka

Os três serviços aparecem registrados pelo `spring.application.name`, como mostrado em [Como executar](#como-executar), passo 7.

### Payment service (chamada direta)

O app não chama o pagamento diretamente: quem chama é o `order-service`. Este teste serve só para ver cada instância funcionando sozinha.

```powershell
curl.exe -i -X POST http://localhost:8081/payments -H "Content-Type: application/json" -d '{\"amount\": 79.80}'
```

Rode algumas vezes. As respostas devem alternar entre:

- `200` com `{"status":"APPROVED","instance":8081}`, informando a porta da instância que atendeu.
- `500` sem corpo, que é a falha simulada (cerca de 50% das chamadas, sorteada com `Random`).

Trocando `8081` por `8082`, a resposta aprovada passa a trazer `"instance":8082`.

### Cardápio e pedidos

**Listar pratos** → `200` com os 5 pratos

```powershell
curl.exe http://localhost:8080/dishes
```

**Buscar prato** → `200` com o House Burger, `"stock":10`

```powershell
curl.exe -i http://localhost:8080/dishes/1
```

**Prato inexistente** → `404` `{"error":"Dish not found"}`

```powershell
curl.exe -i http://localhost:8080/dishes/99
```

**Criar pedido** → `201` com `"totalPrice":79.80` e `"status":"CONFIRMED"`

```powershell
curl.exe -i -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"dishId\": 1, \"quantity\": 2}'
```

**Buscar pedido** → `200` com o pedido criado

```powershell
curl.exe -i http://localhost:8080/orders/1
```

**Quantidade inválida** → `400` `{"error":"Quantity must be at least 1"}`

```powershell
curl.exe -i -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"dishId\": 1, \"quantity\": 0}'
```

**Prato inexistente no pedido** → `404` `{"error":"Dish not found"}`

```powershell
curl.exe -i -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"dishId\": 99, \"quantity\": 1}'
```

**Sem estoque** → `409` `{"error":"Dish out of stock"}`

```powershell
curl.exe -i -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"dishId\": 1, \"quantity\": 50}'
```

**Pedido inexistente** → `404` `{"error":"Order not found"}`

```powershell
curl.exe -i http://localhost:8080/orders/99
```

Como o pagamento falha em cerca de 50% das chamadas, um pedido válido pode, raramente, terminar em `502` (veja [Retry](#retry-e-502-com-estoque-intacto)).

### Load balance

Faça 6 pedidos de 1 unidade seguidos:

```powershell
1..6 | ForEach-Object { curl.exe -s -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"dishId\": 1, \"quantity\": 1}'; "" }
```

**Resultado esperado:**

- Os pedidos voltam com `"status":"CONFIRMED"`.
- Nos terminais das duas instâncias do payment-service, as linhas abaixo aparecem **nas duas**, revezando entre elas (round-robin):

```
Payment request for amount 39.90 handled by instance on port 8081
Payment request for amount 39.90 handled by instance on port 8082
```

- Quando o pagamento falha, a instância registra também `Simulated failure on port ...`.

Exemplo real dos logs das duas instâncias, ordenados por horário:

```
09:18:00.238  8081  aprovado
09:18:00.353  8082  aprovado
09:18:00.417  8081  falha
09:18:00.951  8082  falha
09:18:01.820  8081  falha
09:18:03.512  8082  aprovado
```

Nessa execução, 14 chamadas foram divididas em 7 para cada instância: 5 aprovadas (5 pedidos confirmados) e 9 falhas absorvidas pelo retry.

### Retry e 502 com estoque intacto

A chamada ao pagamento fica no bean `PaymentClient`, anotado com `@Retryable` (retry nativo do Spring Framework 7, habilitado com `@EnableResilientMethods`):

| Parâmetro | Valor | Efeito |
|---|---|---|
| `maxRetries` | 3 | 1 tentativa normal + 3 novas tentativas, 4 no total |
| `delay` | 500 ms | Espera antes da primeira nova tentativa |
| `multiplier` | 2.0 | Backoff exponencial: 500 ms, 1 s, 2 s |
| `maxDelay` | 3000 ms | Nenhuma espera passa de 3 s |
| `jitter` | 100 ms | Varia cada espera em até ±100 ms, para as novas tentativas não sincronizarem |
| `includes` | `RestClientException` | Só tenta de novo em erros de comunicação HTTP |

Com 50% de falha por tentativa, a chance das 4 tentativas falharem é de cerca de 6%.

**Para forçar o 502:**

1. Pare as duas instâncias do payment-service (Ctrl+C nos terminais delas).
2. Confirme que estão paradas. Os dois comandos devem responder `Failed to connect`:

   ```powershell
   curl.exe -i -X POST http://localhost:8081/payments -H "Content-Type: application/json" -d '{\"amount\": 1}'
   curl.exe -i -X POST http://localhost:8082/payments -H "Content-Type: application/json" -d '{\"amount\": 1}'
   ```

3. Anote o estoque atual:

   ```powershell
   curl.exe http://localhost:8080/dishes/1
   ```

4. Faça um pedido:

   ```powershell
   curl.exe -i -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{\"dishId\": 1, \"quantity\": 1}'
   ```

   **Esperado:** depois de alguns segundos (as esperas do retry), `502` com `{"error":"Payment service unavailable"}`.

5. Consulte o estoque de novo. **Esperado:** o mesmo valor do passo 3.

O pedido não é salvo e o estoque não muda porque a falha do pagamento lança uma exceção dentro do `@Transactional`, o que desfaz a transação inteira.

Depois do teste, suba as duas instâncias do payment-service de novo.

### Race condition

**Como funciona:** o `POST /orders` lê o prato com `@Lock(LockModeType.PESSIMISTIC_WRITE)` dentro de um método `@Transactional`, o que gera um `SELECT ... FOR UPDATE`. Enquanto um pedido está em andamento, os outros pedidos do mesmo prato esperam na fila, e ninguém lê um estoque desatualizado. O `LOCK_TIMEOUT=30000` no H2 faz eles esperarem, em vez de falharem.

#### Teste automatizado (JUnit)

Não precisa de nenhum serviço no ar:

```powershell
.\gradlew.bat :order-service:test
```

Para abrir o relatório:

```powershell
start order-service\build\reports\tests\test\index.html
```

`OrderConcurrencyTest`:

- **`fiftySimultaneousOrdersConfirmExactlyTen()`**: 50 threads liberadas ao mesmo tempo (`CountDownLatch`) no prato da promoção, com o pagamento simulado (`@MockitoBean`) para isolar o lock. Confere: 10 confirmados, 40 recusados por falta de estoque, stock final 0 e 10 pedidos no banco.
- **`failedPaymentKeepsStockIntact()`**: o pagamento falha, é lançada a `PaymentProcessingException`, e o estoque e os pedidos ficam inalterados.

**Resultado:** 3 testes, 0 falhas, 100%.

#### Teste real via HTTP

Pré-requisitos: reiniciar o `order-service` (o estoque do prato 1 volta a 10) e deixar as 2 instâncias do payment-service no ar.

1. Monta a lista com 50 pedidos (não mostra nada):

   ```powershell
   $curlArgs = @('-s', '--parallel', '--parallel-immediate', '--parallel-max', '50', '-X', 'POST', '-H', 'Content-Type: application/json', '-d', '{\"dishId\": 1, \"quantity\": 1}', '-w', '%{http_code}\n'); 1..50 | ForEach-Object { $curlArgs += @('-o', 'NUL', 'http://localhost:8080/orders') }
   ```

2. Dispara os 50 em paralelo e conta as respostas por status:

   ```powershell
   curl.exe @curlArgs | Group-Object | Select-Object Count, Name
   ```

3. Confere o estoque:

   ```powershell
   curl.exe http://localhost:8080/dishes/1
   ```

Resultado real, **sem o rate limit** (veja a nota abaixo):

```
Count Name
----- ----
   10 201
    3 502
   37 409
```

`GET /dishes/1` → `"stock":0`

Os `502` vêm do pagamento instável (as 4 tentativas falharam). Eles não consomem estoque: a transação é desfeita e o próximo pedido da fila fica com a vaga. Por isso os `201` são sempre exatamente 10. A quantidade de `502` varia a cada execução.

> **Com o rate limit ligado (padrão),** só 20 das 50 requisições passam, e as outras 30 recebem `429`. Continuam sendo exatamente 10 pedidos confirmados e stock 0. Para rodar o teste sem o limitador no caminho, suba o order-service com um limite maior:
>
> ```powershell
> .\gradlew.bat :order-service:bootRun --args='--orders.rate-limit.per-second=100'
> ```

### Mensageria

**Configuração explícita** (`RabbitConfig` do order-service): `TopicExchange` `delivery.exchange`, fila durável `reviews.queue`, routing key `reviews.new` e o binding entre elas. A mensagem é enviada em JSON.

**Testes do `POST /reviews`:**

**Avaliação válida** → `202` sem corpo (`Content-Length: 0`)

```powershell
curl.exe -i -X POST http://localhost:8080/reviews -H "Content-Type: application/json" -d '{\"dishId\": 1, \"rating\": 5, \"comment\": \"Great\"}'
```

**Rating abaixo de 1** → `400` `{"error":"Rating must be between 1 and 5"}`

```powershell
curl.exe -i -X POST http://localhost:8080/reviews -H "Content-Type: application/json" -d '{\"dishId\": 1, \"rating\": 0, \"comment\": \"Bad\"}'
```

**Rating acima de 5** → `400` `{"error":"Rating must be between 1 and 5"}`

```powershell
curl.exe -i -X POST http://localhost:8080/reviews -H "Content-Type: application/json" -d '{\"dishId\": 1, \"rating\": 6, \"comment\": \"Wow\"}'
```

**Prato inexistente** → `404` `{"error":"Dish not found"}`

```powershell
curl.exe -i -X POST http://localhost:8080/reviews -H "Content-Type: application/json" -d '{\"dishId\": 99, \"rating\": 5, \"comment\": \"Great\"}'
```

**4 avaliações seguidas** (mostra só o status de cada uma) → quatro linhas `202`

```powershell
1..4 | ForEach-Object { curl.exe -s -o NUL -w "%{http_code}`n" -X POST http://localhost:8080/reviews -H "Content-Type: application/json" -d '{\"dishId\": 2, \"rating\": 4, \"comment\": \"Good\"}' }
```

**Conferência no painel do RabbitMQ** (http://localhost:15672, usuário `guest`, senha `guest`):

- **Exchanges:** `delivery.exchange`, tipo `topic`, durável (`D`).
- **Queues and Streams:** `reviews.queue`, durável (`D`). Clicando nela, a seção *Bindings* mostra a ligação com a `delivery.exchange` pela routing key `reviews.new`.

Exemplo real de mensagem na fila (seção *Get messages*):

```
Routing Key:   reviews.new
content_type:  application/json
Payload:       {"dishId":1,"dishName":"House Burger","rating":5,"comment":"Great"}
```

Com o review-service rodando, as mensagens são consumidas na hora. Para ver a fila acumulando, pare o review-service antes de enviar as avaliações.

O order-service não grava a avaliação no banco: só valida, monta a mensagem (buscando o `dishName` no cardápio) e publica na fila.

### Backpressure

**Consumo da fila:** cada mensagem recebida pelo `@RabbitListener` só é somada na memória, num `ConcurrentHashMap` por prato (soma das notas e quantidade). A cada 5 segundos, um `@Scheduled` grava o acumulado no H2 (entidade `ReviewSummary`) e limpa o buffer.

Exemplo real do log, com 5 avaliações esperando na fila quando o review-service subiu:

```
Buffered review for dish 1 with rating 5
Buffered review for dish 2 with rating 4   (x4)
Flushed 5 reviews for 2 dishes to the database
```

5 avaliações recebidas, 1 escrita no banco.

**Rajada de 20 avaliações** para o prato 3, com notas sorteadas de 1 a 5:

```powershell
1..20 | ForEach-Object { $r = Get-Random -Minimum 1 -Maximum 6; curl.exe -s -o NUL -w "%{http_code} " -X POST http://localhost:8080/reviews -H "Content-Type: application/json" -d ('{\"dishId\": 3, \"rating\": ' + $r + ', \"comment\": \"Ok\"}') }
```

Resultado: vinte `202`. No log do review-service:

```
Buffered review for dish 3 with rating ...   (20 linhas)
Flushed 20 reviews for 1 dishes to the database
```

**20 avaliações viraram 1 escrita no banco.** Se a rajada atravessar o momento do flush, aparecem 2 linhas `Flushed` dividindo as 20; o efeito é o mesmo.

**Ranking** (lido do banco, ordenado pela média, maior primeiro):

```powershell
curl.exe http://localhost:8083/reviews/ranking
```

Exemplo real, depois das avaliações acima:

```json
[{"dishId":1,"dishName":"House Burger","average":5.0,"count":1},
 {"dishId":2,"dishName":"Pizza Margherita","average":4.0,"count":4},
 {"dishId":3,"dishName":"Veggie Bowl","average":2.9,"count":20}]
```

A média do Veggie Bowl varia a cada execução, porque as notas são sorteadas.

**Conferência:** no painel do RabbitMQ, a `reviews.queue` fica com *Ready* 0 e a aba *Overview* mostra *Consumers: 1*.

### Spring AI (assistente)

O provedor é configurado por variáveis de ambiente. A chave nunca vai para o repositório.

| Variável | Padrão | Descrição |
|---|---|---|
| `OPENAI_API_KEY` | (nenhum) | Chave do provedor. Obrigatória para o assistente. |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Endereço da API |
| `OPENAI_MODEL` | `gpt-4o-mini` | Modelo |

Sem a chave, o serviço sobe normalmente, mas o `/assistant` responde `502` `{"error":"Assistant unavailable"}`.

**Usando o Groq** (plano gratuito, compatível com a API da OpenAI):

1. Criar a chave em console.groq.com → *API Keys* → *Create API Key*. Ela começa com `gsk_` e só é exibida uma vez.
2. Definir as variáveis no **mesmo terminal** em que o order-service vai subir, como em [Como executar](#como-executar), passo 5.

As variáveis valem só para esse terminal. Ao fechá-lo, elas somem; num terminal novo, é preciso defini-las de novo.

**Usando a OpenAI:** basta definir `OPENAI_API_KEY`.

**Usando um modelo local** (LM Studio ou Ollama): definir `OPENAI_BASE_URL` com o endereço compatível com a OpenAI do servidor local (por exemplo, `http://localhost:11434/v1` no Ollama) e `OPENAI_MODEL` com o nome do modelo.

**Testes** (perguntas sem acento por causa da forma como o PowerShell envia o texto; pelo app, acentos funcionam):

**Pergunta sobre o cardápio** → `200`

```powershell
curl.exe -i -X POST http://localhost:8080/assistant -H "Content-Type: application/json" -d '{\"question\": \"Tem prato vegetariano ate R$ 40?\"}'
```

Exemplo real: `{"answer":"Sim, temos o Veggie Bowl por R$ 32, dentro do seu limite."}`

**Pergunta fora do tema** → `200` com recusa

```powershell
curl.exe -i -X POST http://localhost:8080/assistant -H "Content-Type: application/json" -d '{\"question\": \"Quem ganhou a Copa de 2002?\"}'
```

Exemplo real: `{"answer":"Desculpe, não posso responder a isso. Por favor, pergunte sobre o nosso menu ou faça seu pedido. Estou à disposição!"}`

**Pergunta vazia** → `400` `{"error":"Question must not be empty"}`

```powershell
curl.exe -i -X POST http://localhost:8080/assistant -H "Content-Type: application/json" -d '{\"question\": \"\"}'
```

**Falha no provedor** → `502` `{"error":"Assistant unavailable"}`. O motivo real aparece no log do order-service, logo após a linha `AI provider call failed`.

As respostas mudam a cada chamada, porque são geradas pelo modelo.

### Bônus: rate limit

**Como funciona:** implementação própria com *token bucket* (`OrderRateLimiter`). O balde começa com 20 fichas, cada `POST /orders` gasta 1, e ele se reabastece continuamente a 20 fichas por segundo, no máximo 20. Sem ficha, a resposta é `429` `{"error":"Too many requests, please try again in a moment"}`. Diferente de um contador por segundo, ele não deixa passar 40 pedidos na virada de um segundo para o outro.

O `RateLimitInterceptor` roda antes do controller e só no `POST /orders`. O `GET /orders/{id}` e as outras rotas não são limitadas. O `synchronized` garante que duas requisições não usem a mesma ficha.

O limite é configurável (padrão 20):

```powershell
.\gradlew.bat :order-service:bootRun --args='--orders.rate-limit.per-second=100'
```

**Teste:** 30 pedidos simultâneos com `quantity: 0`. Quem passa pelo limite recebe `400` da validação, e quem é barrado recebe `429`. Assim o teste não consome estoque nem chama o pagamento.

```powershell
$rlArgs = @('-s', '--parallel', '--parallel-immediate', '--parallel-max', '30', '-X', 'POST', '-H', 'Content-Type: application/json', '-d', '{\"dishId\": 2, \"quantity\": 0}', '-w', '%{http_code}\n'); 1..30 | ForEach-Object { $rlArgs += @('-o', 'NUL', 'http://localhost:8080/orders') }
curl.exe @rlArgs | Group-Object | Select-Object Count, Name
```

Resultado real:

```
Count Name
----- ----
   20 400
   10 429
```

Os números podem variar em 1 ou 2, porque o balde reabastece enquanto as requisições chegam. Após cerca de 2 segundos, um pedido normal volta a ser aceito (`201`).

## App do professor (React Native / Expo)

Repositório: github.com/joaocarloslima/diamante-delivery

Clonar numa pasta **fora** do `delivery-system`. Dentro da pasta do app:

```powershell
npm install
npx expo start --port 8090
```

Depois, apertar a tecla `w` no terminal para abrir no navegador. A porta 8090 evita conflito com os serviços (8080 a 8083).

No app: botão do servidor (canto superior direito) → `localhost` → *Testar conexão*.

**Resultado: todas as telas funcionaram sem ajustes.**

| Tela | O que aconteceu | Endpoint |
|---|---|---|
| Cardápio | Lista os 5 pratos com preço e estoque | `GET /dishes` (8080) |
| Pedido | Pedido confirmado e estoque atualizado | `POST /orders` (8080) |
| Avaliação | Aceita nota de 1 a 5 | `POST /reviews` (8080) |
| Ranking | Atualiza a cada 5 s | `GET /reviews/ranking` (8083) |
| Assistente | Responde sobre o cardápio e recusa fora do tema | `POST /assistant` (8080) |

O CORS está liberado no order-service e no review-service (`WebConfig`), o que é necessário quando o app roda no navegador.

## Contrato da API

### order-service (8080)

| Método | Rota | Corpo | Respostas |
|---|---|---|---|
| GET | `/dishes` | | 200 lista de Dish |
| GET | `/dishes/{id}` | | 200 Dish · 404 |
| POST | `/orders` | `{"dishId": 1, "quantity": 2}` | 201 Order · 400 · 404 · 409 · 429 · 502 |
| GET | `/orders/{id}` | | 200 Order · 404 |
| POST | `/reviews` | `{"dishId": 1, "rating": 5, "comment": "Great"}` | 202 sem corpo · 400 · 404 |
| POST | `/assistant` | `{"question": "Tem opção vegana?"}` | 200 `{"answer": "..."}` · 400 · 502 |

### review-service (8083)

| Método | Rota | Respostas |
|---|---|---|
| GET | `/reviews/ranking` | 200 `[{"dishId", "dishName", "average", "count"}]` |

### payment-service (8081 e 8082, interno)

| Método | Rota | Corpo | Respostas |
|---|---|---|---|
| POST | `/payments` | `{"amount": 79.80}` | 200 `{"status": "APPROVED", "instance": 8081}` · 500 |

Todos os erros (4xx e 502) têm o corpo `{"error": "..."}`. Datas em ISO-8601 (`"2026-10-08T20:15:00"`) e valores com ponto decimal.

## Decisões técnicas

**Pedidos e estoque**

- **Prato da promoção:** House Burger, id 1, começa com estoque 10. Os outros 4 pratos começam com 50.
- **Pagamento antes da baixa de estoque:** o `OrderService` só desconta o estoque depois que o pagamento é aprovado. Se o pagamento falhar, a transação é desfeita.
- **`LOCK_TIMEOUT=30000` no H2:** faz os pedidos concorrentes esperarem a liberação do lock, em vez de falharem imediatamente.
- **`quantity` e `rating` como `Integer`:** se o campo não for enviado, chega como `null` e é tratado como `400`, em vez de virar `0` sem aviso.
- **Erros padronizados:** o `GlobalExceptionHandler` converte cada exceção no status HTTP do contrato, sempre com o corpo `{"error": "..."}`.

**Pagamento**

- **`PaymentClient` em bean separado:** o Spring aplica o `@Retryable` por meio de um proxy. Se o método fosse chamado de dentro da própria classe, o proxy seria ignorado e o retry não aconteceria.
- **`RestTemplate` com `@LoadBalanced`:** permite chamar `http://PAYMENT-SERVICE/payments` pelo nome registrado no Eureka.

**Mensageria e backpressure**

- **O order-service não grava avaliações:** valida, monta a mensagem e publica. Um pico de avaliações espera na fila em vez de sobrecarregar o banco.
- **`buffer.merge()`:** soma a nota de forma atômica, sem perder avaliações que chegam ao mesmo tempo em threads diferentes.
- **`buffer.remove()`:** retira e limpa o acumulado de cada prato numa operação só. Uma avaliação que chega durante o flush vai para o próximo.
- **`ReviewSummary` guarda soma e quantidade**, não só a média, para a média continuar exata a cada flush.
- **Média arredondada para 1 casa só na resposta**; no banco ela fica completa.
- **Empate na média:** fica na frente o prato com mais avaliações.
- **A fila é declarada nos dois serviços:** quem subir primeiro cria.
- **`@EnableScheduling`** na classe principal do review-service liga o `@Scheduled`.
- **O consumidor converte o JSON pelo tipo do parâmetro do listener**, e não pelo cabeçalho `__TypeId__`, que aponta para a classe do order-service.

**Assistente**

- **`ChatClient` criado a partir do `ChatClient.Builder`** autoconfigurado pelo Spring AI.
- **O cardápio é lido do banco a cada pergunta** (nome, descrição, preço e estoque) e entra na system message. Prato sem estoque aparece como `OUT OF STOCK`.
- **A descrição entra no prompt** para o modelo saber, por exemplo, que o Veggie Bowl é vegetariano.
- **As instruções estão em inglês** (código em inglês), com a regra de responder em português.
- **`temperature = 0.2`** para respostas mais previsíveis.

**Segurança**

- **Nenhuma URL com `localhost` entre serviços:** o pagamento é chamado pelo nome no Eureka. Os `localhost` que existem nos arquivos de configuração apontam para a infraestrutura (Eureka e RabbitMQ), não para outros serviços.
- **Nenhuma chave de API no repositório:** a chave é lida da variável de ambiente `OPENAI_API_KEY`.

## Problemas conhecidos

**Porta 8083 ocupada**

- **Erro:** `Web server failed to start. Port 8083 was already in use.`
- **Causa provável:** o Expo (app do professor) ocupou a 8083 porque a 8081 estava com o payment-service.
- **Solução:** subir o Expo com `--port 8090`. Para descobrir e encerrar o processo na porta:

  ```powershell
  netstat -ano | findstr :8083
  tasklist /FI "PID eq <PID>"
  taskkill /PID <PID> /F
  ```

  O PID é o número da última coluna do `netstat`.

**Assistente: log `401: Invalid API Key`**

A chave está errada (copiada pela metade ou com espaço) ou foi apagada. Gerar uma nova e definir `OPENAI_API_KEY` de novo.

**Assistente: log `Request failed`**

Acontece em redes que inspecionam HTTPS (como a de laboratórios), cujo certificado o Java não conhece. No Windows, antes do `bootRun`, no mesmo terminal:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStoreType=Windows-ROOT"
```

Faz o Java usar os certificados do Windows. Fora dessas redes, não é necessário.

## macOS e Linux

Os comandos de execução mudam pouco:

```bash
docker compose up -d
./gradlew :eureka-server:bootRun
./gradlew :payment-service:bootRun
./gradlew :payment-service:bootRun --args='--server.port=8082'

export OPENAI_API_KEY="sua-chave"
export OPENAI_BASE_URL="https://api.groq.com/openai/v1"
export OPENAI_MODEL="openai/gpt-oss-120b"
./gradlew :order-service:bootRun

./gradlew :review-service:bootRun
./gradlew :order-service:test
```

Nos testes com `curl`, use `curl` em vez de `curl.exe` e escreva o JSON sem as barras invertidas:

```bash
curl -i -X POST http://localhost:8080/orders -H "Content-Type: application/json" -d '{"dishId": 1, "quantity": 2}'
```
