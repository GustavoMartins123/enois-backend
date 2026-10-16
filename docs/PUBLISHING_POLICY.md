# Política de publicações do Énóis

## Princípio
**Automatizar processos — nunca fabricar interação social.** O feed é feito de autoria humana
genuína. Automação é bem-vinda para *organizar informação* (importar vagas, sincronizar avisos),
nunca para *produzir engajamento*.

## Permitido
- **Criar e agendar posts pela funcionalidade oficial do Énóis**, em fluxo interativo, com o próprio
  usuário no controle (pode editar, reagendar ou cancelar antes da publicação).
- Usar clientes/ferramentas próprias que **atuem em nome do próprio usuário autenticado**, com
  consentimento explícito e ação humana que confirme a publicação.
- Automações **de processo** (ex.: importar vagas de fonte autorizada, notificações, calendário).

## Proibido
- Gerar e publicar posts automaticamente com **IA, bots ou integrações externas**.
- Publicar em nome de usuários sem ação humana que confirme.
- Qualquer mecanismo que contorne a política por **endpoints alternativos, tarefas agendadas,
  webhooks, filas ou serviços internos**.
- Automação de comentários, curtidas, compartilhamentos ou interações para simular engajamento.
- Mecanismos que burlem limites para multiplicar publicações.

## Como o backend garante isso (controle verificável)
1. **Publicar exige sessão de usuário** (`X-Session-Token`, resolvido no backend). **Não existe**
   caminho de token de integração/API-key que publique posts — integrações não têm como publicar.
2. **Sem `scope` de publicação** para aplicações de terceiros — quando houver API pública/OAuth,
   o escopo de escrita de post **não** será concedido a integrações automatizadas.
3. **Idempotência** por operação (repetir não duplica), para não virar vetor de publicação em massa.
4. **Agendamento nativo** é distinto de automação: quem agenda é o **usuário**, no app, e pode
   cancelar; não é um robô publicando em nome dele.
5. **Auditoria**: publicações e (quando implementado) agendamentos registram autor e origem, para
   distinguir o nativo do automatizado.

> O agendamento nativo está **em desenvolvimento** e **não** deve ser removido/desativado. Ele é
> permitido; o que é proibido é a automação externa.

## Punição
Automação proibida (bots/IA/integrações publicando) leva ao **banimento da conta responsável**,
**após verificação de evidências** e o procedimento de moderação da plataforma. Nunca se pune por
suspeita: a decisão é auditável e registra o motivo.

## Exposição na API
A API pública **não** expõe operações de criação de post para execução automática. Se um cliente
externo precisar criar conteúdo, a **ação final de publicação fica sob controle humano**, em fluxo
interativo autorizado — nunca por endpoint alternativo, fila, tarefa agendada ou webhook.
