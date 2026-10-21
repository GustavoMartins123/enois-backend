# Roadmap de implementação — estado real

> Ordem de execução (mais fácil → mais difícil): `docs/BACKLOG.md`.

Cada frente é entregue **uma por passada**, compilando, com testes verdes e publicada. Onde há
dependência externa, está marcado como **BLOQUEADO** (não é código meu).

## Em aberto (sem bloqueio) — conferido no código em 2026-10-21
1. **API `/api/v1` versionada** — NÃO existe: o back não tem alias versionado (grep por `api/v1`
   só acha URLs de terceiros). Falta: alias dos endpoints existentes + DTOs explícitos + erros e
   paginação padronizados.
2. **OpenAPI validado no CI** — o springdoc está plugado (`springdoc.api-docs.path=/api/openapi.json`,
   ligado por `SPRINGDOC_ENABLED`, desligado por padrão), mas a **esteira só constrói a imagem**:
   não há passo que gere e compare o OpenAPI. Falta: o passo (o workflow é do raposo).
3. **Registro de aplicações de terceiros (OAuth + PKCE, escopos, revogação)** — não existe
   (nenhuma tabela/porta de `oauth_client`). Exige decisão de produto/segurança antes.
4. **CI de segurança** — hoje a esteira não roda teste, lint nem secret-scanning
   (`.github/workflows/build-and-push.yml` só constrói e publica a imagem). Falta: SCA, SAST e
   secret-scanning com política de bloqueio, sem desligar checagem.
5. **Testes adversariais — completo (2026-10-21).** Coberto: anônimo x privado
   (`GroupSecurityTests`), token expirado/revogado (`OAuthHardeningTests`,
   `EmailVerificationFlowTests`), payload gigante (`MediaUploadAuthTests` com o teto de 500MB,
   `InlineMediaTests`), XSS em HTML/capa (`SafeHtmlAttackTests`, `GameUrlGuardTests`,
   `AiTriageGuardTests`), importador não sobrescreve vaga da comunidade
   (`CommunitySubmissionImportGuardTests`), **mass assignment** (`MassAssignmentTests` — o corpo
   manda autor/data/contadores e o servidor ignora) e **automação social por caminho
   alternativo** (`AutomatedWritePathsTests` — as 253 rotas de escrita exigem sessão, e a
   allowlist de fluxos públicos do `WriteRouteInterceptor` é comparada com o catálogo esperado).
   O que sobra de segurança é a **esteira** (item 4), que é do raposo.

## Concluído (no ar) — cada item conferido no código em 2026-10-21
- **Vaga com valor obrigatório, período e faixa + descrição.** `JobService.java:512`
  (`SALARY_REQUIRED`), `DESCRIPTION_REQUIRED`, `INVALID_SALARY_RANGE`, `INVALID_SALARY_PERIOD`;
  `JobView.salaryLabel` ("R$ 1.500 / mês"); migração V110; front com valor mínimo obrigatório,
  teto, seletor de período e descrição (`components/job-edit-dialog.tsx`, `routes/vagas.tsx`);
  testes `JobSalaryTests` (7).
- **Feedback por candidato.** `JobApplicationStatus {APPLIED, IN_REVIEW, ADVANCED, NOT_SELECTED}`,
  `feedback` + `reviewedAt` (`JobApplication.java:23-24`), `PATCH /jobs/{jobId}/applications/{id}`
  (`JobContractController.java:342`) e o diálogo de retorno no front.
- **Penalidade por excluir vaga sem dar retorno.** V114 (`job_abandon_penalties`), o `delete`
  devolve `{unanswered, penalized}` e `ReputationService.java:104` desconta 25, 75, 150 (teto
  próprio); `JobAbandonPenaltyTests` (3).
- **Currículo em PDF.** `resume_url` (`UserProfileJpaEntity.java:135`), upload pelo
  `profile-resume-dialog.tsx` (PDF, teto de 5MB, purpose `RESUME`) e congelamento na candidatura
  (`candidate_snapshot`, `JobApplication.java:17`, V25).
- **Áudio nos comentários.** Gravação no composer (`post-comments.tsx:80-118`, `MediaRecorder`,
  teto de 180s), player sem autoplay (`<audio controls>`, linha 364) e o backend aceitando
  `mediaIds` no comentário (`FeedController.java:289`).
- **Importador de vagas plugável.** Porta `JobImportSource` + duas fontes oficiais
  (`JobRadarJobSource`, `OpenJobApiSource` — sem scraping), `ExternalJobImportService` com dedupe
  por (plataforma, id externo) e por URL canônica, importação em lotes de 200 e falha de uma fonte
  isolada, `ExternalJobRefreshScheduler` (carga no boot + diária), V40 com
  `unique (platform, external_id)`, listagem `GET /jobs?source=EXTERNAL` e aba "EXTERNAL" no front
  (`routes/vagas.tsx:149`); testes `ExternalJobImportServiceTests` e
  `CommunitySubmissionImportGuardTests`.
- **Tag de "vulgo"/aka.** `UserProfile.aka` (`UserProfile.java:34`) com a coluna `aka`, exibido no
  perfil e no card, com o campo no editar perfil.
- **Arquivar conversa.** `GET /conversations/archived` (`MessagingContractController.java:259`) e
  `POST /conversations/unhide` (linha 285), com a aba "Arquivadas" e a ação "Restaurar" no front.
- **Apagar mensagens dentro da conversa.** `POST /conversations/{id}/messages/hide`
  (`MessagingContractController.java:146`) com a tabela `message_hidden` (V67) e a seleção na
  thread no front.
- **Portal de docs no app + matriz de escopos.** `/developers` pública, com o escopo de cada
  endpoint (público / sessão / admin) e os casos de uso em curl; **os 132 pares (método, caminho)
  conferem com os mappings do backend** e os exemplos são sintéticos (teste de regressão).
- **Portal `developers.enois.ia.br`** — **BLOQUEADO**: exige DNS/TLS/hospedagem/roteamento (não é
  código meu). O app da rota (`/developers`) já está no ar; falta o domínio.
- Auditoria BUG-01..15; MELHORIA-01 (toasts + desfazer de post); limites 1200/800; caixa de entrada
  paginada + abas Vagas/Pessoas; favoritar comunidade; núcleo de identidade + Login gov.br (plugável,
  desligado sem credencial); notificação/reação de comentário (estado persistido); resposta a resposta;
  dono nunca "Inscrito" na própria vaga; **apagar conversas em massa ("para mim")**.

## Proibições que valem para tudo acima
- **Proibido automatizar a criação/publicação de posts** (IA, bots, integrações, endpoints
  alternativos, tarefas agendadas, webhooks), comentários, curtidas ou engajamento.
- **Permitido**: criar e **agendar posts pela funcionalidade oficial** do Énóis (fluxo interativo, com
  o usuário no controle) e automações de **processo** (ex.: importar vagas). Ver `docs/PUBLISHING_POLICY.md`.
- Publicar vaga exige **identidade verificada** + **credibilidade ≥ 150** (conferido no backend).

## Bloqueados por dependência externa
- **gov.br**: elegibilidade/autorização pelo representante legal + credenciais (código pronto, desligado).
- **LinkedIn**: app aprovado (`w_member_social`) — sem isso, não há publicação legítima.
- **Sanitizar usuários/posts de teste em produção**: sem acesso ao banco e sem endpoint admin.
- **Portal `developers.enois.ia.br`**: DNS/TLS.
