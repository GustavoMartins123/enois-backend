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
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | sim | SMTP dos e-mails de reset de senha e de confirmação de cadastro. Sem isso nenhum e-mail sai (o erro é logado e engolido). |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | p/ login Google | |
| `APPLE_CLIENT_ID` / `APPLE_TEAM_ID` / `APPLE_KEY_ID` / `APPLE_PRIVATE_KEY` | p/ login Apple | `APPLE_PRIVATE_KEY` é o **conteúdo** da chave `.p8` (PEM PKCS#8), com `\n` ou quebras reais. Ela é usada para montar o `client_secret` (JWT ES256) exigido na troca do `code`. |
| `IMAGEKIT_URL_ENDPOINT` / `IMAGEKIT_PUBLIC_KEY` / `IMAGEKIT_PRIVATE_KEY` | p/ mídia | |
| `CORS_ALLOWED_ORIGINS` | não | padrão `*` |
| `SPRINGDOC_ENABLED` | não | padrão `false`. `true` liga `/api/openapi.json` + Swagger UI (use só em dev/VPN) |
| `MANAGEMENT_ENDPOINTS` | não | padrão `health`. Para métricas use `health,prometheus` **e bloqueie `/api/actuator/*` no gateway** |
| `SUPPORT_EMAIL` | não | e-mail do time que recebe os pedidos de suporte. Sem ele o pedido ainda fica registrado e visível em `/admin/support` |
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | p/ push no celular | par de chaves do Web Push (ver passo 7 abaixo). Sem elas o push fica desligado e só as notificações dentro do app funcionam |
| `REDE_SECURITY_ENCRYPTION_KEY` | p/ 2FA por app | chave de 32 bytes em base64 (`openssl rand -base64 32`) que cifra os segredos TOTP em repouso (AES-GCM). **Sem ela, o 2FA por aplicativo authenticator fica indisponível** (erro explícito, sem guardar segredo em texto puro); o 2FA por código de e-mail continua funcionando |
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
   `response_mode=form_post` (o backend aceita o callback via POST) e informe a chave `.p8` em
   `APPLE_PRIVATE_KEY` — sem ela a troca do `code` responde `invalid_client` e o login Apple não
   conclui. Depois de configurar, valide um login real: o backend não simula provedor.
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

- **Vagas**: `POST /jobs` (exige reputação **> 150**) cria; `GET /jobs` lista (com `applied`/`saved`/`owner` por
  usuário); `GET /jobs/mine` = vagas que publiquei (traz `applicationsCount`); `GET /jobs/applications` = vagas
  em que me candidatei; `POST /jobs/{id}/applications` é a inscrição (`409` se repetida); `PUT`/`DELETE /jobs/{id}`
  são do autor. No app: `/vagas` (listar/criar/inscrever), `/vagas/{id}` (detalhe + inscrever/salvar/excluir) e
  `/vagas/minhas` (minhas vagas e minhas candidaturas).
- **Eventos vencidos são sanitizados**: o fim do evento vem da própria data (ISO `yyyy-MM-dd`, ou
  `dd/MM/yyyy`; o horário é opcional e sem ele vale o fim do dia). Passado o fim, o evento **sai da vitrine**
  (`GET /events` só devolve o que ainda vai acontecer; `includePast=true` traz o histórico com `expired: true`)
  e não aceita mais confirmação de presença (`400 EVENT_FINISHED`). Depois de **24h de folga**, o
  `EventSanitizationWorker` (agendado, roda no boot e a cada hora) apaga as presenças — dado pessoal de quem
  confirmou — guardando apenas o total agregado em `events.final_attendees_count`, e marca `sanitized_at`
  (idempotente). Após **365 dias** de retenção o evento é expurgado por inteiro. Data que não pode ser
  interpretada **não vence**: apagar dado pessoal por palpite seria pior que manter.
- **Candidatura com formulário próprio**: quem publica define campos personalizados (`applicationFields` no
  corpo de `POST /jobs`: `label`, `type` — TEXT, TEXTAREA, NUMBER ou LINK — e `required`), até 25, sem rótulos
  repetidos. `POST /jobs/{jobId}/applications` recebe `{message?, answers:[{fieldId,value}]}`: campos
  obrigatórios barram o envio (`400 MISSING_REQUIRED_ANSWERS`), o valor é validado pelo tipo
  (`400 INVALID_ANSWER`) e a candidatura grava um **retrato do perfil do candidato** naquele momento (nome,
  headline, local, habilidades, **tempo de experiência**, reputação e verificação). O empregador recebe por
  **e-mail** e na **conversa direta** (criada na hora entre os dois) e vê tudo em
  `GET /jobs/{jobId}/applications` (só o autor). Candidatar-se à própria vaga é recusado
  (`400 CANNOT_APPLY_TO_OWN_JOB`) e repetir devolve `409 ALREADY_APPLIED`. O detalhe da vaga traz
  `author` (nome, usuário, reputação e verificação) e os campos do formulário.
- **Busca booleana nas vagas**: `GET /jobs?query=...` entende `AND`/`E`, `OR`/`OU`, `NOT`/`NÃO`/`NAO`,
  `"frase exata"`, `-termo` (exclui), `+termo` (exige) e parênteses — sem operador, os termos valem como E
  (precedência: NOT > AND > OR). Os termos casam com título, empresa, local, tipo, nível e habilidades.
  A entrada nunca dá erro de sintaxe (parêntese/aspas soltos viram texto literal); o que existe é um teto de
  64 termos e 12 níveis de parênteses, e `%`/`_` são escapados para não virar coringa. Os filtros de nível,
  tipo, local e habilidades são **combinados** com a busca (antes eram um `else if` em cadeia e se anulavam).
- **Moeda da vaga**: o corpo de `POST /jobs` aceita `currency` (`BRL` padrão, `USD`, `EUR`) e `salary` só com os
  números — a aplicação remove o símbolo que o autor digitar e a exibição remonta o texto com a moeda escolhida
  (`R$ 8.000 – R$ 12.000`, `US$ 90,000`). O símbolo nunca fica duplicado nem trocado, inclusive em vagas antigas.
- **Acordo com a comunidade**: além da reputação, `POST /jobs` exige `communityAgreement: true` — a declaração de
  que a vaga é real e respeita as regras da comunidade. Sem ela a resposta é `400 COMMUNITY_RULES_NOT_ACCEPTED`,
  e o instante da declaração fica em `jobs.community_agreed_at`.
- **Bloqueio de usuário**: `POST /network/blocks/{userId}` bloqueia (definitivo), `DELETE …/{userId}` desbloqueia e
  `GET /network/blocks` lista. Bloquear esconde os dois lados em todo lugar — feed, comentários, notificações,
  busca e sugestões de conexão — derruba a conexão existente e impede nova solicitação (`403 BLOCKED`). O status
  de conexão (`GET /network/connections/{userId}/status`) devolve `blocked` para a UI alternar o botão.
  No app: botão no perfil (`/perfil/{username}`) e a lista em `/bloqueados`, no menu da conta.
- **Suporte**: `POST /support/requests` (autenticado) abre o pedido; `GET /admin/support` (admin) lista a fila em
  aberto. No app: `/suporte`, alcançável pelo menu da conta.
- **Consentimento**: `POST /auth/signup` exige `acceptedTerms: true` (termos + 18 anos); o instante do aceite é
  gravado em `users.terms_accepted_at`. Sem esse campo a resposta é `400 TERMS_NOT_ACCEPTED`.
- **Link compartilhado**: `GET /feed/posts/{postId}` e `GET /feed/posts/{postId}/reactions` são **públicos** —
  é para onde o link de compartilhamento aponta e o destinatário não tem sessão. A sessão, quando existe, só
  acrescenta o estado do visitante (reação) e esconde quem ele bloqueou. Escrever (comentar, reagir, salvar,
  compartilhar) continua exigindo sessão.
- **Confirmação de e-mail**: o cadastro **não** emite sessão. `POST /auth/signup` responde
  `{email, verificationRequired: true, emailSent}` e manda um link (`/auth/verify?token=...`) com token de
  hash SHA-256, validade de 24h e uso único; o instante da confirmação fica em `users.email_verified_at`.
  `POST /auth/email/verify` confirma **e já devolve a sessão**; `POST /auth/email/resend` reenvia (3 por hora
  por usuário, contando o cadastro, e no máximo 5 por 15 min por IP) e responde 202 mesmo para e-mail
  inexistente, para não virar oráculo de contas. Login de conta pendente responde `403 EMAIL_NOT_VERIFIED`, e
  qualquer endpoint autenticado responde o mesmo via `CurrentUserResolver` (segunda barreira). Contas criadas
  **antes** desta migração foram marcadas como verificadas (ver V22) para ninguém perder o acesso; quem entra
  por Google/Apple nasce verificado, porque o provedor confirma o e-mail.
- **Segundo fator pelo provedor**: quem entra por Google/Apple fica com `users.federated_second_factor_at`
  preenchido — conta verificada e 2FA satisfeito, mas **sem** um código nosso para desafiar (por isso o
  `requiredMethod` do login considera apenas TOTP e código por e-mail; o 2FA do provedor aparece como
  `providerSecondFactor` no `GET /auth/2fa/status`).
- **Login social**: todo fluxo começa em `GET /auth/oauth/{provider}/start`, que registra `state` + `nonce` no
  servidor (`oauth_authorization_requests`) e devolve a URL de autorização. O callback confere o `state`
  (existe, mesmo provedor, dentro de 15 min) e o `nonce` dentro do id_token, além de assinatura, emissor,
  audience e validade. **Vínculo com conta existente**: se já existe conta com o e-mail **e o provedor afirma
  que ele é verificado** (claim `email_verified`), o login vincula as duas — a conta entra com o e-mail
  confirmado e com o segundo fator delegado ao provedor (Google/Apple responde pela segunda etapa), sem
  duplicar conta e sem pedir configuração. E-mail apenas *declarado*, sem essa garantia, **não** vincula:
  responde `409 ACCOUNT_EXISTS_UNVERIFIED_EMAIL`. `POST
  /auth/oauth/{provider}/link` inicia a vinculação (o callback que usar esse state vincula em vez de entrar),
  `GET /auth/oauth/providers` lista os vínculos e `DELETE /auth/oauth/{provider}/link` desvincula — recusando
  com `409 LAST_LOGIN_METHOD` quando seria o último jeito de entrar (conta social não tem senha utilizável:
  `user_credentials.password_usable = false`). `POST /auth/oauth/{provider}/token` passou a exigir `state`.
- **Conta verificada e credibilidade**: conta verificada = **e-mail confirmado OU segundo fator ativo**
  (`users.email_verified_at`, `totp_enabled_at`, `email_otp_enabled_at`). Sem nenhum dos dois a reputação é
  **multiplicada por zero** (regra em `ReputationService`, fonte única: vale para o perfil, o ranking e a regra
  de publicar vaga) e as funcionalidades protegidas respondem `403 EMAIL_NOT_VERIFIED` no `CurrentUserResolver`.
- **Segundo fator (2FA)**: `GET /auth/2fa/status` mostra o estado. Por aplicativo authenticator (TOTP RFC 6238,
  segredo cifrado com `REDE_SECURITY_ENCRYPTION_KEY`): `POST /auth/2fa/totp/setup` devolve o segredo e a URI
  `otpauth://`, `POST /auth/2fa/totp/enable` confirma um código e devolve 8 códigos de recuperação (uso único,
  guardados como hash, mostrados uma vez). Por e-mail: `POST /auth/2fa/email/request` envia um código de 6
  dígitos e `POST /auth/2fa/email/confirm` ativa. Desativar exige a senha. No login, quem tem 2FA recebe `202`
  com `{twoFactorRequired, challengeId, method}` em vez da sessão e conclui em `POST /auth/2fa/verify`
  (`POST /auth/2fa/resend` reenvia o código por e-mail). O desafio vale 10 minutos, é de uso único e morre
  depois de 5 tentativas; um código de recuperação também conclui o login. No app: `/seguranca`, alcançável
  pelo menu da conta e pelo botão "Verificar identidade" no próprio perfil.
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

- **Credibilidade zerada sem verificação**: `ReputationService` passou a multiplicar a reputação por zero
  quando a conta não tem e-mail confirmado **nem** segundo fator — a regra fica numa fonte única, então
  perfil, ranking e o gate de publicar vaga concordam.
- **Nenhum e-mail era enviado**: `InfrastructureConfig` declarava um `JavaMailSender` com
  `new JavaMailSenderImpl()` e sem host/porta. Por ser um bean do usuário, ele fazia o
  `MailSenderAutoConfiguration` recuar (`@ConditionalOnMissingBean`) e o envio caía nos padrões do
  JavaMail (`localhost:25`), **ignorando `spring.mail.*`**. Resultado: reset de senha e confirmação de
  cadastro nunca saíam, com o erro apenas logado. O bean foi removido e o SMTP configurado passou a
  valer. O indicador `management.health.mail` também foi desligado: ele abria conexão com o SMTP a cada
  consulta e, com o servidor fora do ar, `/actuator/health` respondia 503 — tirando do ar uma aplicação
  saudável por causa de e-mail.
- **Mentoria sem agendamento**: `nextSlot` era sempre `null` (hardcoded) e o botão "Agendar" do app não
  tinha ação — agora o campo traz o próximo horário livre real e a agenda pode ser publicada e usada.
- **Login social vinculava conta pelo e-mail**: `findOrCreateUser` ligava a identidade a qualquer conta
  que tivesse o mesmo e-mail declarado pelo provedor, sem prova de posse. Agora esse caso responde
  `409 ACCOUNT_EXISTS_LINK_REQUIRED` e a vinculação é explícita, com `state`/`nonce` no servidor.

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
