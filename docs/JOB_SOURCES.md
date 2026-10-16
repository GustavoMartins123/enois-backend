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

## Jooble — **DISPONÍVEL (sitemap) / CONDICIONAL (API oficial)**
- `robots.txt` 200, permissivo (bloqueia só `/*.ashx`); **sitemap index público** (`/sitemap.xml`)
  apontando para `sitemap_tree_jdp_pt_BR_*.xml` (árvore de páginas de vaga).
- Existe **API oficial** (chave; a página `/api/about` fica atrás de challenge Cloudflare). Caminho
  limpo = pedir a **API key**; alternativa imediata = ler o **sitemap público** como no JobRadar.
- **Ainda não implementado.** Adaptador é o próximo passo (mesma porta).

## Remotar — **A CONFIRMAR (provável DISPONÍVEL via páginas SSR)**
- **Sem `robots.txt` e sem `sitemap.xml` (404)** — não há mecanismo publicado para indexar; por isso
  **não** é "disponível" por sitemap.
- É um app **Next.js com SSR** (HTML de 44 KB) — páginas de vaga podem trazer dados no HTML
  (`__NEXT_DATA__`/JSON-LD). Requer inspeção de uma página de vaga real para decidir entre
  **Disponível** (extração da página pública) ou **Manual**.
- **Ainda não implementado.** Precisa de uma passada de investigação da página de vaga.

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
