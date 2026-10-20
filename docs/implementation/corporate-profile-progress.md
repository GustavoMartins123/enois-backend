# PERFIL PROFISSIONAL — PROGRESSO VIVO

> Checklist de execução. Marcar com data + resumo do que foi feito + onde (arquivos).
> Ao concluir uma tarefa, atualizar este arquivo NO MESMO commit.

## Fase 0 — Auditoria e documentação

- [x] 2026-10-20 — Auditoria do estado (plan §Auditoria): perfil único, posts com PostType,
  feeds mapeados, jobs/search/notifications com outbox, migrações até V106 (+V102/104/105 de
  outra sessão). Documentos criados: plan, progress (este), decisions, validation.
- [ ] Confirmar com a dona as decisões abertas do `decisions.md` (username com ponto, rotas).

## Fase 1 — Modelo de domínio

- [x] `PostType.BUSINESS` no enum do feed.
- [x] `CorporateProfile` (domain): campos, estados NOT_CREATED/DRAFT/ACTIVE/DISABLED,
  repositório (port) e casos (criar/editar/desativar/buscar por user e por username).
- [x] Identidade de autoria em `Post` (domain): `authorIdentity` SOCIAL|CORPORATE,
  default SOCIAL; invariante testada (nunca nula; edição preserva).

- [x] Concluída (commit `fa3c193`): 12 testes verdes — CorporateProfileTests (5),
  PostAuthorIdentityTests (4: identidade nunca nula no create/restore, edição e
  mudança de tipo preservam), mais a integração da cadeia de identidade.
- [x] Sobrecarga do `Post.restore` sem identidade (commit `0b4db2c`): toda chamada
  antiga (15 argumentos, ex. MomentExpiryTests) volta como SOCIAL — compila e
  preserva o comportamento.

## Fase 2 — Persistência e migração (V107+)

- [x] Migração idempotente: tabela `corporate_profiles` (1:1 opcional, user_id único),
  coluna `author_identity` em `posts` (default SOCIAL, not null), índices (V107).
- [x] Validação na bancada MariaDB (aplicada 2x sem erro; insert legado → SOCIAL,
  update → CORPORATE; ver `validation.md`). Compatibilidade comprovada: nenhum
  registro existente muda de mãos.
- [x] Adaptador JPA real (Spring Data + mapeamento entidade↔domínio) implementado;
  `CorporateProfilePersistenceTests` (3 cenários) verdes contra H2 com a migração
  V107 aplicada: guardar, buscar por user, e unicidade do @ profissional tratada
  no adaptador (409 USERNAME_TAKEN).

## Fase 3 — Serviço do perfil profissional

- [x] Serviço + controller (/me/business-profile): criar (1:1, um por conta),
  editar detalhes, renomear, ativar (exige headline), desativar. Username em
  namespace próprio com unicidade no banco (409 amigável). (commit 1a10133)

- [ ] Username próprio com unicidade real no banco (índice único; corrida coberta; sem
  colisão com o namespace social).
- [ ] Criar/editar/desativar + regras de estados; testes (H2) do serviço.

## Fase 4 — Importação com consentimento

- [x] Helper "trazer do perfil social": prévia campo a campo + confirmação explícita;
  teste provando que NADA é copiado sem confirmação. `GET
  /me/business-profile/import-preview` só mostra (ajustado aos limites); `POST
  /me/business-profile/import` aplica SOMENTE os campos escolhidos; lista vazia
  recusada (nada em bloco); campo desconhecido recusado. 4 testes verdes.
  (commit do ciclo)

## Fase 5 — Publicação BUSINESS

- [x] Publicar BUSINESS exige perfil ativo da MESMA conta (resolvido pela sessão,
  nunca do payload); BUSINESS não entra em comunidade; edição não troca
  identidade (IDENDITY_TYPE_MISMATCH → corrigido para IDENTITY_TYPE_MISMATCH).
  (commit 1a10133)

- [ ] Validar no backend: só com perfil ACTIVE e autoria corporativa da própria conta;
  edição preserva tipo/autoria; testes de negação (perfil DRAFT, perfil alheio).

## Fase 6 — Feeds no backend

- [x] Tuto/Following/perfil social excluem CORPORATE; feed corporativo e página
  do perfil profissional (novas rotas) trazem apenas BUSINESS; cache social sem
  posts corporativos (distribuição filtrada). (commit 1a10133)

- [ ] `getFeed` exclui BUSINESS (teste com post BUSINESS + social na mesma conta).
- [ ] Feed corporativo novo + perfil profissional novo (só BUSINESS; paginação preservada).
- [ ] Perfil social não mostra BUSINESS; cumprir a tabela de separação (plan).

## Fase 7 — Vagas e candidaturas

- [x] Candidatura usa a IDENTIDADE PROFISSIONAL quando ela existe e está ATIVA: nome,
  headline, local, avatar e indústria vêm do alterego; reputação, experiência,
  habilidades, currículo e verificação são da conta (iguais para as duas identidades).
  Sem perfil ativo, o retrato continua social — vagas funcionam para todo mundo.
  O retrato congelado na candidatura registra `identity` (SOCIAL|CORPORATE) e a
  indústria. `GET /jobs/{id}/applications` expõe candidate.identity/industry.
  2 testes verdes (com e sem perfil profissional).

## Fase 8 — Busca, notificações, salvos, trending, shares

- [x] Auditoria endpoint por endpoint (2026-10-20):

| superfície | regra | estado |
|---|---|---|
| Feed Tudo | BUSINESS nunca | query exclui BLOG+BUSINESS (testado) |
| Para você / Seguindo | só SOCIAL | query `authorIdentity = SOCIAL` (testado) |
| Perfil social | só SOCIAL | query social (testado) |
| Feed corporativo / página do perfil profissional | só CORPORATE | queries dedicadas (testado) |
| Abas Discussões/Recentes/Projetos | filtro por tipo (QUESTION/PROJECT) | BUSINESS não casa por tipo (testado) |
| Comunidades | BUSINESS não nasce nelas | recusa no publish + consulta por comunidade |
| Busca (`/search`) | não indexa posts (só users/communities/jobs/events/mentors) | sem vazamento por construção |
| Salvos | escolha explícita do usuário | BUSINESS salvo aparece (correto: acesso explícito) |
| Notificações | apontam para o post único (acesso explícito) | só autor/participantes recebem |
| Compartilhamento | link direto do post | acesso explícito, sem prévia em feed |
| Cache do feed social | não recebe CORPORATE | distribuição filtra (testado) |

## Fase 9 — Frontend: alternância e identidade ativa

- [ ] Seletor social/profissional no menu + indicador da identidade ativa; atalhos.

## Fase 10 — Frontend: editor do perfil profissional

- [ ] Editor completo (com o fluxo de importação com consentimento) + rotas coerentes
  com o roteamento real (decisão documentada em decisions.md).

## Fase 11 — Frontend: compositor e feeds

- [ ] Opção BUSINESS no compositor com indicação clara de destino; feed corporativo
  e aba no perfil profissional.

## Fase 12 — Segurança

- [ ] Revisão de autorização (criar/editar/ver por identidade; tentativa de BUSINESS
  com perfil alheio → negado; feeds sem vazamento) + testes de negação.

## Fase 13 — Migração de usuários existentes

- [ ] Verificar compatibilidade (posts antigos = SOCIAL; nenhuma quebra de feed/perfil;
  opt-in para o perfil profissional).

## Fase 14 — Desempenho

- [ ] Índices para os feeds novos; paginação preservada; verificação em dados simulados.

## Fase 15 — Testes e critérios de aceite

- [ ] Suíte completa verde (backend + front); cenários da seção 18 do plan; sem regressão.

## Fase 16 — Gate final

- [ ] Revisão cruzada; validation.md completo; push nos dois repositórios (única vez);
  remover a automação de 40 minutos.


---

# EXTENSÃO: PERSONALIZAÇÃO (progresso)

- [x] Fase A — Preferências no backend (commit 96b4c39): user_preferences ampliada
  (liked_topics, blocked_topics, disabled_modules; tema agora nullable); GET/PUT
  /me/preferences; validação item a item (taxonomia real = a das comunidades; selos
  ficam fora); conflito liked+blocked recusado; tema preservado ao atualizar listas.
  Testes: 5 verdes.
- [x] Fase B — Classificação de posts (commit 96b4c39): coluna topic em posts
  (V108), herdada do tópico da comunidade na criação; post solto nasce sem
  classificação (nunca bloqueado).
- [x] Fase C — Aplicação nos feeds (commit 96b4c39): Tudo e Para você/Following
  excluem os assuntos bloqueados na consulta (sem buracos de paginação);
  "O que quero ver" aumenta a prioridade no Para você (posts escolhidos primeiro,
  o resto segue cronológico — não é filtro). Teste: 1 verde (bloquear tira do
  feed do leitor, autor continua vendo, desbloquear traz de volta).
- [ ] Fase D — Módulos desativáveis: backend persiste (disabled_modules já
  existe); falta aplicar no frontend (navegação, atalhos, cards, descoberta,
  tela de módulo desativado, dependência Events→Acontecendo agora).
- [ ] Fase E — Interface das três seções na área Uso saudável.
- [ ] Fase F — Testes da seção 10.5 (segurança/regressão) + auditoria de busca/
  notificações/salvos com as novas preferências.
