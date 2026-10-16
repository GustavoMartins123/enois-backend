# Roadmap de implementação — estado real

Cada frente é entregue **uma por passada**, compilando, com testes verdes e publicada. Onde há
dependência externa, está marcado como **BLOQUEADO** (não é código meu).

## Em andamento / próximas (sem bloqueio)
1. **Vaga com valor obrigatório, período e faixa + descrição** *(pedido mais recente)*
   - Modelo: `description` (obrigatório), `salary` = valor mínimo (obrigatório, numérico > 0),
     `salaryMax` (opcional — faixa), `salaryPeriod` (`HOUR|DAY|MONTH|YEAR`, obrigatório), `currency`.
   - Backend: migração + domínio + entidade + validação (`DESCRIPTION_REQUIRED`, `SALARY_REQUIRED`,
     `INVALID_SALARY_RANGE`) + `JobView.salaryLabel` ("R$ 5.000 / mês" ou "de R$ 4.000 até R$ 6.000 / mês").
   - Frontend: formulário (descrição com contador, valor obrigatório, seletor de período, faixa) +
     página da vaga com descrição completa e o salário com período.
   - Atenção: tornar o valor obrigatório **quebra** os testes que hoje criam vaga sem valor — atualizar
     os testes na mesma passada.
2. **Feedback por candidato nas vagas** — `JobApplicationStatus {APPLIED, IN_REVIEW, NEXT_STAGE,
   REJECTED}` + `status/feedback/reviewed_at`; `PATCH /jobs/{jobId}/applications/{id}` (REJECTED exige
   motivo); notifica o candidato (DM + e-mail).
3. **Penalidade por excluir vaga sem dar feedback** — débito de credibilidade (ledger/ReputationService),
   com teto; aviso no painel antes de excluir.
4. **Currículo em PDF** — campo no perfil (upload PDF validado) + anexar/atualizar na candidatura; o
   empregador recebe o link no snapshot.
5. **Áudio nos comentários** — anexo de comentário (gravação pelo microfone → upload como mídia +
   player; ou embed YouTube/Spotify). *(GIF Giphy/Tenor, Spotify e embeds já entregues.)*
6. **Importador de vagas plugável** — porta `JobImportSource` + `imported_jobs` (unique
   `(source, external_id)`), só fontes com API/feed oficial. **Sem scraping.**
7. **Tag de "vulgo"/aka** — campo de "conhecido como" no perfil, exibido junto do nome.
8. **Arquivar conversa** (ver as arquivadas/restaurar) — complemento do "apagar para mim" já entregue.
9. **Apagar mensagens dentro da conversa** — seleção por mensagem (mesmo padrão do inbox).

## API pública, documentação e segurança (programa grande)
10. **API `/api/v1`** versionada (alias dos endpoints existentes) + DTOs explícitos + erros/paginação
    padronizados. 11. **OpenAPI validado no CI** (springdoc já plugado via `SPRINGDOC_ENABLED`). 12. **Portal de docs no app** + matriz de escopos. 13. **Registro de aplicações de terceiros** (id,
    escopos, credenciais próprias, revogação) — precisa decidir o modelo OAuth (produto/segurança). 14. **CI de segurança** — secret-scanning, SCA, SAST, com política de bloqueio (sem desligar checagem). 15. **Testes adversariais** (anônimo x privado, acesso a terceiros por id, mass assignment,
    token expirado/revogado, payload gigante, automação social por caminho alternativo). 16. **`SECURITY.md`** — **entregue**.
17. **Portal `developers.enois.ia.br`** — **BLOQUEADO**: exige DNS/TLS/hospedagem/roteamento (não é código meu).

## Concluído (no ar)
- Auditoria BUG-01..15; MELHORIA-01 (toasts + desfazer de post); limites 1200/800; caixa de entrada
  paginada + abas Vagas/Pessoas; favoritar comunidade; núcleo de identidade + Login gov.br (plugável,
  desligado sem credencial); notificação/reação de comentário (estado persistido); resposta a resposta;
  dono nunca "Inscrito" na própria vaga; **apagar conversas em massa ("para mim")**.

## Proibições que valem para tudo acima
- **Sem automação de posts** (nem agendamento de posts), comentários, curtidas ou engajamento.
  Automação só de **processos** (ex.: importar vagas), nunca para fabricar interação social.
- Publicar vaga exige **identidade verificada** + **credibilidade ≥ 150** (conferido no backend).

## Bloqueados por dependência externa
- **gov.br**: elegibilidade/autorização pelo representante legal + credenciais (código pronto, desligado).
- **LinkedIn**: app aprovado (`w_member_social`) — sem isso, não há publicação legítima.
- **Sanitizar usuários/posts de teste em produção**: sem acesso ao banco e sem endpoint admin.
- **Portal `developers.enois.ia.br`**: DNS/TLS.
