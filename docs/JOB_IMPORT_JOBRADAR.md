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

## Achado decisivo: existe API oficial, mas exige chave (verificado em 2026-10-16)
O bundle da SPA referencia `https://api.jobradar.com.br`. Teste direto:

| endpoint | resposta |
|---|---|
| `GET https://api.jobradar.com.br/v1/jobs` | **401** `{"error":"API key required"}` |
| `GET https://api.jobradar.com.br/v1/jobs?limit=2` | **401** `API key required` |
| `GET https://api.jobradar.com.br/api/jobs` | **403** `{"error":"Missing request token"}` |
| `GET https://api.jobradar.com.br/api/sources` | **403** `Missing request token` |
| `GET https://api.jobradar.com.br/api/stats` | **403** `Missing request token` |
| `GET https://api.jobradar.com.br/v1` | 404 |

**Leitura:**
- **Existe uma API oficial versionada** (`/v1/jobs`) — ela responde JSON e **diz explicitamente "API key
  required"**. É o caminho **"via API"** correto: **precisa de uma chave que o JobRadar concede**.
- **`/api/*` (o backend interno da SPA) exige "request token"** — é a API privada do app deles.
  **Não** vamos usar: acessá-la exigiria o token deles, o que seria **contornar autorização** — o
  brief proíbe.
- **Não sei o formato do header de autenticação** do `/v1` (nome do header/esquema não é público).
  **Não vou inventar** ("Authorization: Bearer xyz") — contrato inventado é exatamente o que o brief
  proíbe. Precisamos da **documentação/credencial** do JobRadar (ou do exemplo de chamada deles).

## O que fazer para destravar o "via API"
1. **Pedir ao JobRadar** a chave de API (e a doc: base, header de auth, formato de paginação, limites).
2. Implementar `JobRadarJobSource` (porta `JobImportSource`) chamando `GET {base}/v1/jobs` com a
   credencial em variável de ambiente (`JOBRADAR_API_KEY`), **desligado** enquanto não houver chave
   (fail-safe), com dedupe por `sourceExternalId`/URL canônica e selo "Vaga externa · JobRadar".
3. Sem a chave, a importação fica **indisponível** — sem scraping da SPA e sem usar o `/api/*` interno.

> Atualização: a conclusão anterior ("só CSV/manual") fica **superada** por este achado — há API
> oficial; o bloqueio agora é **credencial + doc de autenticação**, não a ausência de API.

## Decisão: LISTAR + REDIRECIONAR (sem credencial, sem scraping de JS)
O objetivo é **listar as vagas do JobRadar no énóis e mandar a pessoa para o site deles** — não
republicar conteúdo. Isso é viável **sem** a API key e **sem** renderizar JS, usando o que o próprio
JobRadar publica para indexadores:
- **`/sitemap-vagas.xml`** (7.978 URLs de vaga), permitido no `robots.txt` (que só bloqueia `?`).
- De cada URL derivamos o **título** (do slug) + a **URL original** + o **id externo**. **Empresa,
  local e salário NÃO estão no sitemap** → ficam nulos (não inventamos).
- A vaga aparece como **"Vaga externa · JobRadar"** com o botão **"Candidatar-se no JobRadar"**,
  abrindo a URL original. **Sem reproduzir descrição/atributos** → sem problema de direitos.

Isso atende "listar + redirecionar" respeitando os limites da fonte. **Não** usamos `/v1` (exige
chave) nem `/api/*` (token da SPA). Limites: teto por chamada, User-Agent identificado, sem `?`.

Implementado: `jobs/adapters/out/imports/JobRadarJobSource` (porta `JobImportSource`),
`parse`/`humanize` cobertos por teste.
