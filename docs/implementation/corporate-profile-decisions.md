# PERFIL PROFISSIONAL — DECISÕES E RISCOS

> Log de decisões de design e riscos/impedimentos. Cada decisão tem data e racional.

## Decisões

1. **2026-10-20 — Perfil profissional em tabela própria (`corporate_profiles`), não em
   colunas na tabela do perfil social.** Racional: o perfil social já tem ~40 colunas e um
   namespace de username; misturar os dois estados numa linha só criaria condicionais em todo
   o código e risco de vazamento entre identidades. Tabela 1:1 opcional, estados próprios.
2. **2026-10-20 — Autoria no `posts` como `author_identity` (SOCIAL|CORPORATE) e não como
   `corporate_profile_id` separado.** Racional: a invariante importante é binária por
   publicação; o perfil corporativo é resolvido a partir do dono da publicação. Evita
   publicações "sem perfil" quando o perfil é desativado depois.
3. **2026-10-20 — Username do perfil profissional em namespace próprio** (não colide com o
   do social; unicidade garantida por índice único na própria tabela). Aberto para a dona:
   formato sugerido — aceitar pontos e hífens (ex.: `empresa.rr`), decidir na Fase 3.
4. **2026-10-20 — Tipo de publicação BUSINESS em vez de novo sistema de posts.** Racional:
   a infraestrutura existente (agendamento, anexos, moderação, outbox) é reaproveitada; a
   separação de feeds é por consulta, conforme tabela do plan.
5. **2026-10-20 — Agendamento real**: automação de 40 minutos criada de fato (CronCreate)
   e apontada para este diretório de documentos. Sem mecanismo real, nunca fingir.

## Riscos e impedimentos

- **Árvore compartilhada**: outras sessões commitam em `rede`. Mitigação: commit pequeno por
  tarefa, `git add` seletivo, migrations numeradas pelo maior existente no instante.
- **Push proibido até o gate final**: o branch local pode divergir bastante; no gate,
  revisar antes do push (Fase 16).
- **Caches e busca**: possíveis pontos de vazamento de BUSINESS no contexto social; Fase 8
  audita endpoint por endpoint antes de considerar concluída.

## Perguntas abertas para a dona

1. Formato do username profissional (pontos/hífens) e rota pública no frontend
   (`/profissional/@username` vs. `/p/@username`) — decidir na Fase 10 com o roteamento real.
2. Visibilidade do feed corporativo: público para todos ou apenas para seguidores? Padrão
   atual do plano: público (mesma visibilidade do perfil).
