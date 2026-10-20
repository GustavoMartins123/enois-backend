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
8. **Currículo em PDF** — campo no perfil (upload PDF validado por tipo/tamanho) + anexar/atualizar na
   candidatura; link no snapshot que o empregador recebe. *Aceite:* PDF válido anexa; inválido recusa.

## Médio-grande (pedido 2026-10-17, Game Dev community)
9. **Imagem no meio do texto** — post/comentário com imagem INSERIDA no ponto exato da
   explicação (ex.: ideia de jogo + screenshot no parágrafo correspondente), não só anexada
   no fim. *Design sugerido:* conteúdo passa a aceitar marcadores `{{media:<id>}}` inline no
   texto (PlainText/defuse preservando o marcador); upload via Mídia (3 passos já existentes);
   o botão de inserir copia o marcador na posição do cursor; o render (RichText) troca o
   marcador por `<figure><img></figure>` com a grade já existente; contador continua no teto
   de 4 mídias; comentário e post usam o mesmo caminho. *Aceite:* imagem aparece exatamente
   entre os parágrafos onde o autor colocou; sem marcador = anexo no fim (comportamento atual).

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
