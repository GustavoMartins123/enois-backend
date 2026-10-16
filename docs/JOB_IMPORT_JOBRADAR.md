# Importação de vagas externas — viabilidade do JobRadar

## Investigação (fonte: jobradar.com.br, verificada em 2026-10-16)
O brief exige investigar API/feed/sitemap/dados estruturados **antes** de implementar. Resultado:

| verificação | resultado |
|---|---|
| `robots.txt` | `User-agent: *` / `Allow: /` / **`Disallow: /*?`** / `Sitemap: /sitemap.xml` |
| `sitemap.xml` | **index** com 4 filhos: `sitemap-vagas.xml`, `-tecnologia`, `-empresa`, `-local` |
| `sitemap-vagas.xml` | **7.978 URLs** de vaga (ex.: `/vaga/<slug>-<id>`) — é um índice público do próprio site |
| página de vaga (HTML) | **~3 KB**: só o *shell* da SPA (`window.__…`), **sem** JSON-LD `JobPosting`, sem título/empresa/descrição no HTML |
| `/api`, `/api/jobs`, `/feed`, `/rss`, `/vagas.json` | 200 vazio — **soft-404 da SPA** (devolve o index), **não** é API |
| dados estruturados | **nenhum** (`application/ld+json` / `JobPosting` ausentes) |

## Conclusão
**Não há API pública, feed, nem dados estruturados.** O conteúdo da vaga **não está no HTML** — é
renderizado por JavaScript no cliente. Portanto:
- **Extração automática por HTTP simples não é viável** (não há dado para ler).
- A única forma de "raspar" seria **renderizar JS** (navegador headless): **frágil**, quebra a cada
  mudança de layout e esbarra nos **termos de uso** da fonte — o brief proíbe contornar proteções e
  manda priorizar mecanismos autorizados.

## Alternativas (sem simular resultado)
1. **Pedir autorização/API ao JobRadar** (ou um feed oficial). Se concedido, entra como um adaptador.
2. **Importação manual/CSV** (viável já): o administrador informa URL + campos (título, empresa,
   local, faixa, tipo, URL de candidatura) ou envia um CSV; o énóis normaliza, deduplica e publica
   como **vaga externa** com selo. Atende os critérios de aceite **sem** depender de scraping.
3. **Renderização headless** — só com **decisão explícita do responsável**, assumindo fragilidade e
   termos. Não implemento por padrão.

## Recomendação
Implementar o **CSV/manual** + a arquitetura plugável (`JobImportSource`) e o modelo de **vaga
externa** (selo + candidatura fora + dedupe + verificação de disponibilidade por URL canônica/cabeçalho),
deixando o adaptador do JobRadar **desligado** até haver API/autorização. Isso entrega os critérios
1, 3–8; o critério 2 (lote a partir de "listagem compatível") fica atendido pelo sitemap **quando**
a fonte publicar os dados de forma legível/autorizada (hoje não publica).

> Não declaro a integração com o JobRadar funcional: a fonte não permite extração legítima e
> confiável hoje. Este documento registra a limitação, como o brief exige.
