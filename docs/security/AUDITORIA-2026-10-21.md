# Auditoria local de segurança e regras de negócio — 21/10/2026

Base examinada: `a6a7b10`, originalmente em `feat/molduras-isoladas`.
Trabalho de auditoria: `test/auditoria-seguranca-regras-negocio`.

**Este documento preserva os achados anteriores às correções.** A implementação, os contratos
de deploy e a comparação posterior estão em [CORRECOES-2026-10-21.md](CORRECOES-2026-10-21.md),
na branch `fix/seguranca-regras-negocio`. Os resultados vermelhos abaixo são históricos.

## Escopo e ambiente

Foram inventariadas **419 rotas de 55 controllers**: 165 GET, 183 POST, 14 PATCH,
43 DELETE e 14 PUT. A matriz completa está em [ROTAS-2026-10-21.md](ROTAS-2026-10-21.md).

Os testes usam Java 25, contas fictícias, MockMvc, H2 e MariaDB 11.8.9 descartável.
Os contêineres de teste não têm rede externa. O MariaDB não publica portas e seus dados
ficam em memória. Nenhuma requisição de ataque foi enviada à plataforma em produção.
O ensaio de navegador usa Chrome com todas as requisições HTTP interceptadas pelo Playwright.

A motivação fornecida foi flood de publicações e tentativas de JavaScript em posts.
A auditoria priorizou esse fluxo e verificou também autenticação, privilégio administrativo,
bloqueios, proteção de menores, transações e isolamento entre usuários.

**Inventário não equivale a aprovação integral de cada regra.** Todas as escritas protegidas
e as rotas administrativas foram sondadas; 73 leituras privadas/operacionais tiveram sondas
adicionais. Nas 79 leituras públicas foram aplicadas três sondas de entrada: limite zero,
limite negativo e cursor malformado. As regras de negócio têm testes específicos, descritos abaixo.
As 14 exceções de escrita dos fluxos de autenticação/bootstrap exigem validações próprias do
controller; uma exceção no interceptor não significa que todo esse fluxo dispense sessão.

## Resultados reproduzidos e prioridades

Na coleta anterior, os testes vermelhos expressam o comportamento seguro esperado.
As correções posteriores mantêm essas invariantes, com as adaptações de teste documentadas.
Os itens abaixo agrupam as causas; quantidade de testes que falham não é quantidade de falhas distintas.

| ID | Prioridade | Resultado | Evidência principal |
| --- | --- | --- | --- |
| SEC-01 | P0, condicional | Nome de usuário presente na allowlist de admin concede privilégio se estiver disponível para cadastro ou renomeação. | Dois testes de `BusinessRuleAdversarialTests` passam de 403 para 200 em `/admin/blocked-ips`. Foi usada uma allowlist sintética, sem tocar em contas administrativas reais. |
| SEC-02 | P1 | Um cliente esgota a cota global de autenticação e impede o login válido de outro IP. | `SharedAuthenticationQuotaAuditTests`: 20 logins inválidos; em seguida login correto de uma conta distinta recebe 429. |
| SEC-03 | P1 | Tentativas erradas de segundo fator não ficam registradas. | `AuthenticationStateAuditTests`: após cinco erros, `attempts` continua em zero no login e na ativação por e-mail. |
| SEC-04 | P1 | Uso único do desafio de segundo fator não tem consumo atômico. | No H2, seis verificações simultâneas do mesmo código emitiram seis sessões. MariaDB também apresentou conflitos/500 na concorrência; distinguir os resultados por banco. |
| SEC-05 | P1 | Bloqueio não impede mensagens em conversa já existente, e criação genérica de conversa contorna a proteção de `/conversations/direct`. | `BusinessRuleAdversarialTests`: envio em conversa antiga e criação alternativa chegam a 201 no cenário isolado. |
| SEC-06 | P1 | Um adulto desconhecido pode incluir um menor em conversa criada como `GROUP` e enviar mensagens sem conexão aceita. | A mesma mensagem é recusada no tipo DIRECT; passa no grupo imposto pelo remetente. |
| SEC-07 | P1 | Bloqueados podem comentar em posts da vítima e provocar menções/notificações. | Comentário retorna 201; menção cria uma notificação para a vítima mesmo após bloqueio. |
| SEC-08 | P1 | Uma avaliação inicial sem sinal de spam aciona o intervalo de dois minutos e silencia a avaliação do flood subsequente. | `SpamDetectionTimingAuditTests`: primeiro post retorna `NO_SIGNAL`; ao chegar a oito posts na janela, retorna `THROTTLED`. |
| SEC-09 | P2 | Autor restrito pode substituir o conteúdo de um post existente por PATCH. | Nova publicação recebe 403; edição de conteúdo público recebe 200. |
| SEC-10 | P2 | Página sem título escapa da deduplicação de conteúdo. | Dois comandos com o mesmo `pageHtml` retornam 201 quando `content` é omitido. O limite de publicações continua sendo uma defesa separada. |
| SEC-11 | P2 | Artigo recusado deixa o texto reservado no deduplicador e impede a correção legítima. | Primeiro envio sem corpo retorna 400 e não persiste post; envio corrigido com o mesmo título retorna 409 `DUPLICATE_CONTENT`. |
| SEC-12 | P2, requer política de storage | O PUT pré-assinado não vincula o tamanho declarado ao upload. | `S3UploadConstraintAuditTests`: `X-Amz-SignedHeaders` contém apenas `content-type;host`. Nenhum upload real ou inspeção de bucket foi feito. |
| SEC-13 | P2 | CSS com função escapada sobrevive à limpeza e solicita recurso remoto invisível. | `u\\72l(...)` vira uma tentativa real de carregar pixel no Chrome, inclusive com CSP e sandbox atuais; a requisição foi interceptada localmente. |
| SEC-14 | P2, defesa em profundidade | SMIL/SVG conserva valores `javascript:` em atributos de animação. | Dois payloads sobrevivem à sanitização. **Não houve execução de JavaScript confirmada no Chrome nos cenários testados**, inclusive no controle sem CSP. Não tratar como XSS explorável comprovado. |
| SEC-15 | P2 | Diagnóstico de migração de mídia fica acessível sem autenticação. | Quatro sondas a GET `/media/migration` retornam 200 e expõem contagens e campo `lastError`. |
| SEC-16 | P2 | Concorrência gera 500 em transferências e publicações. | `EconomyIntegrityAuditTests` e `PublishingAbuseAuditTests`: conflito de idempotência/deadlock tratado como erro inesperado. Nas transferências, o débito não duplicou e snapshots/razão permaneceram reconciliados. |
| SEC-17 | P2, dependente do perfil | MariaDB sem escape global de identificadores falha ao gravar notificações por causa de `read`. | Primeiro ensaio MariaDB gera erro SQL em mensagens, menções e sanções. O perfil QA já declara `hibernate.globally_quoted_identifiers=true`; não extrapolar esse erro para um QA que preserve a configuração. |
| SEC-18 | P2 | Leituras públicas devolvem 500 para entradas inválidas ou parâmetros obrigatórios ausentes. | Sondas de `RouteAuthorizationAuditTests` encontraram respostas 500 em `/games/ratings`, `/groups`, `/jobs` e `/users/search`. `limit=0` também provoca acesso ao índice -1 em paginação. |

### SEC-01: privilégio administrativo por atributo editável

`AdminGuard.isAdmin(String, String)` confia no username; o cadastro deriva o username do nome
informado, e `ProfileService.update` aceita renomeação. Não há reserva específica dos nomes
administrativos. A pré-condição é um nome da allowlist estar livre — por exemplo, após renomeação,
remoção ou configuração antes de existir a conta correspondente.

Correção indicada: usar identidade administrativa imutável, manter concessão/revogação explícita
e impedir que dados editáveis de apresentação atribuam papéis. Conferir configuração e ocupação
dos nomes atuais exige verificação autorizada do ambiente de deploy, não foi presumida nesta auditoria.

### SEC-02, SEC-03 e SEC-04: autenticação e disponibilidade

O limitador `auth` do Resilience4j é compartilhado pela instância. O limite de produção de
20 requisições por minuto é baixo o bastante para um cliente bloquear o acesso de todos.
Os limites por IP não impedem esse caso: a cota global acaba antes da cota de login por IP.
Manter limites por origem/conta, com uma proteção global dimensionada para a plataforma.

No segundo fator, `TwoFactorService` salva a tentativa errada e lança `ApiException`, que
estende `RuntimeException`. A transação do serviço e a transação externa de `AuthService`
desfazem essa gravação. Persistir o contador de erro em transação independente e com incremento
atômico; não aplicar uma exceção genérica de rollback a todas as regras de negócio.

O consumo do desafio deve ser uma operação condicional atômica, que só permita a um chamador
alterar `consumed_at` de nulo para consumido, com conferência das linhas afetadas.
Nenhum código real foi adivinhado: os testes usam um código conhecido e cinco erros sintéticos.

### SEC-05 a SEC-07: assédio e contato indesejado

O bloqueio é verificado no controller da abertura de conversa direta, mas não centralmente em
`MessagingService.createConversation` e `sendMessage`. O campo `kind` escolhido pelo cliente
também permite evitar a regra de destinatário menor, que só vale para DIRECT.

Comentários e menções não fazem a mesma verificação de bloqueio. Filtrar a lista exibida ao usuário
não impede gravar a notificação ou publicar o evento que alimenta push.

Correção indicada: centralizar a política de contato, aplicá-la às rotas alternativas e a conversas
antigas, exigir consentimento/participação válida na inclusão em grupos e verificar bloqueio antes
da criação de notificações. Evitar uma mensagem de erro que revele detalhes desnecessários à conta bloqueada.

### SEC-08 a SEC-11: flood e tentativas legítimas

O automod marca `ultimaAvaliacao` antes de determinar se existe sinal. O intervalo deve limitar
a análise custosa, não ignorar a transição de atividade normal para o limiar de flood.
Os testes anteriores do automod usam intervalo zero e uma coleção de posts já completa, por isso
não cobriam a sequência observada no ataque.

A edição de conteúdo público precisa aplicar os gates de escrita pertinentes. Deduplicação deve
considerar a representação relevante de PAGE/ARTICLE e confirmar a reserva do conteúdo somente
quando a publicação é efetivamente aceita, liberando-a em rollback.

O limite de 10 posts/hora **funcionou no ensaio H2**, inclusive com troca de sessão e 48 requisições
concorrentes; a tentativa não consumiu a cota de outro usuário. No MariaDB, a concorrência também
precisa ser avaliada quanto a deadlocks e respostas 500, sem confundir rejeição com publicação aceita.

### SEC-12 a SEC-14: mídia e conteúdo armazenado

O backend verifica o campo `size`, mas esse valor não entra na assinatura S3. Uma política de bucket
ou outro mecanismo externo pode mitigar o problema; isso não foi inspecionado. Vincular limites ao
upload e validar tamanho/tipo reais antes de tornar o asset utilizável.

A limpeza de CSS usa regex que não normaliza escapes da linguagem. No navegador, a forma escapada
é reconhecida e o recurso remoto é solicitado. A CSP permite imagens HTTPS e, portanto, não distingue
esse pixel de uma imagem permitida.

Para SVG, restringir os atributos que SMIL pode animar e os valores aceitos. As evidências mostram
uma lacuna de sanitização e preservação de URL ativa; CSP, sandbox e tratamento de links são camadas
separadas. O resultado desta rodada **não comprova roubo de sessão ou execução de JavaScript**.

### SEC-15 a SEC-17: operação e concorrência

Diagnóstico de storage deve exigir autorização operacional e devolver erros redigidos para esse
público. O campo `lastError` não deve ser considerado seguro só porque IDs/URLs foram omitidos.

Na economia, a mesma transferência concorrente debitou uma única vez e não deixou divergência de
saldo. O erro 500 ainda precisa virar conflito recuperável ou retry transacional controlado,
sem repetir efeitos monetários. Não foi demonstrada criação de dinheiro.

O problema de SQL de notificações é específico da configuração. O primeiro ensaio foi executado
com dialect MariaDB e as propriedades comuns de teste; a repetição usa o escape global que já
existe no perfil QA. Essa distinção precisa acompanhar qualquer conclusão sobre produção.

Nas leituras públicas, um parâmetro obrigatório ausente deve ser erro de cliente, e limite zero
deve ser recusado ou normalizado antes de construir o cursor. O handler genérico de exceções
e o acesso ao último elemento de uma página vazia produzem respostas 500 e logs de erro evitáveis.
As sondas não demonstram indisponibilidade total; demonstram caminhos baratos para gerar erro interno.

## O que passou nos cenários exercitados

- Todas as rotas de escrita protegidas recusaram ausência de sessão, ID de usuário forjado,
  token inválido e sessão expirada.
- Rotas `/admin/**` recusaram contas comuns, inclusive com cabeçalhos de papel/usuário falsificados.
- Leituras privadas sondadas recusaram as quatro identidades inválidas, exceto o diagnóstico de mídia.
- Troca de sessão e cabeçalho `X-User-Id` não mudaram o autor nem reiniciaram a cota de publicações.
- Retry sequencial do mesmo comando não duplicou posts nem consumiu a cota de publicação novamente.
- Texto duplicado com mudanças de caixa/espaçamento foi recusado por autor; outro autor manteve sua cota.
- Um terceiro não conseguiu ler/enviar em conversa alheia nem editar/apagar post de outro autor.
- Conta comum não alterou cotação nem concedeu prêmios monetários.
- Valores monetários zero, negativos e extremos não alteraram os saldos dos participantes.
- Reuso de chave de idempotência por usuários distintos manteve o isolamento das transferências.
- Corrida para comprar a mesma moldura não cobrou duas vezes no ensaio realizado.
- Terceiros não confirmaram/reembolsaram compras alheias; estados terminais recusaram nova decisão.
- As proteções usuais contra script, eventos inline, iframe desconhecido, HTML de formulário e URLs
  executáveis diretas bloquearam os payloads correspondentes de criação e edição.

## Limitações e verificações ainda necessárias

- Esta é uma auditoria do commit indicado, não uma inspeção da versão efetivamente implantada.
- Não foram lidos tokens reais nem coletados dados de usuários. Evidências e contas são sintéticas.
- Não foram executados testes destrutivos de carga contra usuários, storage ou provedores externos.
- Não foram inspecionados WAF/ALB, políticas de bucket, CORS/CSP efetivos no gateway, SMTP ou configuração
  real de administradores. As constatações que dependem disso estão qualificadas acima.
- Os limitadores e caches de deduplicação são locais ao processo: múltiplas réplicas multiplicam
  cotas. Reinício/evicção também precisa ser considerado ao desenhar a defesa de flood.
- O executor do automod usa fila sem limite explícito. Saturação por múltiplas contas/replicação exige
  ensaio específico, com limite de carga e observabilidade; não foi alegada exaustão de memória.
- Rotas de leitura pública e fluxos de domínio extensos permanecem com cobertura parcial. A matriz
  inclui sondas de entrada, mas não transforma uma sonda de autenticação ou paginação em prova
  de todas as regras de propriedade e estado.

## Reprodução

Resultados anteriores às correções, nas nove classes originais:

| Ambiente | Executados | Passaram | Falhas de asserção | Erros de execução |
| --- | ---: | ---: | ---: | ---: |
| H2 | 1.588 | 1.556 | 32 | 0 |
| MariaDB 11.8.9, identificadores escapados como em QA | 1.588 | 1.555 | 33 | 0 |

São os mesmos casos repetidos em dois bancos, não 3.176 casos independentes.
A diferença inclui respostas 500 sob concorrência de publicações no MariaDB.
O ensaio MariaDB aplicou **122 migrações até V137** em banco vazio.
A compilação `./mvnw -B -o -DskipTests compile` passou com Java 25.
O Chrome executou nove cenários controlados dos três payloads selecionados.

Suíte anterior, executada antes de adicionar os testes: **671 testes, 18 falhas, 4 erros e 1 ignorado**.
Foram excluídos os testes temporários que leem um export real do LinkedIn. Três erros envolveram DNS
sem rede; os demais resultados de referência estão registrados separadamente, não atribuídos a esta mudança.

```bash
./mvnw -B -o -Dtest='*Test,*Tests,!TempRealZipTests' test
./mvnw -B -o -Dtest='com.socialcoisa.rede.securityaudit.*Tests,S3UploadConstraintAuditTests' test
```

O segundo comando reproduzia as falhas na base; na branch de correções deve passar.
O ambiente precisa de Java 25 e dependências Maven disponíveis para usar `-o`.

Para testar um banco **descartável**, fornecer as propriedades de conexão desse banco e o dialect:

```bash
./mvnw -B -o \
  -Dspring.datasource.url=jdbc:mariadb://127.0.0.1:3306/security_audit_qa \
  -Dspring.datasource.driver-class-name=org.mariadb.jdbc.Driver \
  -Dspring.datasource.username=root \
  -Dspring.datasource.password= \
  -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect \
  -Dspring.jpa.properties.hibernate.globally_quoted_identifiers=true \
  -Dtest='com.socialcoisa.rede.securityaudit.*Tests,S3UploadConstraintAuditTests' test
```

O exemplo usa a conta sem senha de um contêiner sem rede externa e sem dados persistentes.
Não reutilizar a configuração de acesso desse exemplo em serviço exposto.
Usar banco vazio em cada execução: as fixtures têm nomes estáveis e alguns testes deixam estado proposital.

Ensaio de navegador, depois de gerar as fixtures com `StoredHtmlSecurityAuditTests`:

```bash
python3 scripts/security/browser_html_probe.py --assert-inert
```

Saídas regeneráveis: `target/security-audit/routes.tsv`, `target/security-audit/html/` e
`target/security-audit/browser-evidence.json`. As evidências resumidas desta execução estão em
[RESULTADOS-2026-10-21.json](RESULTADOS-2026-10-21.json).

## Ordem de correção sugerida

1. Identidade administrativa imutável e cota compartilhada de autenticação.
2. Persistência de tentativas e consumo atômico do segundo fator.
3. Política central de bloqueio/contato e inclusão consentida de menores em conversas.
4. Detecção de flood com intervalo real, gates de edição e deduplicação com rollback correto.
5. Sanitização sem bypass por escape/SMIL, vínculo de tamanho no storage e proteção do diagnóstico.
6. Tratamento de conflitos/deadlocks e consistência das propriedades SQL entre perfis de deploy.

A primeira coleta adicionou testes e evidências. As correções de produção foram implementadas
em seguida e estão documentadas no relatório vinculado no início. Não foi feito deploy.
