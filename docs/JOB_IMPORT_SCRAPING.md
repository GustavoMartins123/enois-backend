# Importação do JobRadar por scraping headless — autorização e desenho

## Autorização (registro)
> "autorizo o scraping headless do JobRadar, ciente de que é frágil e que os termos são de
> responsabilidade da plataforma" — responsável pela plataforma Énóis, 2026-10-16.

Fica registrado que: (a) **não é integração oficial/autorizada pelo JobRadar**; (b) é **frágil** e pode
quebrar sem aviso quando a fonte mudar; (c) **direitos autorais e termos de uso são de
responsabilidade da plataforma**.

## Por que headless (e não HTTP)
A página de vaga é uma **SPA**: o HTML (~3 KB) só tem o *shell* e meta tags genéricas do site; os
dados (empresa, local, faixa, data, descrição) são montados por JavaScript no cliente. **Requisição
HTTP simples não retorna nada.** Extrair os campos exige **renderizar o JS** (navegador headless).

`robots.txt` do JobRadar: `Allow: /` com `Disallow: /*?` — as páginas de vaga (`/vaga/<slug>-<id>`)
não têm query string, então **robots permite a página**.

## Desenho (a implementar)
1. **Dependência:** Playwright (Chromium) no backend — imagem maior e mais RAM; **pin de versão** e
   navegador baixado no build da imagem.
2. **Descoberta:** sitemap público (`/sitemap-vagas.xml`) — já implementado em `JobRadarJobSource`.
3. **Enriquecimento (headless):** para cada slug, abrir `/vaga/<slug>-<id>`, esperar o conteúdo e
   extrair: **título, empresa, local, faixa salarial, tipo, data de publicação, descrição, URL**.
   Campo ausente fica **nulo** — nada inventado.
4. **Persistência:** as importadas passam a ser **gravadas** com marca de origem:
   `sourceType=EXTERNAL`, `sourcePlatform=JobRadar`, `sourceUrl`, `sourceExternalId`,
   `sourceImportedAt`, `sourceLastCheckedAt`, `externalStatus`. **Selo explícito na UI:**
   **“Vaga externa · JobRadar — não criada no Énóis”** + botão **“Visitar vaga”** (candidatura fora).
5. **Dedupe:** id externo → URL canônica normalizada → (empresa+título+local), com proteção de
   concorrência no banco.
6. **Worker agendado:** `@Scheduled` com **teto por execução** (ex.: 20–50), **backoff** e
   **User-Agent identificado**; **uma página por vez**; sem paralelismo agressivo.
7. **Revalidação:** periodicamente, revisitar para atualizar campos e marcar `externalStatus`
   (ENCONTRADA / POSSIVELMENTE_ENCERRADA), diferenciando **erro temporário** de **encerramento**;
   **nunca apagar** por falha isolada (mantém histórico; oculta das buscas padrão).
8. **Resiliência:** falha da fonte **não** afeta as vagas internas; lote com falhas parciais continua.
9. **Testes:** parse/normalização com HTML **fixture** (sem rede), dedupe, limites, campos ausentes,
   e “falha não apaga/marca como encerrada”.

## Alternativa preferida
A **API oficial** (`https://api.jobradar.com.br/v1/jobs`, responde `"API key required"`) continua
sendo o caminho **limpo e estável** — se o JobRadar conceder a chave, este scraper pode ser
**desligado** e a mesma porta `JobImportSource` passa a ler a API.

## Estado
**Não implementado ainda.** Exige adicionar Playwright/Chromium ao backend (dependência de imagem),
o worker agendado e a persistência com marca de origem. Ver `BACKLOG.md` (item 9).
