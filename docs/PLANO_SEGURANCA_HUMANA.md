# PROMPT MESTRE — ÉNÓIS HUMAN-FIRST SECURITY
## Defesa permanente contra bots, conteúdo automatizado e abuso coordenado

---

## Estado da execução (atualizado a cada rodada)

Este bloco é o que a próxima rodada lê primeiro. **Antes de implementar qualquer coisa do plano,
confira aqui o que já existe no código**: boa parte das camadas já foi construída em rodadas
anteriores e o trabalho de verdade agora é fechar as lacunas, não refazer o que está pronto.

### Já existe (conferido no código em 2026-10-21)
- **Prevenção por taxa e por janela:** `shared/security/GlobalApiRateLimitFilter` (300/min por IP),
  `IdentityRateLimitFilter` (cadastro, login, senha, e-mail, 2FA), `PostRateLimitFilter`,
  `IpRateLimiter`, `UserRateLimiter`, `IpBlockService`/`IpBlockReader`, `ActionThrottle` (ações que
  mexem em credibilidade), `NewAccountCooldown` (conta nova postando em seguida), `AuthThrottle`
  (falhas de login por conta), `resilience4j` (circuit breaker do storage, bulkhead, rate limiters).
- **Detecção comportamental:** `shared/security/BehavioralTimingGuard` (cadência de-bot: intervalo
  quase idêntico), `ContentDuplicationGuard` (hash do texto normalizado, 1 h), `HoneypotGuard`
  (campo isca no formulário), `PostSpamAutomodService` + `PostSpamAutomodListener` (spam
  automatizado em post), `SuspiciousUserService` (contas suspeitas), `moderation/AiVerdictApplicationService`
  (triagem com IA — indicador auxiliar, nunca prova).
- **Moderação e aprendizado:** `SanctionService` (restrição/banimento), `SanctionProposalService`
  (proposta para o humano decidir), `ModeratorService` (decisão humana + registro de recurso),
  `ModerationLearningService` (sobe/derruba peso por sinal), `ModerationSignalCatalog` +
  `ModerationSignalWeights` (versionados, o peso base nunca some), `AutoReportEvent` +
  `moderation/AutoReportListener` (denúncia automática), `ReportPreTriageService`.
- **Autorização no servidor:** `WriteRouteInterceptor` (toda rota de escrita pede sessão, exceto a
  allowlist pública explícita), `AdminRouteInterceptor` (rota admin exige admin), `CurrentUserResolver`
  (o header `X-User-Id` não vale sem sessão), `SafeHtml` (HTML sanitizado), `OutboundUrlGuard` (SSRF).
- **Portões de escrita (ECA + sanção):** `shared/age/AgeGate` (data de nascimento declarada e, para
  menor, autorização do responsável ativa) e `shared/sanctions/SanctionGate` (conta com sanção ativa
  não escreve) são chamados em **todo** caminho que produz conteúdo ou fala com outra pessoa: post
  e comentário do feed, post e comentário de grupo e mensagem privada (o blog entra pelo feed).
  Antes de 2026-10-21 o portão da sanção só olhava o feed, e o grupo nem olhava o de idade.
- **Identidade:** confirmação de e-mail em duas etapas, 2FA (TOTP), `SecretCipher` (token cifrado),
  hash de sessão versionado, `PendingSignup` com limite de reenvio.
- **Política escrita:** `docs/PUBLISHING_POLICY.md` (proibição de automação de publicação),
  `docs/SECURITY.md` (reportar vulnerabilidade).

### Lacunas (o que o plano pede e ainda NÃO existe) — a fila real
1. **Motor de risco centralizado — PASSO 1 ENTREGUE (2026-10-22); falta a escalada e a
   leitura.** Já existe `shared/risk/RiskEngine`: ele junta os sinais (cadência de robô, conteúdo
   repetido, conta nova, sanção ativa), vira **nível** (LOW/MEDIUM/HIGH/CRITICAL) e registra a
   decisão com o motivo e a versão da regra na tabela `risk_assessments` (V154). A resposta
   escalonada (permitir → monitorar → limitar → verificação → bloquear → ocorrência → suspender →
   banir) está em `RiskDecision`, mas o motor roda em **modo de observação** (`rede.risk.enforce
   =false`): ele avalia, registra e escala o que é grave, e não barra ninguém. *Falta:* a escalada
   pelo motor (hoje a denúncia automática sai dos guardas, para não duplicar denúncia e inflar o
   score da triagem) e a leitura do histórico na tela da moderação.
2. **Correlação de campanha** (itens 5 e 8): não há detector que agrupe contas coordenadas
   (mesma janela, mesmo texto, mesma origem, contas criadas na mesma hora) numa ocorrência
   consolidada, com timeline e evidências.
3. **Falsos positivos e pessoas legítimas** (item 4): falta a marca de quem foi sinalizado
   (para rever o caso e aprender com o falso positivo) — a regra "VPN não é motivo de bloqueio" precisa de
   verificação por conta, não só por IP.
4. **Observabilidade do sistema de segurança** (item 12): métricas de volume por endpoint, de
   bloqueios, de contas suspeitas, de incidentes confirmados, de latência do motor e de falso
   positivo; hoje há log e `RequestObservabilityFilter`, mas não há painel/métrica própria.
5. **Base de conhecimento de incidentes** (item 10): não há catálogo versionado de padrões de abuso
   confirmados, nem registro de incidente com análise pós-morteiro.
6. **Recorrência** (item 9): a invalidação de sessão no banimento e a revogação de credencial de
   API precisam de cobertura de teste que prove a continuidade bloqueada.

### Regras de cada rodada
- Uma lacuna por vez, a de maior impacto, com código de verdade e teste novo (nunca desativar
  teste, nunca "mvn clean").
- Marcar aqui, no mesmo commit, o que saiu: `- [x] ENTREGUE data — o que foi feito` e o item da
  lista de lacunas que fecha. O que fica em aberto continua na lista.
- Commitar localmente. **NÃO FAZER PUSH**: publicar é decisão da dona.
- Árvore compartilhada: arquivo modificado por outra sessão é intocável; se preciso, usar um
  `git worktree` isolado numa branch própria. Lock: `/tmp/enois-auditoria.lock` (35 min).
- Migração: número seguinte ao maior que existir no instante (inclusive não commitado),
  idempotente, validada 2x na bancada (`docker exec -i enois-bench66 mariadb -uroot -pbench`).
- Ao fechar a última lacuna (ou ao declarar todas bloqueadas por dependência externa, com o
  motivo anotado aqui), apagar a automação que roda este plano (CronList → "Implementar o plano
  mestre de segurança humana" → CronDelete) e relatar o relatório final do item 15.

### 1. MISSÃO

Você está trabalhando no Énóis, uma rede social independente, comunitária e orientada à liberdade, segurança e participação humana.

O Énóis não é uma plataforma de engajamento artificial. Não queremos fabricar atividade para inflar métricas, nem permitir que agentes automatizados substituam pessoas nas interações sociais.

Sua missão é auditar, implementar, testar e aprimorar continuamente um sistema integrado de prevenção, detecção, contenção e investigação de bots, spam automatizado, fabricação de engajamento e abuso coordenado.

Não implemente apenas um CAPTCHA, bloqueio de IP ou detector de textos gerados por IA. Precisamos de uma arquitetura de segurança completa, integrada ao backend, à API, à moderação e à infraestrutura existente.

O sistema deve aprender com incidentes reais, reconhecer novos padrões de abuso e reduzir continuamente a capacidade de agentes automatizados operarem na plataforma.

Não faça apenas um planejamento ou entregue recomendações teóricas. Inspecione o código existente e implemente as melhorias possíveis, de forma incremental, segura e testável.

---

### 2. PRINCÍPIO FUNDAMENTAL: O ÉNÓIS É UMA REDE PARA PESSOAS

Estabeleça uma política permanente de proibição de bots e automação de atividades sociais.

É proibido utilizar bots, agentes de IA ou scripts para:

- Criar contas em massa.
- Operar contas de usuários autonomamente.
- Publicar posts, comentários ou respostas automaticamente.
- Curtir, seguir, compartilhar ou reagir automaticamente.
- Enviar mensagens em massa.
- Fabricar engajamento ou simular participação humana.
- Manipular credibilidade, seguidores ou reputação.
- Distribuir spam.
- Publicar conteúdo artificial em escala.
- Coordenar campanhas de interação automatizada.
- Contornar banimentos ou mecanismos de segurança.
- Explorar APIs para realizar atividades sociais automatizadas.
- Sobrecarregar a plataforma deliberadamente.
- Utilizar agentes automatizados para substituir a participação humana.

A proibição deve valer para todos os usuários, incluindo perfis pessoais, empresas, contas verificadas, moderadores e desenvolvedores.

Nenhum nível de credibilidade, verificação ou privilégio administrativo concede imunidade.

A política deve ser aplicada no backend, independentemente da interface utilizada.

Não basta ocultar funcionalidades no frontend. Todas as operações protegidas precisam de autorização e validação no servidor.

#### Exceções legítimas

Preservar as seguintes funcionalidades, desde que não sejam utilizadas para fabricar participação social:

- Agendamento nativo de publicações iniciado manualmente pelo usuário.
- Ferramentas de correção ortográfica e acessibilidade.
- Clientes alternativos utilizados por pessoas reais.
- Integrações técnicas expressamente autorizadas e com escopo limitado.
- Ferramentas internas de segurança, observabilidade e manutenção.
- Sistemas internos de moderação e análise de denúncias.

Essas exceções não autorizam bots a operar contas, publicar autonomamente ou simular interações humanas.

O uso de IA para revisar um texto não constitui, isoladamente, evidência de automação abusiva. O alvo principal é a automação de atividades sociais e a fabricação de participação artificial.

Se houver uma política específica contra textos integralmente gerados por IA, ela deverá ser implementada como uma regra adicional, claramente definida e tecnicamente distinta da detecção de bots.

---

### 3. PRIMEIRA ETAPA OBRIGATÓRIA: AUDITORIA DO CÓDIGO

Antes de modificar qualquer arquivo, examine os repositórios relevantes do Énóis.

Identifique:

- Linguagens e frameworks utilizados.
- Arquitetura do backend e frontend.
- Banco de dados e mecanismos de migração.
- Autenticação, sessões e autorização.
- Cadastro e recuperação de contas.
- Publicações e comentários.
- Curtidas, seguidores e demais interações.
- Mensagens privadas.
- Grupos e comunidades.
- Empresas e vagas.
- Sistema de credibilidade.
- API pública e clientes alternativos.
- Sistema de denúncias e moderação.
- Notificações.
- Logs e métricas.
- Infraestrutura de hospedagem e proteção.
- Limites de requisições existentes.
- Testes automatizados.
- Mecanismos de banimento e suspensão.

Mapeie os endpoints e identifique os caminhos pelos quais um atacante poderia realizar operações sociais sem passar pelos controles existentes.

Verifique também operações concorrentes, duplicação de requisições e possíveis inconsistências entre a aplicação e o banco de dados.

Não invente classes, serviços, tabelas, endpoints ou integrações.

Não substitua componentes existentes sem justificativa técnica.

Reutilize os mecanismos disponíveis sempre que forem adequados.

Ao concluir a auditoria, documente os riscos encontrados e prossiga com a implementação incremental das correções prioritárias. Não encerre o trabalho apenas com um relatório.

---

### 4. CAMADA DE PREVENÇÃO

Implemente ou aprimore controles de segurança para:

- Cadastro automatizado.
- Tentativas abusivas de autenticação.
- Publicações em rajada.
- Comentários repetitivos.
- Curtidas e seguidores artificiais.
- Mensagens em massa.
- Manipulação de credibilidade.
- Abuso de endpoints.
- Abuso da API.
- Tentativas de evasão de limites.
- Repetição de operações.
- Reincidência após banimentos.
- Sobrecarga deliberada da infraestrutura.

Os controles devem ser aplicados no backend.

Implementar limites configuráveis por conta, sessão, IP, endpoint e tipo de operação, quando apropriado.

Utilizar mecanismos seguros de controle de frequência que resistam a concorrência e requisições simultâneas.

Garantir que operações concorrentes não consigam contornar limites por meio de múltiplas requisições paralelas.

Utilizar transações, operações atômicas ou mecanismos distribuídos compatíveis com a arquitetura existente quando necessário.

Considerar a idade da conta, seu histórico e o tipo de operação para determinar limites de risco.

Não depender exclusivamente de endereços IP.

Não bloquear automaticamente usuários legítimos por utilizarem VPNs, redes compartilhadas, navegadores com proteções de privacidade ou clientes alternativos.

Não adicionar dependências de infraestrutura sem demonstrar sua necessidade.

---

### 5. MOTOR DE DETECÇÃO COMPORTAMENTAL

Implementar um motor modular de detecção capaz de combinar diferentes indicadores de automação.

Considerar:

- Frequência e intervalo entre ações.
- Volume de publicações por período.
- Repetição de conteúdos.
- Similaridade entre textos.
- Padrões de comentários.
- Sequências de curtidas e seguidores.
- Atividades sincronizadas entre contas.
- Criação de contas seguida de atividade intensa.
- Mudanças abruptas de comportamento.
- Tentativas recorrentes de contornar limites.
- Comportamentos associados a incidentes anteriores.
- Tentativas de manipulação de credibilidade.
- Relações entre contas envolvidas em campanhas coordenadas.

Separar regras determinísticas de indicadores probabilísticos.

Combinar múltiplos sinais independentes antes de classificar um comportamento como automação abusiva.

Uma conta muito ativa não deve ser classificada automaticamente como bot.

Criar mecanismos para identificar campanhas distribuídas entre diversas contas, considerando comportamento, conteúdo, frequência e relações temporais.

Cada decisão relevante deve registrar os motivos que contribuíram para a classificação, permitindo auditoria posterior.

Evitar expor publicamente os critérios exatos, os limiares de detecção ou detalhes que facilitem a evasão.

---

### 6. DETECÇÃO DE CONTEÚDO ARTIFICIAL

Implementar uma camada específica para identificar padrões de conteúdo associados a automação, spam e fabricação de participação.

Analisar:

- Publicações duplicadas.
- Variações superficiais de textos.
- Similaridade textual e semântica.
- Repetição entre diferentes contas.
- Comentários padronizados.
- Publicações coordenadas.
- Frequência e sincronização de conteúdos.
- Relação entre publicações e comportamento das contas.
- Padrões recorrentes identificados em incidentes anteriores.

Utilizar algoritmos de similaridade, agrupamento e análise comportamental adequados ao volume da plataforma.

Modelos de IA podem ser utilizados como indicadores auxiliares, quando houver justificativa técnica.

Não considerar um detector de texto gerado por IA como prova definitiva de automação.

Não classificar uma pessoa como bot exclusivamente por causa do estilo de escrita, vocabulário, gramática ou aparência artificial de uma publicação.

Priorizar a identificação de comportamentos abusivos, automação de interações e fabricação de engajamento.

Não enviar indiscriminadamente conteúdo privado para serviços externos.

Qualquer integração externa deverá ser avaliada quanto à privacidade, retenção de dados, custo, latência e dependência operacional.

---

### 7. MOTOR CENTRAL DE RISCO E RESPOSTA

Criar ou aprimorar um mecanismo centralizado de avaliação de risco.

A avaliação deve considerar:

- Gravidade do comportamento.
- Quantidade e confiabilidade dos indicadores.
- Histórico de incidentes.
- Relação com outras contas.
- Evidências disponíveis.
- Possibilidade de falsos positivos.
- Impacto potencial na plataforma.

Definir níveis configuráveis de risco e respostas correspondentes.

As respostas possíveis incluem:

1. Permitir a operação e registrar os eventos relevantes.
2. Aumentar o monitoramento.
3. Limitar temporariamente determinadas ações.
4. Solicitar verificação adicional.
5. Bloquear uma operação suspeita.
6. Abrir uma ocorrência automática para a moderação.
7. Suspender temporariamente uma conta em situações de risco elevado.
8. Aplicar banimento permanente quando a violação estiver suficientemente comprovada.
9. Bloquear tráfego abusivo e acionar os mecanismos de proteção da infraestrutura durante ataques ativos.

Implementar contenção automática para situações de risco elevado, sem depender da disponibilidade imediata de um moderador.

Entretanto, não aplicar banimento permanente exclusivamente com base em uma pontuação probabilística ou em um único classificador.

Registrar a versão das regras, os indicadores e os motivos utilizados em cada decisão.

Garantir que as decisões possam ser reconstruídas durante uma investigação.

Os limiares e as políticas devem ser configuráveis de forma segura no backend.

O motor não pode alterar autonomamente a própria política de banimento permanente.

---

### 8. DENÚNCIAS AUTOMÁTICAS E MODERAÇÃO

Integrar a detecção ao sistema de moderação existente.

Quando forem identificados comportamentos suspeitos, o sistema deverá:

1. Registrar o evento.
2. Identificar as contas e publicações envolvidas.
3. Correlacionar eventos relacionados.
4. Calcular o nível de risco.
5. Criar ou atualizar uma ocorrência.
6. Anexar evidências técnicas relevantes.
7. Aplicar a contenção adequada.
8. Notificar os moderadores pelos mecanismos existentes.
9. Registrar as decisões tomadas.
10. Incorporar o resultado da investigação ao processo de melhoria contínua.

Cada ocorrência deve conter:

- Identificador único.
- Categoria.
- Prioridade.
- Contas envolvidas.
- Conteúdos relacionados.
- Data e hora dos eventos.
- Indicadores detectados.
- Motivos da classificação.
- Nível de confiança.
- Linha do tempo.
- Medidas de contenção.
- Estado da investigação.
- Decisão final.
- Histórico de auditoria.

Implementar deduplicação e agrupamento de incidentes.

Uma campanha de spam envolvendo centenas de publicações deve gerar uma ocorrência consolidada, preservando a referência a cada evento individual.

Não criar denúncias redundantes para cada ação de uma mesma campanha.

Diferenciar incidentes críticos, como ataques à disponibilidade, de violações comuns de conteúdo.

Garantir que os moderadores recebam informações suficientes para compreender a detecção e tomar decisões fundamentadas.

A denúncia automática não deve depender de denúncias manuais de outros usuários.

O próprio Énóis deve identificar e encaminhar incidentes proativamente.

---

### 9. BANIMENTO PERMANENTE E PREVENÇÃO DE REINCIDÊNCIA

Estabelecer o banimento permanente de contas confirmadamente utilizadas para operar bots ou automatizar atividades sociais proibidas.

Aplicar a política também a:

- Bots utilizados para manipular credibilidade.
- Contas que fabricam engajamento.
- Contas utilizadas em campanhas coordenadas de spam.
- Contas reincidentes após medidas anteriores.
- Contas envolvidas em tentativas comprovadas de contornar banimentos.

Implementar mecanismos para impedir a continuidade das operações proibidas após o banimento.

Avaliar a invalidação de sessões, revogação de credenciais de acesso e bloqueio de operações pendentes, conforme a arquitetura existente.

Investigar tentativas de evasão considerando múltiplos sinais independentes.

Não associar automaticamente contas diferentes apenas porque compartilham um IP, dispositivo ou rede.

Preservar evidências relevantes para investigação e auditoria, com acesso controlado e políticas de retenção adequadas.

Não permitir que usuários privilegiados concedam exceções informais à política de proibição de bots.

Qualquer integração técnica autorizada deve possuir escopo documentado e não pode permitir a fabricação de participação social.

---

### 10. APRENDIZADO CONTÍNUO

Implementar um processo estruturado de melhoria contínua baseado em incidentes reais.

O sistema deve transformar ataques e tentativas de abuso em conhecimento técnico reutilizável.

#### Registro de incidentes

Registrar informações suficientes para reconstruir os eventos, incluindo:

- Categoria do incidente.
- Horário.
- Operações realizadas.
- Endpoints envolvidos.
- Regras acionadas.
- Indicadores observados.
- Medidas aplicadas.
- Resultado da investigação.
- Eventuais falsos positivos.

Não registrar senhas, tokens ou dados pessoais desnecessários.

#### Base de conhecimento

Manter um catálogo versionado de padrões de abuso confirmados, incluindo:

- Spam automatizado.
- Publicações repetitivas.
- Manipulação de credibilidade.
- Criação coordenada de contas.
- Automação de interações.
- Abuso da API.
- Evasão de limites.
- Campanhas de conteúdo artificial.
- Ataques à disponibilidade.
- Reincidência após banimento.

Registrar exemplos positivos e negativos para reduzir erros de classificação.

#### Análise pós-incidente

Para cada incidente relevante, produzir uma análise capaz de responder:

- O que aconteceu?
- Como o ataque foi identificado?
- Quais controles funcionaram?
- Quais controles falharam?
- Qual foi o impacto?
- Como o ataque poderia ter sido contido antes?
- Quais melhorias podem evitar a repetição?
- Quais são os riscos de falsos positivos das melhorias propostas?

#### Geração de melhorias

Automatizar a identificação de padrões recorrentes e a proposição de novas regras.

Toda regra nova deve passar por testes de regressão e avaliação de impacto antes de ser ativada.

Quando apropriado, iniciar em modo de observação, sem bloquear usuários, para medir os resultados.

O sistema pode automatizar a análise, a geração de propostas e os testes.

Mudanças críticas nas políticas de segurança devem permanecer sujeitas a validação controlada.

Não permitir que o sistema aprenda exclusivamente com suas próprias decisões sem avaliação independente.

Não permitir que um modelo altere automaticamente os critérios de banimento permanente.

---

### 11. TESTES DE SEGURANÇA E REGRESSÃO

Criar ou atualizar testes unitários, de integração e de regressão.

Cobrir obrigatoriamente:

- Operações dentro e fora dos limites.
- Requisições concorrentes.
- Tentativas de contornar limites.
- Publicações repetitivas.
- Campanhas coordenadas.
- Contas recém-criadas com atividade anormal.
- Falsos positivos em usuários legítimos.
- Criação e deduplicação de ocorrências.
- Aplicação de restrições.
- Banimento permanente.
- Tentativas de evasão de banimento.
- Invalidação de sessões.
- Persistência dos eventos.
- Falhas de componentes externos.
- Recuperação após falhas.
- Funcionamento da API pública.
- Compatibilidade com clientes alternativos legítimos.
- Preservação do agendamento nativo de publicações.
- Funcionamento dos recursos de acessibilidade.
- Proteção contra abuso de operações concorrentes.

Verificar que os controles são aplicados no backend e não dependem apenas da interface.

Não considerar o trabalho concluído apenas porque o código compila.

Executar os testes relevantes e apresentar os resultados reais.

Não inventar resultados nem afirmar que testes foram executados quando isso não ocorreu.

Não remover testes existentes apenas para facilitar a aprovação da implementação.

---

### 12. OBSERVABILIDADE E SEGURANÇA OPERACIONAL

Implementar métricas e alertas para:

- Volume de requisições por endpoint.
- Frequência de publicações.
- Taxa de bloqueios.
- Contas suspeitas.
- Incidentes confirmados.
- Tempo entre detecção e contenção.
- Falsos positivos.
- Reincidência.
- Latência do motor de detecção.
- Consumo de recursos.
- Erros de processamento.
- Disponibilidade durante ataques.

Garantir que o sistema de segurança não se torne um ponto único de falha.

Os controles fundamentais de autenticação, autorização e frequência devem continuar funcionando mesmo quando componentes de análise avançada estiverem indisponíveis.

Definir políticas de retenção de eventos e minimização de dados.

Garantir que os registros não exponham segredos ou informações privadas desnecessárias.

Não adicionar dependências pesadas sem justificativa baseada em necessidades reais de volume, latência ou persistência.

---

### 13. CRITÉRIOS DE IMPLEMENTAÇÃO

Trabalhe incrementalmente.

Para cada etapa:

1. Identifique os arquivos afetados.
2. Explique o problema técnico.
3. Implemente a solução adequada à arquitetura existente.
4. Crie ou atualize os testes.
5. Execute os testes relevantes.
6. Analise regressões.
7. Documente as decisões.
8. Atualize o estado da implementação.

Não reescreva toda a aplicação.

Não misture refatorações desnecessárias com correções críticas de segurança.

Não introduza migrações destrutivas sem necessidade e sem estratégia de compatibilidade.

Siga os padrões existentes de código, testes, persistência, observabilidade e implantação.

Se uma funcionalidade depender de infraestrutura inexistente, identifique a dependência e apresente uma alternativa viável.

Não simule integrações nem declare funcionalidades concluídas sem verificar sua implementação.

Priorize as vulnerabilidades de maior impacto e prossiga com as melhorias seguintes sem aguardar uma nova solicitação para cada etapa, desde que estejam dentro do escopo autorizado e não envolvam operações externas ou destrutivas não aprovadas.

---

### 14. CICLO PERMANENTE DE MELHORIA

Estabeleça um processo recorrente que:

1. Analise novos incidentes.
2. Correlacione eventos relacionados.
3. Identifique padrões de abuso.
4. Compare os resultados com incidentes anteriores.
5. Avalie falhas de detecção.
6. Identifique possíveis falsos positivos.
7. Proponha novas regras.
8. Crie testes reproduzíveis.
9. Execute regressões.
10. Avalie impacto e custo.
11. Valide mudanças críticas.
12. Publique melhorias aprovadas.
13. Monitore os resultados.
14. Reverta alterações problemáticas.
15. Atualize a documentação de segurança.

A melhoria contínua deve produzir resultados mensuráveis, e não apenas relatórios.

Acompanhar a evolução dos indicadores e demonstrar se cada mudança realmente melhorou a proteção.

Não inventar metas numéricas antes de estabelecer uma linha de base com dados reais.

---

### 15. RELATÓRIO FINAL OBRIGATÓRIO

Ao concluir cada etapa, apresentar:

1. Arquitetura encontrada.
2. Vulnerabilidades identificadas.
3. Arquivos alterados.
4. Componentes implementados.
5. Regras de segurança adicionadas.
6. Migrações realizadas.
7. Testes executados e resultados.
8. Métricas e alertas disponíveis.
9. Integração com a moderação.
10. Melhorias de aprendizado contínuo.
11. Limitações conhecidas.
12. Riscos residuais.
13. Próximas ações prioritárias.

Diferenciar claramente o que foi implementado, o que foi testado, o que está parcialmente concluído e o que ainda depende de infraestrutura ou aprovação.

Não afirmar que a plataforma está protegida contra todos os bots.

Demonstrar quais ataques são prevenidos, quais são detectados, quais são contidos e quais ainda podem escapar.

---

### 16. OBJETIVO FINAL

O Énóis deve se tornar progressivamente mais resistente à automação abusiva.

A plataforma deve detectar padrões suspeitos, conter ataques, encaminhar denúncias automaticamente, aplicar a política permanente contra bots e aprender com incidentes reais.

Nenhuma conta deve receber imunidade por credibilidade, verificação ou privilégios.

Nenhum ataque confirmado deve terminar sem uma análise de suas causas e uma avaliação das melhorias necessárias.

Nenhuma alteração de segurança deve ser considerada bem-sucedida sem testes e evidências.

A prioridade é proteger as pessoas, a integridade das interações, a disponibilidade da plataforma e a confiança da comunidade.

**O Énóis existe para conectar pessoas reais. Bots não têm autorização para participar da rede, fabricar engajamento ou substituir a presença humana. Essa política deve ser permanente, aplicada tecnicamente e sustentada por um sistema de segurança que evolui continuamente.**

Comece pela auditoria dos repositórios e pela identificação dos vetores de abuso de maior impacto. Em seguida, implemente as correções prioritárias, os testes e a integração com a moderação. Continue o trabalho incrementalmente, documentando os resultados reais e os riscos que ainda precisam ser tratados.