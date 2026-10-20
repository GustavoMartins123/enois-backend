# PERFIL PROFISSIONAL (ALTER EGO CORPORATIVO) — PLANO DE EXECUÇÃO

> Plano vivo da evolução estrutural: duas identidades na mesma conta (social + profissional),
> tipo de publicação BUSINESS, feed corporativo separado no backend, integração com vagas e
> migração suave dos usuários existentes. Execução incremental por fases, com validação real.

## Regras de operação (valem para toda execução)

1. Ler este plano e o `corporate-profile-progress.md` antes de codar; implementar a próxima
   tarefa pendente na ordem das fases, com código funcional e testes de verdade.
2. **Nunca fazer push** enquanto o projeto completo não estiver implementado e validado
   (gate final da Fase 16). Commits locais sim, a cada tarefa concluída.
3. A árvore é **compartilhada** com outras sessões/automações: `git status` antes, `git add`
   apenas nos próprios arquivos, nunca `mvn clean`, nunca reverter a árvore inteira.
   Migrações: idempotentes (`if not exists`), número seguinte ao maior que existir no
   instante (incluindo arquivos não commitados).
4. Exclusão mútua: usar o lock `/tmp/enois-corporate.lock` (mtime); se outra execução
   estiver rodando (lock com menos de 35 min), sair sem fazer nada.
5. Testes: rodar os testes de tudo que tocar (módulos tocados); suíte completa nos gates
   de fase. Não desativar testes nem validações.
6. Ao concluir TODO o escopo (Fases 1–16 verificadas no progress e no validation), a
   execução usa `CronList` + `CronDelete` para remover a automação de 40 minutos.
7. Repositórios: backend `/home/mel/Documentos/re`**`de`**, frontend `/home/mel/Documentos/connect-grow`.
   Documentação viva nesta pasta (`docs/implementation/`).

## Três diretrizes da dona (não negociáveis)

1. **O perfil profissional NÃO é cópia automática do social.** Reaproveitar dados do perfil
   social só via fluxo de importação COM revisão e consentimento explícito (o usuário vê o
   que vai ser trazido e confirma campo a campo ou em bloco).
2. **Separação de feeds é obrigatoriamente no backend.** Nenhum filtro confiável ao frontend:
   a tabela abaixo é a fonte; cada endpoint que lista publicações precisa cumprir a sua linha
   (incluindo busca, notificações, salvos, trending, shares e caches).
3. **Agendamento real.** A automação de 40 minutos existe de fato (criada via CronCreate;
   documentada aqui). Sem mecanismo real, não fingir — documentar o bloqueio.
4. **Valores da conta são COMPARTILHADOS entre as duas identidades** (diretriz da dona,
   2026-10-20): é a MESMA conta com uma versão pessoal e uma profissional — não são duas
   pessoas. Faíscas/carteira, credibilidade/reputação, seguidores/conexões, verificação de
   identidade, segundo fator e login continuam no nível da conta e NUNCA são duplicados por
   identidade. O que é próprio de cada identidade: apresentação (nome, username, headline,
   bio, avatar, banner, links) e autoria/destino das publicações (social vs. BUSINESS).

## Auditoria do estado atual (Fase 0, 2026-10-20)

- **Stack**: backend Spring Boot 4 / Java 25, arquitetura hexagonal (um pacote por feature:
  `domain` / `application` / `adapters`), sem Spring Security — autorização por endpoint via
  `CurrentUserResolver` (sessão + identidade verificada; `resolveSafely` tolerante para leitura
  pública) e `AdminGuard`. Testes H2 (`@SpringBootTest` + MockMvc + `TestAuthHelper`); produção
  MariaDB 11.4. Frontend React 19 + Tan Start, API em `src/lib/api/*`, textos em `src/texts.ts`.
- **Perfil**: tabela única de perfil (`UserProfile`: username, email, name, headline, company,
  location, avatar, banner, bio/bioHtml, bannerHtml, nickname, socialName, pronouns, aka,
  resumeUrl/Name, badges, skills, goals, availability, portfolio, externalLinks, openToWork,
  mentor, contadores, reputation, showRanking, segundos fatores). Experiências/formação/
  certificações em tabelas próprias (`/me/experiences`, `/me/education`, `/me/certifications`).
- **Publicações**: tabela `posts` com `PostType` (INSIGHT, ARTICLE, QUESTION, PROJECT,
  OPPORTUNITY, PAGE, NEWS, TUTORIAL, REVIEW, DIVERTIDO, MOMENT, BLOG), `community_id`,
  `page_html` (longtext), corpo de artigo em tabela própria, agendados, selos, reações,
  comentários, moderação com triagem por IA, outbox (`PostPublished` etc.).
- **Feeds existentes**: `GetFeedService.getFeed` (Tudo), `getProfilePosts`, `getSavedFeed`,
  `getCommunityFeed`; grupos têm feed próprio (tabelas separadas); blog separado.
- **Vagas**: módulo `jobs` (vagas, candidaturas, policy de publicação, importação externa).
- **Busca**: módulo `search`. Notificações: módulo `notifications` com outbox.
- **Migrações**: até V106 commitado (services); V102/V104/V105 não commitados de outra sessão.
- **Conclusão da auditoria**: o modelo atual COMPORTA a evolução sem um segundo sistema de
  publicações — `posts` ganha a identidade de autoria e o tipo BUSINESS; o perfil profissional
  é uma tabela nova 1:1 opcional com namespace de username próprio.

## Tabela de separação de feeds (fonte de verdade —_backend_)

| contexto | Social | BUSINESS |
|---|---|---|
| Feed Tudo (`getFeed`) | sim | **nunca** |
| Perfil social (`getProfilePosts`) | sim | não |
| Perfil profissional (novo) | não | sim |
| Feed corporativo (novo) | não | sim |
| Comunidades / grupos / blog | conforme contexto (nunca BUSINESS) | nunca |
| Busca pública | sim | não (fase 1; depois, contexto próprio) |
| Salvos | conforme o que foi salvo | idem |
| Notificações de interação | seguem a publicação | seguem a publicação |

## Fases

- **Fase 0 — Auditoria e documentação** (feita neste arquivo).
- **Fase 1 — Modelo de domínio**: `CorporateProfile` (1:1 opcional com a conta: nome completo,
  headline, bio, avatar, banner, localização, links, indústria, disponibilidade; estados
  NOT_CREATED/DRAFT/ACTIVE/DISABLED); `PostType.BUSINESS`; identidade de autoria em `posts`
  (`author_identity` SOCIAL|CORPORATE, default SOCIAL — nunca nula, nunca trocada em silêncio).
- **Fase 2 — Persistência e migração** (V107+): tabelas novas + colunas; índices; validação na
  bancada MariaDB; compatibilidade com dados existentes (todo post antigo = SOCIAL).
- **Fase 3 — Serviço do perfil profissional**: criar/editar/desativar; username próprio com
  unicidade real no banco (índice único, corrida coberta); regras de estados.
- **Fase 4 — Importação com consentimento**: helper "trazer do perfil social" que mostra
  prévia campo a campo e exige confirmação; nunca cópia automática.
- **Fase 5 — Publicação BUSINESS**: validar no backend (só com perfil ACTIVE; autoria
  corporativa da própria conta); edição preserva tipo e autoria; sem conversão silenciosa.
- **Fase 6 — Feeds no backend**: `getFeed` exclui BUSINESS; feed corporativo novo; perfil
  profissional novo; perfil social não mostra BUSINESS; cumprir a tabela acima.
- **Fase 7 — Vagas e candidaturas**: candidatura usa o perfil profissional (nome completo,
  experiência); vagas continuam funcionando para quem só tem perfil social.
- **Fase 8 — Busca, notificações, salvos, trending, shares**: auditar cada endpoint que lista
  publicações e cumprir a tabela (BUSINESS fora do contexto social).
- **Fase 9 — Frontend: alternância e identidade ativa** (seletor no menu, indicador, atalhos;
  nunca prova de autorização no cliente).
- **Fase 10 — Frontend: editor do perfil profissional** (com o fluxo de importação com
  consentimento) e rotas coerentes com o roteamento atual (ex.: `/profissional/@username`,
  decidido na fase com base no router real).
- **Fase 11 — Frontend: compositor e feeds** (opção BUSINESS com indicação clara de destino;
  feed corporativo e aba no perfil).
- **Fase 12 — Segurança**: revisão de autorização (criar/editar/ver por identidade; tentativa
  de publicar BUSINESS com perfil alheio → negado; feeds sem vazamento).
- **Fase 13 — Migração de usuários existentes**: nada a migrar obrigatoriamente (opt-in);
  verificar compatibilidade (posts antigos = SOCIAL; nenhuma quebra de feed/perfil).
- **Fase 14 — Desempenho**: índices para os feeds novos; paginação preservada.
- **Fase 15 — Testes e critérios de aceite**: suíte completa verde (backend + front), cenários
  de segurança da seção 18, sem regressão das funcionalidades existentes.
- **Fase 16 — Gate final**: revisão cruzada, `validation.md` completo, e só então push nos
  dois repositórios (a única vez); depois, remover a automação.
