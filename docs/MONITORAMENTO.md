# Monitoramento e defesa em tempo real

Registro dos ciclos de monitoramento e defesa do Énóis: o estado do app, o que foi
encontrado e o que foi feito. Uma linha por ciclo, sem inventar o que não foi lido.

## Como o ciclo funciona

- **Sinais**: `GET /api/stats/community` (3x), a home, `/api/health`, e uma checagem de que o
  limite por IP do login continua de pé. Latência de rota pública acima de ~2s, 5xx/503 ou
  timeout é incidente em andamento.
- **Logs**: dependem de acesso ao cluster. O bastion (`44.200.215.79`) hoje **não** tem
  credencial AWS (`aws sts get-caller-identity` falha) nem `kubectl`; para ler os logs do backend
  falta um destes: kubeconfig no bastion, permissão de CloudWatch (`aws logs`) para a instância,
  ou o endpoint de logs do cluster. Sem isso, o relatório diz que não houve leitura de log.
- **Defesa**: `shared/security/AbuseGuardFilter` (branch `feature/seguranca-risco`, commit
  `1c4c841`) — 20 erros em 3 min por IP → 429 com `Retry-After` por 15 min, dobrando na
  reincidência. Protege quando estiver publicado; enquanto não estiver, o ciclo só registra.
- **Nada de**: teste de carga contra a produção, bloqueio manual de IP sem ordem, mexer em
  infraestrutura.

## Ciclos

- **2026-10-22 14:30** — app saudável: `/api/stats/community` 200 em 0,81s/0,81s/1,02s (a
  distância até `us-east-1` explica o RTT; não é fila), home 200 em 1,19s, `/api/health` 200 em
  1,49s. Login respondeu 401 nas 6 tentativas erradas (o limite por IP é 60/min, então 6 não
  estouram — não é furo). Nenhum sinal de ataque ativo nos sinais medidos. Sem leitura de log
  (bastion sem credencial AWS nem kubectl). Defesa pronta no código, **falta publicar**.
