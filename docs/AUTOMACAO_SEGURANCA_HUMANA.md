# Automação: plano mestre de segurança humana (a cada 30 minutos)

Esta é a receita da automação que roda o plano `docs/PLANO_SEGURANCA_HUMANA.md` de meia em meia
hora, rodada por rodada, apagando-se quando a última lacuna fechar.

**Por que este arquivo existe:** a plataforma **não deixa criar automação de dentro de uma
automação em execução** ("CronCreate is not allowed while running a scheduled automation"). A
automação de auditoria (a de 40 minutos) é o que roda no momento em que a dona pede esta, então a
criação precisa ser feita de fora — pela tela de automações ou numa sessão normal.

## Como criar (1 passo)

- **Título:** `Implementar o plano mestre de segurança humana do Énóis a cada 30 minutos`
- **Agendamento:** cron `*/30 * * * *`, repetindo a cada 30 minutos
- **Prompt:** o texto do bloco "PROMPT" abaixo, sem mudar nada.

## PROMPT

Executar mais uma rodada do PLANO MESTRE DE SEGURANÇA HUMANA do Énóis (o arquivo está em
docs/PLANO_SEGURANCA_HUMANA.md no repositório do backend, /home/mel/Documentos/rede). Ler o plano
inteiro antes de mexer e ler o bloco "Estado da execução", que diz o que já saiu e o que falta.

MISSÃO (o que não pode ser esquecido em nenhuma rodada): o Énóis é uma rede de pessoas. É proibido,
em qualquer interface e para qualquer conta (inclusive empresa, conta verificada, moderador e
desenvolvedor — credibilidade e privilégio não dão imunidade): criar contas em massa, operar
contas autonomamente, publicar posts/comentários/respostas automaticamente, curtir/seguir/
compartilhar/reagir automaticamente, mandar mensagem em massa, fabricar engajamento, manipular
credibilidade/reputação, distribuir spam, publicar conteúdo artificial em escala, coordenar
campanhas automatizadas, contornar banimentos ou mecanismos de segurança, explorar a API para
atividade social automatizada e sobrecarregar a plataforma de propósito. Exceções legítimas
(preservar): agendar publicação pelo recurso oficial com o usuário no controle, corretor
ortográfico/acessibilidade, clientes alternativos usados por pessoas reais, integrações autorizadas
com escopo limitado, ferramentas internas de segurança/observabilidade/manutenção e o sistema
interno de moderação. Texto revisado por IA não é prova de automação: o alvo é automação de
atividade social e fabricação de participação.

COMO TRABALHAR NESTA RODADA (incremental, sem reescrever a aplicação):
1. Auditar o que já existe no backend (/home/mel/Documentos/rede: Spring Boot 4, arquitetura
   hexagonal por feature, sem Spring Security — CurrentUserResolver/AdminGuard, Flyway, MariaDB
   11.4 em produção, H2 nos testes) e escolher O item aberto de MAIOR impacto que ainda falta na
   lista de lacunas do plano. Conferir no código antes de afirmar que falta: a lista do repo já
   mentiu várias vezes. Não inventar classes, tabelas, endpoints ou integrações.
2. Implementar esse item com código de verdade no backend (e no front /home/mel/Documentos/
   connect-grow só quando houver contrato de interface a mudar), com testes novos ou ajustados
   (H2 + @SpringBootTest + MockMvc + TestAuthHelper no back; vitest no front). Nunca desativar
   teste nem validação, nunca "mvn clean", nunca reverter a árvore.
3. Verificar de verdade: rodar os testes do que tocou (mvn -Dtest=..., vitest, eslint nos arquivos
   tocados, tsc sem erro NOVO, vite build) e mostrar o resultado real. Migração: número seguinte ao
   maior que existir no instante (inclusive não commitado), idempotente, validada na bancada
   (docker exec -i enois-bench66 mariadb -uroot -pbench, aplicando 2x).
4. Registrar no mesmo commit: marcar a lacuna como entregue no plano (docs/PLANO_SEGURANCA_HUMANA.md,
   com "[x] ENTREGUE data — resumo" e o que foi feito), atualizar o bloco "Estado da execução" e,
   se o item veio do BACKLOG, marcar lá também. Commitar localmente. NÃO FAZER PUSH: a publicação
   é decisão da dona.
5. Árvore compartilhada: antes de mexer, git status nos dois repositórios — arquivo modificado por
   outra sessão é INTOCÁVEL; se a árvore de trabalho estiver ocupada, usar um worktree isolado
   (git worktree add) numa branch própria e nunca tocar no checkout de outra sessão. Lock:
   /tmp/enois-auditoria.lock (gravar timestamp ao começar; se o arquivo tiver menos de 35 minutos,
   sair sem fazer nada). Nunca tocar em migration já commitada com o mesmo número.
6. Encerrar a rodada relatando: o que foi implementado, arquivos alterados, regras de segurança
   adicionadas, migrações feitas, testes executados e resultados reais, o que continua aberto e os
   riscos residuais. Não afirmar que a plataforma está protegida contra todos os bots.

QUANDO ACABAR: quando todas as lacunas do plano estiverem entregues (ou explicitamente bloqueadas
por dependência externa, com o motivo anotado no plano), apagar ESTA automação: usar CronList para
achar "Implementar o plano mestre de segurança humana do Énóis a cada 30 minutos" e CronDelete
nela; então relatar o relatório final do item 15 do plano (arquitetura encontrada, vulnerabilidades
identificadas, arquivos alterados, componentes implementados, regras adicionadas, migrações, testes
e resultados, métricas e alertas, integração com a moderação, melhorias de aprendizado contínuo,
limitações conhecidas, riscos residuais, próximas ações) e quais partes ficaram dependendo de
fatores externos.
