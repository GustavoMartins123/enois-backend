# Backlog ordenado (mais fácil → mais difícil)

Ordem por **esforço × risco**, para entregar valor cedo e não travar em dependência externa.
Cada item é feito **numa passada**, compilando, com testes verdes e publicado (backend e/ou front).
Os 4 últimos são **BLOQUEADOS** por dependência externa (não é código meu).

## Fácil (1 arquivo/poucos pontos)
1. **Tag "vulgo"/aka no perfil** — campo "conhecido como" (`aka`, varchar 60) no perfil, exibido ao
   lado do nome. Backend: migração + domínio + view + validação de tamanho. Front: campo no editar
   perfil (com contador) e exibição no perfil/card.
   *Aceite:* salvar com/sem aka; aparece no perfil; limite respeitado.
2. **Arquivar conversa (ver arquivadas / restaurar)** — complementa o `hidden_at` já entregue:
   endpoint `POST /conversations/{id}/unhide` + lista de "Arquivadas" e ação "Restaurar".
   *Aceite:* arquivar some da caixa; restaurar volta; não afeta o outro participante.
3. **Apagar mensagens dentro da conversa** — marcar por usuário (`message_hidden(user_id, message_id)`),
   endpoints em massa e seleção na thread (mesmo padrão do inbox: selecionar + selecionar tudo).
   *Aceite:* apagar some só para quem apagou; paginação continua coerente.

## Médio
4. **Áudio nos comentários** — anexo de comentário (gravação pelo microfone → upload de mídia +
   player) e/ou embed de YouTube/Spotify (GIF e embeds **já entregues**).
   *Aceite:* gravar, enviar, tocar; recarregar mantém o anexo; sem autoplay.
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
6. **Feedback por candidato** — `JobApplicationStatus {APPLIED, IN_REVIEW, NEXT_STAGE, REJECTED}` +
   `status/feedback/reviewed_at` + `PATCH /jobs/{jobId}/applications/{id}` (REJECTED exige motivo) +
   notificação (DM + e-mail). *Aceite:* avançar/reprovar com motivo; candidato notificado.
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
9. **Importador de vagas plugável** — porta `JobImportSource` + `imported_jobs` (unique `(source,
   external_id)`) + dedupe/normalização + revisão antes de publicar (respeita identidade + ≥150).
   Fontes só com API/feed oficial. **Sem scraping.** *Aceite:* importar e deduplicar; bloqueio sem os requisitos.
10. **API `/api/v1` + OpenAPI validado no CI** — alias versionado dos endpoints existentes + DTOs
    explícitos + erros/paginação padronizados; springdoc já plugado. *Aceite:* OpenAPI bate com os
    endpoints (falha o CI se divergir); v1 responde igual v0.
11. **Portal de docs no app + matriz de escopos** — página pública de desenvolvedores + escopos.
    *Aceite:* documenta só o que existe; exemplos sintéticos.
12. **Registro de aplicações de terceiros (OAuth + PKCE, escopos, revogação)** — programa de auth de
    terceiros. Exige decisão de produto/segurança antes. *Aceite:* app só recebe escopo autorizado; revogável.
13. **CI de segurança + testes adversariais** — secret-scanning, SCA, SAST com política de bloqueio;
    testes negativos (anônimo x privado, id de terceiro, mass assignment, token expirado, payload
    gigante, automação social por caminho alternativo). *Aceite:* gates verdes sem desligar checagem.

## BLOQUEADO por dependência externa (não é código meu)
14. **Portal `developers.enois.ia.br`** — exige DNS, TLS, hospedagem, roteamento e pipeline.
15. **gov.br** — pedido pelo representante legal + credenciais (código pronto, desligado).
16. **LinkedIn** — app aprovado (`w_member_social`); sem isso, sem publicação legítima.
17. **Sanitizar usuários/posts de teste em produção** — sem acesso ao banco e sem endpoint admin.

## Regras que valem para tudo
- **Proibido automatizar a criação/publicação de posts** (IA/bots/integrações/endpoints alternativos/
  tarefas agendadas/webhooks). **Permitido** agendar pela funcionalidade oficial. Ver `PUBLISHING_POLICY.md`.
- Publicar vaga exige **identidade verificada** + **credibilidade ≥ 150**, conferido no backend.
- Nada de simulação de integração oficial; onde faltar credencial, documentar e falhar seguro.
