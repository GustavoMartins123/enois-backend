# Correções de segurança e evidências — 21/10/2026

Branch: `fix/seguranca-regras-negocio`. Base: `a6a7b10`.

A auditoria original está em [AUDITORIA-2026-10-21.md](AUDITORIA-2026-10-21.md).
Os achados foram reproduzidos antes de alterar produção: **1.588 casos, 32 falhas de
asserção, zero erros no H2**. A rodada original com MariaDB 11.8.9 também está preservada,
com 33 falhas. Quantidade de asserções que falham não equivale a quantidade de vulnerabilidades.

O resumo anterior não foi sobrescrito: [ANTES-H2-2026-10-21.json](ANTES-H2-2026-10-21.json)
contém contagens e nomes dos testes que falhavam, extraídos do XML Surefire.
[RESULTADOS-2026-10-21.json](RESULTADOS-2026-10-21.json) conserva os resultados da primeira auditoria.

## Comportamento corrigido e prova

| Achado | Antes, reproduzido localmente | Correção e condição verificada |
| --- | --- | --- |
| SEC-01 | Cadastro/rename para nome administrativo sintético libera `/admin/blocked-ips` (200). | Autoridade por `REDE_ADMIN_USER_IDS` ou e-mail verificado da allowlist. Nome editável permanece sem privilégio (403). IDs preservam autoridade após rename; e-mail não verificado não concede admin. |
| SEC-02 | Vinte logins inválidos impedem login válido de outra origem (429). | Autenticação usa limites por origem/conta, sem pool global `auth`. Outro cliente continua entrando (200); limites por IP continuam cobertos pela suíte existente. |
| SEC-03/04 | Cinco códigos errados deixam contador em zero; no H2 seis chamadas simultâneas emitem seis sessões. | Lock transacional do desafio; somente a exceção específica de código incorreto confirma o contador, demais erros desfazem a transação. Cinco erros persistem; seis verificações simultâneas permitem uma sessão e cinco conflitos 409. |
| SEC-05/06 | Mensagem em conversa antiga, criação genérica após bloqueio e contato imposto via `GROUP` chegam a 201. | Política aplicada no serviço a abertura e envio, em ambos os sentidos do bloqueio. Tipo de conversa não dispensa conexão aceita com menor. Recusa 403; fluxos permitidos têm regressões existentes. |
| SEC-07 | Comentário após bloqueio retorna 201; menção persiste notificação. | Comentários, reações e compartilhamentos verificam bloqueio. Notificação social bloqueada não grava linha nem emite evento de push. Decisão de sanção/recurso continua sendo entregue. |
| SEC-08 | Primeiro `NO_SIGNAL` consome intervalo e flood seguinte retorna `THROTTLED`. | Intervalo reservado atomicamente apenas quando há sinal; primeiro flood alcança análise. IA indisponível não autoriza punição automática. |
| SEC-09 | Conta restrita publica com 403, mas edita post antigo com 200. | Edição exige os gates de idade e sanção, como a publicação (403). Exclusão do próprio conteúdo continua disponível. |
| SEC-10/11 | Página sem título repete com 201; artigo recusado envenena deduplicação (409 ao corrigir). | Deduplicação usa HTML sanitizado da página; valida corpo antes da reserva e desfaz a própria reserva no rollback. Teste adicional corrige alvo de comentário recusado sem perder o texto. |
| SEC-12 | PUT assinado contém apenas `content-type;host`. | Assinatura inclui `content-length` e vincula o tamanho validado do asset. Teste usa SDK real e credenciais fictícias, sem upload ou acesso ao bucket. |
| SEC-13/14 | CSS escapado tenta buscar pixel remoto no Chrome; SMIL conserva URL executável. | Normalização de escapes/comentários CSS antes da checagem; bloqueio de funções de imagem com URL em string; allowlist de atributos animáveis. SVG e animações legítimas continuam cobertos. |
| SEC-15 | `/media/migration` retorna 200 a quatro identidades sem sessão válida. | Requer sessão administrativa: 401 sem autenticação válida, 403 para conta comum. |
| SEC-16 | Retries concorrentes geram 500. | Comandos autenticados serializam pelo usuário antes da consulta de idempotência; conflitos transacionais retornam 409. Retry concluído pode retornar 200 com replay. Razão e saldos conferidos; compra concorrente cobra uma vez. |
| SEC-17 | MariaDB sem escape global falha ao gravar coluna reservada `read`. | Mapeamento da coluna escapado, compatível com sua capitalização no H2 e com MariaDB; não renomeia coluna. QA já tinha escape global: o achado era dependente do perfil. |
| SEC-18 | Entrada inválida/ausência de parâmetros retorna 500 em quatro leituras públicas. | Parâmetro obrigatório ausente vira 400; limites da paginação são normalizados antes de consulta/cursor, preservando o formato da resposta. |

Os ensaios de publicação incluem rajada de 24 pedidos, 48 chamadas concorrentes em oito
threads, troca de sessão, identidade forjada e replay. A cota permanece em dez publicações
por hora por autor no cenário testado, sem consumir a cota de outra conta.

A limpeza também é reaplicada ao entregar posts/páginas e bios/banners de perfil existentes.
As linhas antigas não são reescritas. Um teste injeta HTML legado diretamente no banco
fictício e confere as entregas JSON e HTML.

**Não foi demonstrada execução de JavaScript.** O ensaio original de Chrome já registrou
ausência de execução com e sem CSP/sandbox. A prova positiva foi a tentativa de recurso CSS
remoto, interceptada localmente; SMIL foi tratado como reforço da sanitização.
Evidência original: [NAVEGADOR-ANTES-2026-10-21.json](NAVEGADOR-ANTES-2026-10-21.json).

## Deploy e contratos

**Sem migração.** Nenhuma versão Flyway é criada ou alterada. Desafios, notificações e
publicações existentes permanecem no schema atual. Locks usam transações/linhas existentes.

Antes de implantar, configurar `REDE_ADMIN_USER_IDS` com IDs das contas administrativas ou
manter `REDE_ADMIN_EMAILS` com e-mails já verificados. `REDE_ADMIN_USERNAMES` deixa de conceder
autoridade; instalações que dependiam exclusivamente dele precisam dessa atualização de configuração.

`GET /api/media/migration` passa a exigir `X-Session-Token` administrativo. Bloqueios e sanções
agora recusam as interações antes permitidas pelas lacunas descritas; conflitos concorrentes
podem retornar `409 CONCURRENT_WRITE`. Os formatos JSON existentes são preservados.
URLs novas de upload exigem o tamanho declarado; o navegador calcula `Content-Length` do
arquivo enviado. Nenhum cabeçalho proibido precisa ser definido pelo JavaScript do frontend.
URLs pré-assinadas já emitidas continuam válidas até expirar (janela atual: 60 minutos).

## Limites da evidência

Tudo foi local, com contas sintéticas, Java 25 e containers sem rede externa. Não houve
teste contra usuários, bucket, SMTP ou infraestrutura de produção. As 419 rotas inventariadas
têm sondas de autenticação/entrada; isso não prova todas as regras de negócio de cada rota.
Políticas de bucket/gateway e comportamento distribuído não foram validados. Rate limits e
deduplicação continuam locais ao processo: múltiplas réplicas multiplicam as cotas.

As asserções de segurança não foram dispensadas. Uma correção de teste foi necessária:
`containsOnly(200, 409)` exigia também a presença de 409, mas todos os retries podem legitimamente
ser replay 200. A asserção agora permite esses dois status e mantém número de respostas,
débito único, crédito único e conciliação do razão. As fixtures de HTML ganharam conteúdo
distinto por caso para que o deduplicador não bloqueasse testes independentes do sanitizador.

## Verificação executada

| Rodada | Executados | Passaram | Falhas | Erros | Ignorados |
| --- | ---: | ---: | ---: | ---: | ---: |
| Auditoria H2 antes | 1.588 | 1.556 | 32 | 0 | 0 |
| Auditoria MariaDB antes, escape global como no QA | 1.588 | 1.555 | 33 | 0 | 0 |
| Auditoria H2 depois | 1.602 | 1.602 | 0 | 0 | 0 |
| Auditoria MariaDB 11.8.9 depois, sem escape global | 1.602 | 1.602 | 0 | 0 | 0 |
| Auditoria MariaDB 11.8.9 depois, com escape global como no QA | 1.602 | 1.602 | 0 | 0 | 0 |
| Suíte completa depois | 2.274 | 2.251 | 18 | 4 | 1 |

As execuções da auditoria são repetições dos mesmos cenários em configurações diferentes,
não casos independentes somados. Há 14 casos adicionais em relação à coleta inicial:
autoridade administrativa, eventos de notificação, rollback de comentário, HTML legado,
variantes CSS/SMIL e diagnóstico administrativo.

| Classe da auditoria | Passaram por execução |
| --- | ---: |
| `RouteAuthorizationAuditTests` | 1.543 |
| `StoredHtmlSecurityAuditTests` | 23 |
| `BusinessRuleAdversarialTests` | 11 |
| `PublishingAbuseAuditTests` | 8 |
| `EconomyIntegrityAuditTests` | 5 |
| `AuthenticationStateAuditTests` | 3 |
| `AdminAuthorityAuditTests` | 3 |
| `NotificationContactAuditTests` | 3 |
| `SharedAuthenticationQuotaAuditTests` | 1 |
| `SpamDetectionTimingAuditTests` | 1 |
| `S3UploadConstraintAuditTests` | 1 |

Cada schema MariaDB foi criado vazio e recebeu as **122 migrações existentes até V137**;
a execução posterior usou `spring.jpa.hibernate.ddl-auto=validate`, sem criação automática
de tabelas pelo Hibernate. O escape global foi testado ligado e desligado. Sem migração nova.

O Chrome passou nos nove cenários dos três payloads originais com `--assert-inert`:
nenhum script executado, nenhuma URL SVG executável e nenhum pixel de `audit.invalid`.
O link legítimo para `example.invalid` é clicado pelo ensaio e sua navegação também é
interceptada. [Evidência posterior do navegador](NAVEGADOR-DEPOIS-2026-10-21.json).

**A suíte completa não ficou verde.** Os nomes dos 22 testes com falha/erro são exatamente
os mesmos da referência: zero casos novos falhando. Incluem contratos antigos de cadastro,
jogos/serviços, idade e galeria; três erros dependem de DNS bloqueado no ambiente e um envolve
Broken pipe no upload HTTP. A comparação foi feita por classe e nome do teste, não apenas
pela soma. A execução completa contém ainda um teste temporário de importação que retorna
`SEM ARQUIVO` neste container; nenhum export real foi lido.

Evidências extraídas do Surefire, sem propriedades do ambiente ou logs de requisições:

- [Depois H2](DEPOIS-H2-2026-10-21.json).
- [Depois MariaDB sem escape global](DEPOIS-MARIADB-2026-10-21.json).
- [Depois MariaDB como QA](DEPOIS-MARIADB-QA-2026-10-21.json).
- [Suíte completa posterior](SUITE-DEPOIS-2026-10-21.json).
- [Compilação Java 25](COMPILACAO-2026-10-21.txt).

## Reproduzir

Com Java 25 e dependências locais (retirar `-o` se precisar baixar dependências):

```bash
./mvnw -B -o -DskipTests compile
./mvnw -B -o -Dtest='com.socialcoisa.rede.securityaudit.*Tests,S3UploadConstraintAuditTests' test
python3 scripts/security/browser_html_probe.py --assert-inert
```

A rodada final H2 também executa `com.socialcoisa.rede.groups.*Tests` para verificar os
fluxos existentes após normalizar a paginação de grupos ao teto de 50 do serviço.
Essa rodada terminou em **1.652/1.652**: 1.602 da auditoria e 50 testes existentes de grupos.
Para conferir a suíte existente sem o diagnóstico temporário de export real:

```bash
./mvnw -B -o -Dtest='*Test,*Tests,!TempRealZipTests' test
```

MariaDB deve ser **descartável e vazio**, com dados exclusivamente fictícios:

```bash
./mvnw -B -o \
  -Dspring.datasource.url=jdbc:mariadb://127.0.0.1:3306/security_audit \
  -Dspring.datasource.driver-class-name=org.mariadb.jdbc.Driver \
  -Dspring.datasource.username=root -Dspring.datasource.password= \
  -Dspring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MariaDBDialect \
  -Dspring.jpa.properties.hibernate.globally_quoted_identifiers=false \
  -Dspring.jpa.hibernate.ddl-auto=validate \
  -Dtest='com.socialcoisa.rede.securityaudit.*Tests,S3UploadConstraintAuditTests' test
```

Repetir com outro banco vazio e `globally_quoted_identifiers=true` para a configuração de QA.
O exemplo corresponde ao container local sem rede externa; não é configuração de produção.
Os testes usam nomes estáveis e deixam estado: não reaproveitar o schema entre execuções.
`scripts/security/summarize_reports.py target/surefire-reports --audit-only` extrai as contagens.
