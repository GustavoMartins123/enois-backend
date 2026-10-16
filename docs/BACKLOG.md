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
5. **Vaga: valor obrigatório + período + faixa + descrição** — modelo novo (`description` obrigatório,
   `salary` = mínimo obrigatório > 0, `salaryMax` opcional, `salaryPeriod HOUR|DAY|MONTH|YEAR`) +
   `JobView.salaryLabel` + validação (`DESCRIPTION_REQUIRED`, `SALARY_REQUIRED`, `INVALID_SALARY_RANGE`)
   + formulário e página da vaga. Atualizar os testes que hoje criam vaga sem valor.
   *Aceite:* sem valor/descrição não salva; faixa invertida → 400; exibe "R$ X / mês" ou "de X até Y / mês".

## Médio-grande
6. **Feedback por candidato** — `JobApplicationStatus {APPLIED, IN_REVIEW, NEXT_STAGE, REJECTED}` +
   `status/feedback/reviewed_at` + `PATCH /jobs/{jobId}/applications/{id}` (REJECTED exige motivo) +
   notificação (DM + e-mail). *Aceite:* avançar/reprovar com motivo; candidato notificado.
7. **Penalidade por excluir vaga sem feedback** — débito de credibilidade (ledger/`ReputationService`),
   com teto, + aviso no painel. *Aceite:* excluir sem responder reduz credibilidade; com respostas, não.
8. **Currículo em PDF** — campo no perfil (upload PDF validado por tipo/tamanho) + anexar/atualizar na
   candidatura; link no snapshot que o empregador recebe. *Aceite:* PDF válido anexa; inválido recusa.

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
