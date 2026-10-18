# Deploy log — diagnóstico versionado

Este arquivo é o rastro das verificações de deploy feitas SEM acesso ao cluster
(somente HTTP público + GitOps + GitHub Actions). Atualizar a cada investigação.

## Como ler o estado do BACKEND ao vivo (sem kubectl)

- `GET /api/version` — endpoint público novo: versão/artefato do pod que está servindo,
  boot e estado do Flyway DESTA instância (`aplicadas`, `pendentes`, `falhadas`, `ultima`).
  - **404** aqui = o pod em produção é anterior a este endpoint (imagem velha no ar).
  - `falhadas > 0` ou o pod sem responder = migração travando o boot.
- `GET /api/games`, `/api/coin/cotacao`, `/api/loja` — features recentes; 404 = pod velho.
- `GET /api/health` — 200 sempre, inclusive no pod velho (não serve para versionar).

## Migrações — verificação local (MariaDB, collation de produção)

Receita: banco novo `utf8mb4 / utf8mb4_unicode_ci` e TODOS os arquivos de
`src/main/resources/db/migration` aplicados em ordem (`docker exec ... < Vn.sql`).

- **2026-10-18 (77 arquivos, V1→V82): 77/77 OK, zero duplicatas de versão.**
  (A V59 tem as colunas `text` — o row-size do InnoDB estourava com varchar(4000).)
- Regra que continua valendo: **nunca FK para `users`** (collation drift → errno 150
  no boot da produção) e DDL idempotente (`if not exists`).

## Histórico de sondas (UTC)

| quando | backend pod | front | observação |
|---|---|---|---|
| 2026-10-18 21:30 | `/api/games` 404 — pod velho | features novas no ar (chunks verificados) | manifest backend `0a90eb5` (CI verde) sem troca de pod |

## Checklist para quem tem kubectl (o que pedir quando o pod não troca)

1. `kubectl -n forest-apps rollout status deployment/socialcoiso-back` — o motivo aparece aqui.
2. `kubectl -n forest-apps get pods -l app=socialcoiso-back` — eventos:
   - **ImagePullBackOff** → role IRSA da SA `socialthing-sa` sem pull do ECR (ou imagePullSecrets);
   - **Pending** → falta de recurso no nó (requests cpu=1/memory=1Gi);
   - **CrashLoopBackOff** → ver `kubectl logs` do pod novo (Flyway/boot) — o `/api/version`
     do pod ATIVO também diz o estado do Flyway quando ele sobe.
3. `kubectl -n forest-apps describe pod <novo>` → seção Events é a resposta.
