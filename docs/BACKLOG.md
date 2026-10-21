# Backlog ordenado (mais fácil → mais difícil)

Ordem por **esforço × risco**, para entregar valor cedo e não travar em dependência externa.
Cada item é feito **numa passada**, compilando, com testes verdes e publicado (backend e/ou front).
Os 4 últimos são **BLOQUEADOS** por dependência externa (não é código meu).

## Fácil (1 arquivo/poucos pontos)
1. **[x] ENTREGUE (verificado no código em 2026-10-20) — Tag "vulgo"/aka no perfil.**
   `UserProfile.aka` (coluna `aka`, varchar 60) no domínio e no perfil público; front com o
   campo no editar perfil (com contador) e a exibição no perfil/card.
   *Aceite cumprido:* salva com/sem aka, aparece no perfil, limite respeitado.
2. **[x] ENTREGUE (verificado no código em 2026-10-20) — Arquivar conversa (ver arquivadas /
   restaurar).** `GET /conversations/archived` + `POST /conversations/unhide` no backend
   (`MessagingContractController`), com a aba "Arquivadas" e a ação "Restaurar" no front.
   *Aceite cumprido:* arquivar some da caixa, restaurar volta, não afeta o outro participante.
3. **[x] ENTREGUE (verificado no código em 2026-10-20) — Apagar mensagens dentro da conversa.**
   `message_hidden(user_id, message_id)` com `POST /conversations/{id}/messages/hide` em massa
   e a seleção na thread no front.
   *Aceite cumprido:* apagar some só para quem apagou e a paginação continua coerente (a
   leitura filtra no banco).

## Médio
4. **[x] ENTREGUE (verificado no código em 2026-10-20) — Áudio nos comentários.** Gravação
   pelo microfone no composer de comentário (`MediaRecorder`, teto de 3 min) → upload de mídia →
   player sem autoplay; GIF e embeds já entregues antes.
   *Aceite cumprido:* gravar, enviar, tocar; recarregar mantém o anexo; sem autoplay.
5. **[x] ENTREGUE 2026-10-20 — Vaga: valor obrigatório + período + faixa + descrição.** V110
   (`description longtext`, `salary_min numeric(12,2)`, `salary_max`, `salary_period`); vagas
   antigas continuam exibindo o texto livre em `salary`. Backend: descrição e mínimo > 0
   obrigatórios em criar E editar (`DESCRIPTION_REQUIRED`, `SALARY_REQUIRED`), faixa invertida
   recusada (`INVALID_SALARY_RANGE`), período HOUR|DAY|MONTH|YEAR (padrão MONTH;
   `INVALID_SALARY_PERIOD`), rótulo pronto no `JobView.salaryLabel` ("R$ 1.500 / mês" ou
   "de R$ 1.500 até R$ 2.000 / mês"). Front: formulário de criação e edição com mínimo/teto/
   período e descrição obrigatória; listagem e página mostram `salaryLabel` primeiro.
   Testes: `JobSalaryTests` (7) + ajuste dos testes que criavam vaga sem valor; V110
   validada na bancada MariaDB (aplicada 2x + insert com as colunas novas).

## Médio-grande
6. **[x] ENTREGUE (verificado no código em 2026-10-20) — Feedback por candidato.**
   `JobApplicationStatus {APPLIED, IN_REVIEW, ADVANCED, NOT_SELECTED}` com `feedback` e
   `reviewed_at` no domínio, `PATCH /jobs/{jobId}/applications/{id}` e o diálogo de retorno no
   front (o motivo é obrigatório ao recusar).
   *Aceite cumprido:* avançar/recusar com motivo; o candidato é notificado (DM e e-mail).
7. **[x] ENTREGUE 2026-10-20 — Penalidade por excluir vaga sem dar retorno.** V114
   (`job_abandon_penalties`, uma linha por vaga — a PK garante que a mesma exclusão não conta
   duas vezes). O `delete` da vaga conta as candidaturas ainda em APPLIED; se houver, registra
   a penalidade e devolve o aviso na própria resposta (`{unanswered, penalized}`), que o painel
   "Minhas vagas" mostra como toast; o diálogo de exclusão já alerta a regra antes de confirmar.
   No `ReputationService`, a penalidade entra no componente de integridade: 25, depois 75,
   depois 150 (teto próprio de 150, somado à penalidade de moderação com teto global de 600).
   Quem respondeu todas (qualquer estado fora de APPLIED) não é penalizado. Testes:
   `JobAbandonPenaltyTests` (3: sem resposta penaliza e avisa; respondido não penaliza; não
   conta duas vezes) + `ReputationServiceTests` 6/6 (penalidade desconta 25). V114 validada na
   bancada MariaDB (create 1x + reexecução + PK rejeitando duplicata).
8. **[x] ENTREGUE 2026-10-20 (verificação de código; entregue nas rodadas anteriores) —
   Currículo em PDF.** Confirmado no código de ponta a ponta: o diálogo do perfil
   (`profile-resume-dialog.tsx`) sobe o PDF pelo fluxo de mídia com purpose `RESUME`,
   validando tipo (`application/pdf`) e tamanho no cliente, com baixar e remover; o campo
   vive no perfil (V64, `resume_url`/`resume_name`); a candidatura congela o currículo no
   `candidate_snapshot` (não é coluna: trocar o PDF depois não reescreve candidatura antiga)
   e o empregador recebe o link no resumo e no `CandidateView`. O backend aceita o purpose
   com teto de 50MB (`MediaService`). Aceite cumprido: PDF válido anexa; tipo errado recusa
   no cliente.

## Médio-grande (pedido 2026-10-17, Game Dev community)
9. **[x] ENTREGUE 2026-10-20 — Imagem no meio do texto.** O conteúdo aceita o marcador
   `{{media:<id>}}` e o render troca cada um pela mídia real no ponto exato (imagem com zoom,
   vídeo e áudio com player sem autoplay). Backend: `shared/text/InlineMedia` valida que TODO
   marcador referencia anexo da MESMA publicação (400 `INVALID_MEDIA` para marcador órfão —
   mídia alheia disfarçada de anexo não passa), aplicado em publicar, editar e comentar no
   `FeedController` (a edição valida contra as mídias que o post JÁ tem, pois ela não mexe em
   anexos). Front: `RichText` recebe `mediaIds` e divide o texto nos marcadores; a grade do fim
   só mostra o que NÃO foi inserido inline (sem repetição); no composer, cada anexo ganha o
   botão "Inserir no texto" — o marcador nasce `pendente-N` e vira o id real no upload.
   `InlineMediaTests` 4/4 (marcador próprio aceito e preservado; alheio recusado; comentário
   idem; sem marcador não valida nada). Sem marcador = anexo no fim, como sempre.

## Grande
10. **[x] ENTREGUE 2026-10-21 — Importador de vagas plugável.** O plano previa a porta
    `JobImportSource` + tabela `imported_jobs` com `unique (source, external_id)`; no código a
    porta tem exatamente esse nome e a tabela chama `external_jobs` (mesma chave de unicidade,
    V40) — a mesma que abriga também a vaga que a comunidade cola de outro site, para a listagem,
    o filtro por plataforma, a busca e a dedupe não terem dois códigos paralelos.
    *O que está feito:* porta `JobImportSource` (`jobs/application/port/out/`) com duas fontes
    oficiais — `JobRadarJobSource` e `OpenJobApiSource` (`jobs/adapters/out/imports/`), **sem
    scraping** (nenhuma delas toca a API privada da SPA); `ExternalJobImportService` deduplica por
    (plataforma, id externo) **e** globalmente pela URL canônica, não sobrescreve a vaga que a
    comunidade cadastrou, importa em lotes de 200 (transação curta por bloco) e isola a falha de
    uma fonte; `ExternalJobRefreshScheduler` faz a carga no boot (em segundo plano) e o refresh
    diário; a listagem é `GET /jobs?source=EXTERNAL` e o front tem a aba "EXTERNAL" com filtro por
    plataforma (`routes/vagas.tsx:149`).
    *Aceite cumprido:* importar e deduplicar — `ExternalJobImportServiceTests`
    (`importsIntoItsOwnTableAndDoesNotDuplicateOnRerun`) e `CommunitySubmissionImportGuardTests`
    (a vaga da comunidade não é reescrita pela importação); bloqueio sem os requisitos — a fonte
    fora do ar não derruba as demais (`available()` por fonte, `catch` só na chamada externa) e
    quem cadastra vaga na comunidade passa pelo mesmo gate das vagas internas (reputação > 150 +
    `COMMUNITY_RULES_NOT_ACCEPTED`).
    *Nota de escopo:* a "revisão antes de publicar" do plano se aplica a quem **publica** vaga
    (`JobPublishingPolicy`: reputação > 150 e identidade verificada quando a exigência está
    ligada). A vaga importada é listagem de terceiro (o botão vai para o link de origem), não
    publicação de membro — por isso ela não passa por aquele gate.
11. **API `/api/v1` + OpenAPI validado no CI** — alias versionado dos endpoints existentes + DTOs
    explícitos + erros/paginação padronizados; springdoc já plugado. *Aceite:* OpenAPI bate com os
    endpoints (falha o CI se divergir); v1 responde igual v0.
    *(no ROADMAP são dois itens: 1 = a API v1, 2 = o OpenAPI no CI.)*
12. **[x] ENTREGUE 2026-10-21 — Portal de docs no app + matriz de escopos.** A página pública
    `/developers` (`src/routes/developers.tsx` + `src/constants/developers-api.ts`) traz a
    matriz da API com o escopo de cada endpoint (`public` / `auth` / `admin`, renderizado na
    própria página) e os casos de uso em curl.
    *Aceite cumprido (verificado em 2026-10-21):* **os 132 pares (método, caminho) documentados
    batem com os mappings do backend** (checagem automática por script contra os `@*Mapping`) —
    e antes da rodada de hoje 8 deles NÃO existiam (o módulo de serviços é `/servicos/...`, mas a
    doc prometia `/services/servicos/...` e `/services/contratos/...`: quem copiasse pegava 404).
    *Exemplos sintéticos:* a página é pública e os exemplos estavam com dado REAL capturado da
    produção (nome completo da dona, nome completo de outra pessoa, username de um usuário e o id
    de um post que continua no ar). Tudo virou a persona fictícia que o arquivo já usava
    (`ada`/`ada-silva`), o id do post virou sintético e o placeholder de alvo do painel de
    moderação deixou de citar um usuário real. O cabeçalho do arquivo, que se gabava de "exemplos
    REAIS capturados da produção", foi corrigido. `developers-api.test.ts` trava a regra (só
    persona fictícia e id de exemplo): o teste **falha** se o dado real voltar.
    *Escopo que fica para o item 13:* escopos OAuth de aplicações de terceiros (aqui o escopo é o
    nível de acesso da API de cada rota).
    Front: `925b048a` (branch `fix/lint-higiene-front`).
13. **Registro de aplicações de terceiros (OAuth + PKCE, escopos, revogação)** — programa de auth de
    terceiros. Exige decisão de produto/segurança antes. *Aceite:* app só recebe escopo autorizado; revogável.
14. **CI de segurança + testes adversariais** — secret-scanning, SCA, SAST com política de bloqueio;
    testes negativos (anônimo x privado, id de terceiro, mass assignment, token expirado, payload
    gigante, automação social por caminho alternativo). *Aceite:* gates verdes sem desligar checagem.
    *Estado em 2026-10-21:* a **esteira não roda nada disso** — `.github/workflows/build-and-push.yml`
    só constrói e publica a imagem (nem teste Java, nem lint do front, nem secret-scanning). Dos
    testes negativos, já existem: anônimo x privado (`GroupSecurityTests`), token expirado/revogado
    (`OAuthHardeningTests`, `EmailVerificationFlowTests`), payload gigante (`MediaUploadAuthTests` com
    o teto de 500MB, `InlineMediaTests`), XSS/HTML não confiável (`SafeHtmlAttackTests`,
    terceiro no corpo) e "automação social por caminho alternativo". O passo de CI fica com o raposo
    (Actions é dele).
    *2026-10-21 — os DOIS cenários que faltavam foram implementados e passam:*
    - **mass assignment** — `MassAssignmentTests` (4): o corpo manda `authorId`, `authorUsername`,
      `userId`, `createdBy`, `ownerId` e até contadores, e a autoria, a data e os contadores
      continuam sendo os do servidor e da sessão; a quarta prova é "acesso a terceiro por id":
      mídia de outra conta na publicação alheia é recusada com 403 `MEDIA_OWNER_MISMATCH`. O app
      **não tem** o buraco — o teste é a trava para ele voltar.
    - **automação social por caminho alternativo** — `AutomatedWritePathsTests` (2): varre as
      **253 rotas de escrita** do backend (extraídas do próprio código fonte, não de uma lista
      escrita à mão) e exige 401 em todas sem sessão, menos os 14 fluxos públicos de entrada; e
      compara a allowlist do `WriteRouteInterceptor` com o catálogo esperado — se alguém abrir mais
      um fluxo público (webhook, caminho de bot), o teste quebra. Provado nos dois sentidos: o
      teste **falha** quando `/feed/posts` entra na allowlist.
    *O que continua aberto neste item:* a esteira em si (secret-scanning, SCA, SAST) — Actions é do
    raposo.
    *(no ROADMAP são dois itens: 4 = a CI de segurança, 5 = os testes adversariais.)*

## BLOQUEADO por dependência externa (não é código meu)
15. **Portal `developers.enois.ia.br`** — exige DNS, TLS, hospedagem, roteamento e pipeline.
16. **gov.br** — pedido pelo representante legal + credenciais (código pronto, desligado).
17. **LinkedIn** — app aprovado (`w_member_social`); sem isso, sem publicação legítima.
18. **Sanitizar usuários/posts de teste em produção** — sem acesso ao banco e sem endpoint admin.

## Higiene do backend (entregue 2026-10-21)
- **[x] ENTREGUE 2026-10-21 — A suíte voltou a ficar verde, e 2 das 9 falhas eram defeitos do
  app (não dos testes).** O cadastro em duas etapas (V134, 2026-10-20) deixou 5 classes de teste
  desatualizadas e, no caminho, **abriu dois buracos no código de produção**:
  1. **Login de cadastro pendente respondia "senha inválida".** Quem se cadastrava e clicava em
     "Entrar" antes de confirmar recebia 401 `INVALID_CREDENTIALS` — a senha estava certa, a conta
     só ainda não existia. Agora o backend responde **403 `EMAIL_NOT_VERIFIED`**, mandando
     confirmar o e-mail (`AuthService.login`).
  2. **O reenvio do link de confirmação não tinha teto.** O fluxo de conta já criada conta os
     envios e recusa o 4º dentro da janela; o cadastro de duas etapas reenviava **sem limite** —
     dava para spammar a caixa de quem se cadastrou. Agora vale o mesmo teto (3 envios por hora,
     contando o e-mail do cadastro): **V138** (`pending_signups.resend_count` +
     `last_sent_at`), validado na bancada MariaDB (aplicada 2x, sem erro).
  *Os testes desatualizados foram atualizados (não desligados):* `birthDate` no corpo do
  cadastro (obrigatório desde a regra do ECA), confirmação do e-mail antes do login, o estado do
  menor passou a `MINOR_CONSENT_PENDING` (supervisão parental, V137), a criação de mensagem
  responde 201 e o link usado uma segunda vez responde 404 `INVALID_TOKEN` (no fluxo de duas
  etapas o pendente some quando o link é usado).
  *Verificado:* as 5 classes que estavam vermelhas (`AgePolicyTests`, `AuthProfileFlowTests`,
  `EmailVerificationFlowTests`, `SignupChallengeTests`, `ThemeGalleryTests`) passam — 14/14.
  *Fica em aberto (para a próxima rodada):* o front consome o link de confirmação **no
  carregamento** da página, então um scanner de e-mail (Safe Links e afins) que busca o link
  queima o token e a pessoa vê "link inválido" ao clicar de verdade — o backend não tem como
  saber que ele já foi usado, porque o pendente é apagado na confirmação.

- **[x] ENTREGUE 2026-10-21 — O quadro de HTML nunca encolhia (buraco de 400px na tela).**
  A bio em HTML do perfil — e os outros quadros com altura automática — ficavam com a altura
  inicial de 520px mesmo com conteúdo de 116px: o card "Sobre" virava quase vazio. A causa: a
  conta pegava o `scrollHeight` do `<html>` do quadro, mas o `<html>` tem a altura da **viewport**
  e o `scrollHeight` dele nunca é menor que ela — então a média do quadro era sempre a viewport.
  Agora o `<html>` só entra na conta quando o conteúdo **estoura** a viewport, e a conta virou uma
  função pura (`src/lib/html-frame-height.ts`) com 4 casos de teste usando os números medidos no
  navegador. *Prova:* Chrome headless com conta local e bio em HTML — o quadro foi de 520px para
  116px, e o card "Sobre" passou a abraçar o texto. Front: `fffff551`.
- **[x] ENTREGUE 2026-10-21 — A página de desenvolvedores era ilegível no celular.** O bloco de
  código usava `overflow-x-auto`: o comando de `curl` saía cortado e a única saída era um scroll
  lateral sem nenhuma pista. Agora a linha **quebra** (`whitespace-pre-wrap` + `break-words`) e o
  exemplo inteiro cabe na tela. E o **botão de copiar só aparecia no `hover`** (`opacity-0` →
  `group-hover:opacity-100`): no toque não há hover, então o botão ficava invisível — agora ele
  fica sempre visível no celular (`sm:opacity-0` mantém o hover no desktop), com o bloco abrindo
  espaço (`pr-11`) para o texto não ficar embaixo do botão.
  *Verificado* (Chrome headless a 390px): `scrollWidth` 390 = `clientWidth` 390 (sem transbordo),
  bloco com 324px e a linha longa quebrada dentro da caixa, botão de copiar com `opacity: 1`.
  Front: `e692a78f`.
- **[x] ENTREGUE 2026-10-21 — O link de confirmação não é mais consumido no carregamento da
  página.** A tela `/auth/verify` confirmava o e-mail no `useEffect`, ou seja, no carregamento:
  quem busca o link sem ser a pessoa (Safe Links do Outlook, verificador de links do Gmail e de
  vários gateways corporativos) executa a página, **queima o token e cria a conta sem ninguém
  pedir** — e a pessoa que clicava de verdade recebia "link inválido", sem entender que a conta
  já existia. Agora a página mostra "Confirme seu e-mail" com um botão, e **só o clique consome o
  token**; o clique duplo (com sessão na tela) mostra "link já usado" em vez de "link inválido".
  *Prova* (Chrome headless, sessão real contra backend local, com o pendente lido do H2): scanner
  (busca sem JS) deixa o pendente no banco; **com o código antigo, abrir a página no navegador
  consumia o token** (pendente = 0 linhas e a conta nascia sozinha); **com o código novo, abrir a
  página não consome** e o clique confirma, guarda a sessão e leva ao feed. Front: `924233af`.
- **[x] ENTREGUE 2026-10-21 — O "quem sou eu" vinha do cache sem inscrição: a rede, os
  comentários e os grupos tinham o mesmo defeito da caixa de entrada.** A regra era
  `getCurrentUserId()` ler o **cache** da query `["me"]` sem se inscrever nela: só quem assina a
  query redesenha quando ela chega, e a inscrição fica na árvore do cabeçalho — as páginas
  continuavam com o id do primeiro desenho.
  *Medido com o `/me` atrasado de propósito* (Chrome headless, sessão real contra backend
  local): em `/rede`, o **pedido de conexão que a própria pessoa enviou caía na seção
  "RECEBIDAS", com os botões "Rejeitar" e "Aceitar"** — dava para aceitar o próprio pedido. Em
  `/grupos`, o próprio post só mostrava "excluir" depois de outra consulta redesenhar; nos
  comentários, o próprio comentário era tratado como de terceiro até a próxima redesenhadura.
  *Corrigido:* as três telas (e o item de comentário) passam a se inscrever na query do `/me`.
  Não sobrou nenhuma leitura avulsa do cache no front. Front: `33f2b1c6`.
- **[x] ENTREGUE 2026-10-21 — A caixa de entrada mostrava a própria pessoa no lugar do
  parceiro.** A tela de mensagens resolve o "outro participante" com `getCurrentUserId()`, que lia
  o **cache** da query `["me"]` sem se inscrever nela — e a página usa o `AppContainer` comum (não
  o `AuthenticatedAppContainer`, que é quem assina a query), então o cache ficava vazio: o id vinha
  `null` e o "outro" caía no **primeiro** participante da conversa, que era a própria pessoa. Na
  tela: cada conversa da caixa aparecia com o nome e a foto de quem estava olhando, e a própria
  mensagem saía alinhada como se fosse do outro.
  *Corrigido:* `getCurrentUserId()` cai no `current_user` do `localStorage` (o app grava no login)
  e exige sessão para isso; a página e os dois componentes passam a se inscrever na query do
  `/me` para redesenhar quando a resposta chega. `current-user.test.ts` trava o fallback (4 casos).
  *Prova:* Chrome headless a 390px, com sessão real contra backend local e uma conversa com
  mensagem — a lista passou de "AA | Ana Auditoria" para "BA | Bruno Audicao" e a própria
  mensagem saiu à direita (`justify-end`). Front: `d92bfdcc`.
## Higiene do front (entregue 2026-10-21)
- **[x] ENTREGUE 2026-10-21 — A tela de entrada parou de afirmar o que o produto não tem.**
  O painel de marca do login (e do reset de senha, que tinha o mesmo bloco) trazia **números
  fixos no texto** — "120k+ profissionais", "2.8k mentores ativos", "640 comunidades" — numa
  rede com **2.275 contas** (é o que o próprio `/stats/community` responde). E trazia um
  **depoimento inventado**, atribuído a "Marina Rocha, Engenheira Sênior · Nubank": pessoa
  que não existe, com o nome de uma empresa real.
  Agora o painel usa os **números de verdade** (mesma fonte da landing, cache de ~10 min) e a
  frase é a tese do produto. Se a consulta falhar, o painel fica **sem número** em vez de voltar
  a inventar um. O bloco duplicado nas duas rotas virou o componente `AuthHero`.
  *Achado pela auditoria de responsividade* (Chrome headless a 1280px). Front: `c8ac304d`.
- **[x] ENTREGUE 2026-10-21 — As telas de erro do app estavam em inglês.** A página 404 e a
  *error boundary* do root (a que aparece quando qualquer rota quebra) falavam inglês: "Page not
  found", "Try again", "Go home" — num produto que fala português do começo ao fim. As rotas com
  `notFoundComponent` próprio (perfil, blog, vaga) já estavam em português; o root ficou para trás.
  A varredura também achou a categoria de tema "Save points" (as outras cinco são em português).
  Agora: "Página não encontrada" / "Esta página não carregou" / "Tentar de novo" / "Voltar para o
  início" / "Pontos de save". `src/texts.test.ts` trava a regra — varre todos os valores de
  `texts` e falha se algum tiver palavra inequivocamente inglesa (o português não tem "the",
  "your", "try again"); o teste **falha** se o texto em inglês voltar.
  *Achado pela auditoria de responsividade* (Chrome headless a 390px): uma URL inexistente caía
  na 404 em inglês. Front: `0aaca5b8`.
- **[x] ENTREGUE 2026-10-21 — Front com lint limpo e build que nao quebra no `npm install`.**
  O `eslint` do front acusava 53 erros e a CI nao roda lint, entoa divida acumulou em silencio
  (regra `local/type-boundaries`: um tipo por arquivo em `src/types/**`). Os tipos que viviam
  fora de `src/types/**` (matriz da API em `developers-api.ts`, tipos do jogo de palavras, tipos
  de moderacao) foram para a casa canonica; `SessionPayload` e `ThemeGalleryItem` ja tinham
  arquivo proprio e passaram a importar dele. Tambem sumiram dois statements mortos do
  ImageKit em `src/lib/api/media.ts` (chave publica do ImageKit, morta desde o S3).
  *Verificado:* `eslint .` de 53 erros para 0 (4 avisos preexistentes), `tsc --noEmit` sem erro,
  `vitest` 273/273, `vite build` ok. Alem disso, `@tanstack/react-start` estava com `^` e o
  `npm install` resolvia 1.169.x (exige vite >= 8): o build quebrava com "Could not resolve entry
  module index.html" e o proprio `npm install` falhava com ERESOLVE. As tres versoes do
  `@tanstack` foram travadas na que o lock ja resolvia (react-start 1.168.22, react-router
  1.170.13, router-plugin 1.168.16) e o `npm install` puro voltou a resolver.
  Front: `847adfc8` (branch `fix/lint-higiene-front`).

## Regras que valem para tudo
- **Proibido automatizar a criação/publicação de posts** (IA/bots/integrações/endpoints alternativos/
  tarefas agendadas/webhooks). **Permitido** agendar pela funcionalidade oficial. Ver `PUBLISHING_POLICY.md`.
- Publicar vaga exige **identidade verificada** + **credibilidade ≥ 150**, conferido no backend.
- Nada de simulação de integração oficial; onde faltar credencial, documentar e falhar seguro.
