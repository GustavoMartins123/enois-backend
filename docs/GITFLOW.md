# Gitflow e regras de PR

Regra de ouro: **nada entra em produção sem passar por QA.** `main` é produção, `qa` é o
ambiente de validação, e PR é o único caminho para as duas.

## Branches

| Branch | Papel | Deploy |
|---|---|---|
| `main` | produção | automático ao mergear (só vem de `qa` ou de `hotfix/`) |
| `qa` | ambiente de QA | automático a cada merge |
| `feat/<assunto>` | funcionalidade | nenhum |
| `fix/<assunto>` | correção | nenhum |
| `hotfix/<assunto>` | correção urgente de produção | ver seção Hotfix |

- Branch nova sempre sai de `qa` (nunca de `main`), com nome curto e em kebab-case.
- Rebase na `qa` atualizado antes de abrir o PR; sem merge de `main` para dentro da feature.
- Commits pequenos e com mensagem curta. **Em correção de segurança, a mensagem não explica a
  falha** (repositório é público): assunto neutro, detalhe só na descrição interna/PR privado.

## Fluxo normal

1. `git switch qa && git pull` → `git switch -c feat/minha-coisa`
2. Implementa, roda testes e build local.
3. Abre PR **para `qa`** (não para `main`).
4. CI verde + **1 aprovação** → merge (squash).
5. Deploy automático em QA. Validação funcional no ambiente.
6. Abre PR **de `qa` para `main`** (mesma árvore, sem edições novas) → CI verde + aprovação → merge.
7. Deploy em produção.

## Hotfix (produção quebrada)

1. Branch de `main`: `hotfix/<assunto>`.
2. PR para `main` com prioridade e descrição do impacto.
3. Merge → deploy de produção.
4. **Obrigatório**: abrir PR do `hotfix` para `qa` (para o QA não divergir) e post-mortem curto
   em `docs/incidentes/` quando for incidente.

## Regras de PR

**Título**: convencional (`feat:`, `fix:`, `chore:`, `docs:`, `refactor:`, `test:`), curto.

**Descrição** (template):
- O que muda e por quê (2–5 linhas).
- Como testar (passos e/ou curl).
- Riscos e rollback (quando tocar dinheiro, auth, migração ou dados).
- Print/vídeo se mexer em tela (mobile e desktop).

**Proibições**:
- Segredo, token, chave, dump de banco ou dado pessoal no código, no PR ou no commit.
- Push direto em `main` e `qa` (branch protection: sem force-push, sem bypass).
- PR gigante: acima de ~400 linhas alteradas, divida.
- Detalhar a correção de segurança na mensagem pública do commit.
- Merge com CI vermelho ou com conflito resolvido "no olho".

**Obrigatório para aprovar**:
- CI verde (lint, testes, build).
- Migração Flyway com número novo (nunca reutilizar V já aplicada) e testada.
- Se muda contrato de API: atualizar `docs/` e o portal `/developers`.
- Se mexe em permissão: teste que prova o 403 para quem não pode.

## Proteções de branch (configurar no GitHub)

`main` e `qa`:
- Require pull request (1 aprovação) e dismiss stale approvals.
- Require status checks: CI do repositório.
- Require linear history; block force-push e deletion.
- `main` só aceita PR vindo de `qa` ou `hotfix/*`.
