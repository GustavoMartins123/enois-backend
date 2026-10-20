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
