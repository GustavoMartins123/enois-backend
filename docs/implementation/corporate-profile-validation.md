# PERFIL PROFISSIONAL — VALIDAÇÃO

> Registro das validações reais: comandos executados e resultados. Nada de "deveria passar".

## 2026-10-20 — Fase 0

- Auditoria documentada no plan (estudo do código real, não suposições).
- Documentos criados e commitados no `rede` (backend) — commit pendente no próximo ciclo
  junto com a Fase 1 (o git status da árvore tem trabalho não commitado de outra sessão;
  commitar apenas os arquivos desta pasta).
- Automação de 40 minutos: CRIADA de fato — id automation-66c1edea-6784-4bdf-9f72-99cb2b08f06c,
  título "Implementar o plano do perfil profissional a cada 40 minutos", agenda real de 40 em 40
  minutos. Ela lê estes documentos e retoma o trabalho; remove a si mesma no gate final.
- Sem testes novos nesta fase (nada de código mudou ainda).

## 2026-10-20 — Fases 1 e 2

**Testes (worktree isolado, H2, migração V107 aplicada pelo Flyway):**

- `mvn test -Dtest='PostAuthorIdentityTests,CorporateProfileTests,CorporateProfilePersistenceTests'`
  → **12 testes, 0 falhas** (exit 0):
  - `PostAuthorIdentityTests` (4): publicação social é SOCIAL por padrão; BUSINESS é
    CORPORATE; identidade nula não existe (restore legado vira SOCIAL); editar
    conteúdo/tipo preserva a identidade.
  - `CorporateProfileTests` (5): nasce DRAFT sem nada copiado do social; formatos de @
    aceitos e recusados; ciclo DRAFT→ACTIVE→DISABLED→reabrir sem perder dados;
    renomear a apresentação não muda o @.
  - `CorporateProfilePersistenceTests` (3): guarda e busca por user/username; **a
    unicidade do @ profissional é do banco** (segunda conta com o mesmo @ → 409
    USERNAME_TAKEN); o namespace profissional não colide com nada do social.
- `MomentExpiryTests` exigia a assinatura antiga do restore: **sobrecarga adicionada**
  (commit `0b4db2c`) — toda chamada legada compila e volta SOCIAL.

**Bancada MariaDB 11.4 (docker enois-bench66, banco scratch `enois_corp`):**

- V107 aplicada **duas vezes** sem erro (idempotente): tabela `corporate_profiles`
  criada com índice único em `username`; coluna `author_identity varchar(20) NOT NULL
  DEFAULT 'SOCIAL'` adicionada em `posts` (com a tabela na forma real do V1+alterações).
- Insert legado sem informar identidade → gravado como **SOCIAL**; update explícito →
  **CORPORATE** lido de volta. Banco scratch descartado após a validação.
- Compatibilidade comprovada: nenhum registro existente muda de mãos (default SOCIAL).


## 2026-10-20 — Fases 3–6 (serviço, BUSINESS, separação de feeds)

**Testes (árvore compartilhada e worktree isolado):**

- `BusinessFeedSeparationTests` (3, verdes): a mesma conta publica um social e um
  BUSINESS — o social aparece em Tuto/Following/perfil social, o BUSINESS não; o
  BUSINESS existe apenas no feed corporativo e na página do perfil profissional;
  BUSINESS sem perfil ativo → 403 NO_ACTIVE_CORPORATE_PROFILE; editar não troca
  identidade (400 IDENTITY_TYPE_MISMATCH); perfil inativo não tem página (404).
- `PostAuthorIdentityTests` (4) e `CorporateProfileTests` (5): verdes (fase 1).
- **Suíte completa no worktree isolado (commit 6472cbe): 346 testes, 2 falhas —
  ambas em módulos de OUTRAS sessões** (media LazyMigration e SupportCategories,
  trabalho em voo no repositório), nenhuma no escopo deste plano. Módulos deste
  plano (feed, corporate, linkedin share) 100% verdes.

**Correções feitas durante a validação:**

- As queries "sociais" do repositório estavam invertidas (retornavam o que NÃO
  era social); corrigidas para `authorIdentity = :social` (commit 6472cbe).
- `FeedDistributionService`: post CORPORATE não entra no fanout do cache social.
- `FeedCorporateController` + rotas `/feed/corporate` e `/corporate/{username}/posts`.
- `CorporateProfileController` (endpoints /me/business-profile) com a regra de
  ativação exigindo headline.


## 2026-10-20 — Extensão: personalização (Fases A, B, C)

- `PersonalizationTests` (5 verdes): defaults vazios + persistência; valores
  inválidos recusados (taxonomia e módulos); conflito liked+blocked → 409;
  limpar volta ao padrão; tema preservado na mesma linha.
- `PersonalizationFeedTests` (1 verde, ponta a ponta): comunidade de tecnologia
  → post herda o assunto → leitor bloqueia → post some do feed DO LEITOR →
  autor (sem bloqueio) continua vendo → desbloquear traz de volta.
- Correções: V108 dividida em instruções únicas (H2); tema da conta virou
  nullable; grafia TECNOLOGIA (a taxonomia é em português).


## 2026-10-20 — Fase 4 (importação com consentimento)

- `CorporateImportConsentTests` (4 verdes): a prévia mostra headline/bio do social SEM
  gravar nada; importar só "headline" traz só ela (bio continua vazia, nome
  profissional preservado); seleção vazia → 400 NO_FIELDS_SELECTED; campo desconhecido
  → 400 INVALID_IMPORT_FIELD.
- Correção durante a validação: usernames únicos por teste (o H2 da classe é o mesmo —
  username repetido entre testes dava 409).


## 2026-10-20 — Fase 7 (vagas/candidaturas)

- `JobCorporateApplicationTests` (2 verdes): candidato com perfil profissional ATIVO →
  retrato congelado identity=CORPORATE, nome/headline/indústria do alterego; candidato
  sem perfil → identity=SOCIAL, nome social. `JobServiceTests` segue 20/20 (o fake de
  repositório corporativo vazio foi adicionado ao construtor do serviço).
- Correções: reputação mockada (>150) como nos testes de vaga existentes; o apply
  responde 201 (não 200).


## 2026-10-20 — Fase 8 (auditoria busca/notificações/salvos)

- Auditoria endpoint por endpoint registrada no progress (tabela). Achados: a busca não
  indexa posts (sem vazamento por construção); salvos e link direto são acesso explícito
  (corretos); abas por tipo não casam com BUSINESS.
- `BusinessFeedSeparationTests` ampliado: as abas DISCUSSIONS e PROJECTS também provam
  ausência do post BUSINESS (3/3 verdes).


## 2026-10-20 — Fases 9/10 (frontend do perfil profissional)

- `npx eslint` nos arquivos tocados: 0; `tsc --noEmit`: 9 erros (baseline
  pré-existente, nenhum nos arquivos do plano); `npx vitest run`: 95 testes verdes
  (os 2 "arquivos falhos" são as regras de ESLint, pré-existentes).
- Página /profissional e seletor de identidade no menu implementados; commit local
  no front (push bloqueado pelo plano).


## 2026-10-20 — Fase 11 (compositor e feed corporativo)

- `active-identity.test.ts` (3 verdes): efetiva é CORPORATE só com perfil ATIVO;
  sem perfil/DRAFT/DISABLED → SOCIAL; escolha social continua social.
- `npx vitest run`: 98 verdes; `tsc` no baseline (nenhum erro nos arquivos do plano);
  eslint 0 nos arquivos tocados (3 erros de prettier pré-existentes em linhas de
  outras sessões no feed.tsx, fora dos meus hunks).


## 2026-10-20 — Extensão Fase D (módulos desativáveis, frontend)

- `module-paths.test.ts` (3 verdes): mapa área→módulo, incluindo /acontecendo→EVENTS;
  rotas fora de módulos desativáveis ficam livres (/feed, /perfil).
- `npx vitest run`: 101 verdes; eslint 0 nos arquivos tocados; tsc no baseline.


## 2026-10-20 — Extensão Fase E (interface das três seções)

- Diálogo de Uso saudável com as três seções; eslint 0; `tsc` no baseline;
  `npx vitest run`: 101 verdes. O conflito liked×blocked é impedido na própria tela
  (o backend continua validando e recusando com 409).
