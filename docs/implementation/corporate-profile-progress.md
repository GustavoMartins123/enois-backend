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

- [ ] Helper "trazer do perfil social": prévia campo a campo + confirmação explícita;
  teste provando que NADA é copiado sem confirmação.

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

- [ ] Candidatura usa o perfil profissional (nome completo, experiência); vagas funcionam
  para quem só tem perfil social (teste dos dois cenários).

## Fase 8 — Busca, notificações, salvos, trending, shares

- [ ] Auditar cada endpoint que lista publicações; BUSINESS fora do contexto social;
  registro da auditoria aqui (endpoint por endpoint).

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
