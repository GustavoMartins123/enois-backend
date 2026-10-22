# Agregador de vagas externas — investigação e classificação das fontes

Arquitetura comum: a porta `jobs.application.port.out.JobImportSource` (descoberta) + tabela própria
`external_jobs` + worker (boot/meia-noite) + listagem com selo de origem. **Adicionar uma fonte = um
adaptador**; não há lógica duplicada de persistência/validação/dedupe.

Classificação pedida pelo brief. **Estado real de cada fonte (investigado em 2026-10-16):**

## JobRadar — **DISPONÍVEL** (implementado)
- `robots.txt`: `Allow: /` (bloqueia só `?`); `sitemap-vagas.xml` com ~7.978 URLs de vaga.
- Sem API pública (o `/v1/jobs` responde `401 API key required`; o `/api/*` da SPA exige request
  token — **não usado**). Páginas são SPA (HTML sem dados), então enriquecemos apenas com o que o
  sitemap traz: **título (do slug) + URL + id**.
- Implementado: `JobRadarJobSource` (sitemap), tabela `external_jobs`, selo "Vaga externa · JobRadar"
  + botão "Visitar vaga", busca por título, cache e refresh periódico.

## Jooble — **CONDICIONAL** (só com a API oficial)
- `robots.txt` 200, permissivo (bloqueia só `/*.ashx`); **sitemap index público** com filhos
  `sitemap_tree_jdp_pt_BR_*.xml` — testado: o filho responde 200 e traz URLs `/jdp/<numero>`.
- **MAS** (duas coisas decidem):
  1. a URL é **opaca** (`/jdp/6125900633590463951`): **não traz título** — diferente do JobRadar, cujo
     slug carrega o título. Só com o sitemap teríamos "vaga sem título".
  2. a **página da vaga responde 403 "Just a moment..."** — é o **desafio do Cloudflare (anti-bot)**.
     O brief proíbe contornar bloqueios ⇒ **não lemos a página**.
- Conclusão: o caminho legítimo é a **API oficial do Jooble** (`client key`), via `/api/<key>`. Sem a
  chave, a fonte fica **desabilitada**. **Ainda não implementado.**

## Remotar — **A CONFIRMAR / provável MANUAL**
- **Sem `robots.txt` e sem `sitemap.xml` (404)** — não há mecanismo publicado para indexar.
- É **Next.js (Pages Router)** (`__NEXT_DATA__` no HTML), mas a **home não expõe links de vaga**
  (só páginas institucionais) e `/vagas`, `/remote-jobs`, `/vagas-remotas` respondem **404** — a
  listagem parece ser montada no **cliente**. **Sem navegador eu não confirmo o formato real** da
  listagem/detalhe.
- Próximo passo: inspecionar a listagem real (com browser headless) para achar o path e ver se há
  dados no HTML. Até lá, **MANUAL** (cadastro por URL) é o que se pode afirmar.

## Indeed — **CONDICIONAL**
- `robots.txt` com vários `Disallow`; **sem sitemap (404)**; termos restritivos.
- O acesso oficial é o **programa de parceiros / Publisher API**, que exige **aprovação e
  credenciais** (`https://docs.indeed.com/`). **Sem acesso autorizado hoje → fonte desabilitada**
  para importação automática. Alternativa compatível: **importação manual por URL**.
- **Não implementado** (e **não** será por scraping).

## Trovit — **INDISPONÍVEL (no Brasil)**
- `emprego.trovit.com.br` **redireciona para `www.adzuna.com.br`** (403) — a operação BR foi
  incorporada pela Adzuna. Como o brief pede para **não** tratar a fonte errada como origem, esta URL
  não serve.
- Futuro: **Adzuna** tem API oficial (chave/parceria) → seria **CONDICIONAL**. Não implementado.

## Resumo
| fonte | classificação | mecanismo |
|---|---|---|
| JobRadar | **Disponível** | sitemap público (implementado) |
| Jooble | **Disponível / Condicional** | sitemap público **ou** API oficial (chave) |
| Remotar | **A confirmar** | páginas Next SSR (inspeção pendente) |
| Indeed | **Condicional** | Publisher API (aprovação) — sem scraping |
| Trovit | **Indisponível** | redireciona para Adzuna; futuro = API da Adzuna |

## Modelo de procedência (a implementar na Fase 1)
Distinguir, na `external_jobs` (e no view): **fonte de descoberta** (`platform`, já existe),
**origem original** (`originalOrigin`, quando identificável) e **destino da candidatura**
(`applyUrl`, hoje = `url`). Hoje `platform` + `url` cobrem descoberta e destino; **`originalOrigin` e
`applyUrl` separados ainda não existem**.

## Deduplicação
Hoje: por `(platform, external_id)`. **Dedupe global entre fontes** (id original → URL canônica →
URL de candidatura normalizada → empresa → título normalizado) **ainda não** está implementada — é o
item central da Fase 1 do brief.

## Não implementado (para não fingir)
Nenhum adaptador além do JobRadar. Jooble/Remotar/Indeed/Trovit estão classificados, **não**
implementados; nenhuma integração fictícia foi criada.
