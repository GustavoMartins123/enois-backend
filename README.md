# Rede — Social Platform Backend

Rede is a social platform backend built with **Spring Boot 4** (Java 25) following **hexagonal architecture** (ports & adapters).

## Prerequisites

- **JDK 25** (Eclipse Temurin recommended)
- **Maven** (or use the bundled `mvnw` wrapper)
- **Docker** (optional — for MariaDB/MySQL or running via docker-compose)

## Quick Start (dev profile)

```bash
./mvnw spring-boot:run
```

This starts with the `dev` profile using an **H2 in-memory database**. Swagger UI is available at [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html).

## Profiles

| Profile | Database    | How to activate                          |
|---------|-------------|------------------------------------------|
| `dev`   | H2 (memory) | `SPRING_PROFILES_ACTIVE=dev` (default)   |
| `hml`   | MariaDB     | `SPRING_PROFILES_ACTIVE=hml`             |
| `prd`   | MySQL       | `SPRING_PROFILES_ACTIVE=prd`             |

### hml (MariaDB)

```bash
# Start MariaDB
docker run -d --name rede-mariadb \
  -e MARIADB_ROOT_PASSWORD=root \
  -e MARIADB_DATABASE=rede_hml \
  -e MARIADB_USER=rede_hml \
  -e MARIADB_PASSWORD=rede_hml \
  -p 3306:3306 mariadb:11

# Run with hml profile
SPRING_PROFILES_ACTIVE=hml ./mvnw spring-boot:run
```

### prd (MySQL 8+)

```bash
# Start MySQL
docker run -d --name rede-mysql \
  -e MYSQL_ROOT_PASSWORD=root \
  -e MYSQL_DATABASE=rede_prd \
  -e MYSQL_USER=rede_prd \
  -e MYSQL_PASSWORD=rede_prd \
  -p 3307:3306 mysql:8

# Run with prd profile
SPRING_PROFILES_ACTIVE=prd ./mvnw spring-boot:run
```

## Building

```bash
./mvnw clean package -DskipTests
```

Produces `target/rede-0.0.1-SNAPSHOT.jar`.

## Running Tests

```bash
./mvnw test
```

All 24 integration tests must pass.

## Docker

```bash
# Build and start (defaults to dev profile with H2)
docker compose up --build

# With hml profile and MariaDB
docker compose --profile hml up --build
```

## API Documentation

- **Swagger UI**: [http://localhost:8081/swagger-ui.html](http://localhost:8081/swagger-ui.html)
- **OpenAPI JSON**: [http://localhost:8081/api/openapi.json](http://localhost:8081/api/openapi.json)

## Monitoring

- **Health**: `GET /actuator/health`
- **Metrics**: `GET /actuator/metrics`
- **Prometheus**: `GET /actuator/prometheus`

## Architecture

The project follows **hexagonal architecture**:

```
src/main/java/com/socialcoisa/rede/
├── {domain}/domain/         # Entities, value objects, domain services
├── {domain}/application/    # Use cases / ports (inbound)
├── {domain}/adapters/in/    # REST controllers (inbound adapters)
└── {domain}/adapters/out/   # JPA/HTTP repositories (outbound adapters)
```

Modules: `identity`, `feed`, `media`, `network`, `profile`, `messaging`, `notifications`, `search`, `community`, `admin`.

## Deploy (EKS via GitOps)

O deploy **não** se faz por `fly deploy`. O fluxo é:

1. Push em `main` de `florestaencantada/enois-backend`.
2. GitHub Actions (`.github/workflows/build-and-push.yml`) builda a imagem Docker, publica no ECR
   (`818928662779.dkr.ecr.us-east-1.amazonaws.com/enois/backend:<sha>`) e atualiza o manifesto em
   `florestaencantada/integration_gitops` → `clusters/forest-dev/applications/enois-app.yaml`.
3. O Argo CD (Application `forest-dev-applications`) sincroniza e faz o rollout no cluster `forest-dev`,
   namespace `forest-apps`, Deployment `socialcoiso-back`.

### Roteamento (importante)

O `HTTPRoute` do gateway faz `URLRewrite ReplacePrefixMatch: /` para o caminho `/api`, ou seja, **remove o
prefixo `/api` antes de encaminhar ao backend**. Por isso os controllers deste projeto são mapeados na
**raiz** (`/auth/...`, `/feed`, `/health`) e o prefixo `/api` é responsabilidade do gateway. O front sempre
chama `https://enois.ia.br/api/...`. Esse é o mesmo padrão do app irmão (skillify) — não adicione `/api`
nos `@RequestMapping` do backend.

Aviso de segurança decorrente disso: como o gateway encaminha `/api/*` **sem autenticação**, tudo que o
backend expõe fica público em `https://enois.ia.br/api/...` — inclusive `/api/actuator/*` (que chega em
`/actuator/*`) e `/api/api/openapi.json`. Por isso os defaults abaixo são restritivos.

## Configuração e ações manuais

### Variáveis de ambiente (runtime do Deployment)

| Variável | Obrigatória | Observação |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | sim | `qa` no cluster |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | sim | vêm do Secret `socialthing-db-secrets` |
| `REDE_ADMIN_EMAILS` | sim | allowlist (separada por vírgula) para `/admin/reports/**`. **Vazio = ninguém é admin.** |
| `REDE_FRONTEND_URL` | sim | base do front; o callback OAuth volta em `${REDE_FRONTEND_URL}/auth/callback` |
| `REDE_BACKEND_URL` | sim | base pública do back; monta os redirect URIs do OAuth |
| `FRONTEND_URL` | sim | link do e-mail de reset de senha: `${FRONTEND_URL}/auth/reset?token=...` |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | sim | SMTP do "esqueci minha senha". Sem isso o e-mail não sai (o erro é logado e engolido). |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | p/ login Google | |
| `APPLE_CLIENT_ID` / `APPLE_TEAM_ID` / `APPLE_KEY_ID` / `APPLE_PRIVATE_KEY` | p/ login Apple | |
| `IMAGEKIT_URL_ENDPOINT` / `IMAGEKIT_PUBLIC_KEY` / `IMAGEKIT_PRIVATE_KEY` | p/ mídia | |
| `CORS_ALLOWED_ORIGINS` | não | padrão `*` |
| `OAUTH_STATE_COOKIE_SECURE` | não | padrão `true`; use `false` só em dev http |
| `SPRINGDOC_ENABLED` | não | padrão `false`. `true` liga `/api/openapi.json` + Swagger UI (use só em dev/VPN) |
| `MANAGEMENT_ENDPOINTS` | não | padrão `health`. Para métricas use `health,prometheus` **e bloqueie `/api/actuator/*` no gateway** |
| `SUPPORT_EMAIL` | não | e-mail do time que recebe os pedidos de suporte. Sem ele o pedido ainda fica registrado e visível em `/admin/support` |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | p/ push no celular | par de chaves do Web Push (ver passo 7 abaixo). Sem elas o push fica desligado e só as notificações dentro do app funcionam |
| `VAPID_SUBJECT` | não | contato do emissor VAPID, no formato `mailto:contato@dominio` (padrão `mailto:suporte@enois.ia.br`) |

### Passo a passo das ações manuais

1. **Criar/atualizar o Secret do banco** (`socialthing-db-secrets`) com as chaves `host`, `port`, `dbname`,
   `username`, `password`. Sem isso o pod não sobe (Flyway falha no boot).
2. **Definir `REDE_ADMIN_EMAILS`** no Deployment com os e-mails que podem moderar. Enquanto estiver vazio,
   `/admin/reports/**` responde 403 para todos.
3. **Configurar o SMTP** (`MAIL_*`) e `FRONTEND_URL` para o reset de senha funcionar de ponta a ponta.
4. **Configurar o OAuth**: criar as credenciais no Google Cloud e no Apple Developer e cadastrar os
   redirect URIs exatamente como
   `${REDE_BACKEND_URL}/api/auth/oauth/google/callback` e `.../apple/callback`. Para Apple, use
   `response_mode=form_post` (o backend aceita o callback via POST).
5. **Rotacionar segredos vazados** (ver seção "Segurança").
6. Verificar o rollout: `kubectl -n forest-apps get pods -l app=socialcoiso-back` e
   `kubectl -n forest-apps logs deploy/socialcoiso-back`.
7. **Ligar as notificações push no celular (Web Push)**: gerar um par de chaves VAPID
   (`npx web-push generate-vapid-keys`), guardar a privada como Secret e definir `VAPID_PUBLIC_KEY`,
   `VAPID_PRIVATE_KEY` (e `VAPID_SUBJECT`) no Deployment. A chave **pública** é entregue ao app por
   `GET /push/config` — não precisa de variável no front. Depois de trocar as chaves, todas as inscrições
   antigas param de receber push (o usuário reativa no menu da conta); as inscrições expiradas (404/410 do
   serviço de push) são removidas sozinhas. Vale para Chrome/Edge/Firefox no Android e desktop; no iOS só
   funciona com o app **instalado na tela de início** (iOS 16.4+).

### Migrações travadas (rollout parado servindo versão antiga)

O DDL do MariaDB não é transacional: quando uma migração quebra **no meio**, o Flyway deixa a versão marcada
como `FAILED` em `flyway_schema_history` e passa a **recusar toda migração seguinte nesse banco**. O pod novo
não sobe, o ArgoCD não consegue promover e o cluster segue servindo a imagem antiga — sem erro visível de fora
(foi o que aconteceu com a V17, que tentava criar uma FK entre collations diferentes).

`FlywayMigrationConfig` limpa esse marcador no boot: se houver alguma migração em estado `FAILED`, roda
`repair()` (só remove entradas `FAILED` e realinha checksums) e migra de novo. Se ainda assim o pod não subir,
o caminho manual é:

```bash
kubectl -n forest-apps logs deploy/socialcoiso-back | grep -i -A20 flyway   # ver o erro real
delete from flyway_schema_history where version = '<versao que falhou>';    # ou flyway repair
```

## Fluxos de produto (referência rápida)

- **Vagas**: `POST /jobs` (exige reputação > 150) cria; `GET /jobs` lista (com `applied`/`saved`/`owner` por
  usuário); `GET /jobs/mine` = vagas que publiquei (traz `applicationsCount`); `GET /jobs/applications` = vagas
  em que me candidatei; `POST /jobs/{id}/applications` é a inscrição (`409` se repetida); `PUT`/`DELETE /jobs/{id}`
  são do autor. No app: `/vagas` (listar/criar/inscrever), `/vagas/{id}` (detalhe + inscrever/salvar/excluir) e
  `/vagas/minhas` (minhas vagas e minhas candidaturas).
- **Suporte**: `POST /support/requests` (autenticado) abre o pedido; `GET /admin/support` (admin) lista a fila em
  aberto. No app: `/suporte`, alcançável pelo menu da conta.
- **Consentimento**: `POST /auth/signup` exige `acceptedTerms: true` (termos + 18 anos); o instante do aceite é
  gravado em `users.terms_accepted_at`. Sem esse campo a resposta é `400 TERMS_NOT_ACCEPTED`.
- **Push no celular**: `GET /push/config` (público) informa se o push está ligado e devolve a chave pública
  VAPID; `POST /push/subscriptions` (autenticado) registra o dispositivo (`endpoint` + `keys.p256dh`/`keys.auth`)
  e `DELETE /push/subscriptions?endpoint=...` remove. Toda notificação criada por `NotificationService` também
  é enviada como push depois do commit (`PushNotificationListener` → `WebPushSender`); falha de push é logada e
  nunca quebra o fluxo que gerou a notificação. No app: menu da conta → "Ativar notificações no celular".

## Segurança — pendências que exigem ação manual

- **`dump_rede.sql` está versionado** com dados pessoais reais (e-mails, hashes de senha) — foi commitado
  com a mensagem "foda-se a LGPD". Remover do HEAD **não** apaga do histórico do Git. Ação: remover o
  arquivo, reescrever o histórico (`git filter-repo`) ou considerar o repositório comprometido, **trocar as
  senhas dos usuários afetados** e avaliar notificação conforme a LGPD.
- **Segredos em texto puro**: conferir e rotacionar `DB_PASSWORD`, credenciais de e-mail, chaves do
  ImageKit e credenciais OAuth caso tenham vazado (o `.env` do front já vazou a `IMAGEKIT_PRIVATE_KEY`).
- **`/admin/reports/**`** agora exige admin via `REDE_ADMIN_EMAILS`; antes qualquer usuário logado
  aprovava/descartava denúncias.
- **Recomendações** (`GET /recommendations/received|given/{userId}`) seguem **sem autenticação** por serem
  exibidas em perfis públicos — avalie se devem exigir login.

### Já corrigido

- **Doc da API público**: `GET /api/openapi.json` (stub manual) e `GET /api/api/openapi.json` + Swagger UI
  (springdoc) respondiam 200 sem autenticação, entregando o mapa completo da API. Agora o stub foi removido
  e o springdoc fica desligado por padrão (`SPRINGDOC_ENABLED=false`).
- **Actuator público**: `/api/actuator/prometheus` devolvia ~110 KB de métricas (JVM, pool de conexões,
  URIs) e `/api/actuator/metrics` idem. Agora só `health` é exposto e o índice `/actuator` também.
- **Sem headers de segurança**: adicionados HSTS, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`,
  `Referrer-Policy: no-referrer`, `Permissions-Policy` e CSP restritiva (`default-src 'none'`) nas respostas
  da API — ver `SecurityHeadersFilter`.
- **Email de terceiros** em `GET /users/{username}`, `/users/by-id/{id}`, `/users/search` e `/ranking`
  (era público, sem login): removido da projeção pública.
- **`/admin/reports/**`** exigia apenas sessão: agora exige admin (`REDE_ADMIN_EMAILS`).

Testes de regressão cobrem os dois primeiros itens em `ContractControllerTests`.
