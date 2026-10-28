# Economia da faísca — cotação por uso e emissão escalada

A faísca é um **token de utilidade** com supply fixo de 2.000.000 (gênese). Nada é emitido além
disso: os ganhos saem da tesouraria e as transferências apenas movem saldo entre contas. Este
documento descreve o cálculo automático da **cotação de referência** (BRL) e a conversão da
**emissão automática** por essa cotação.

> A cotação é uma referência interna para remunerar criadores e exibir a mini bolsa. **Não é
> câmbio**: comprar faísca com dinheiro não existe, e não há promessa de valor nem mercado
> secundário (Termos, item 13).

## Como o cálculo roda

`CoinEconomyService` executa uma vez por dia (`rede.coin.economy.cron`, padrão `0 10 3 * * *`):

1. Mede, na janela (`window-days`, padrão 30 dias), três coisas no ledger:
   - **Uso em escrow** (`ESCROW_HOLD`): compras no marketplace e contratos de serviço pagos em
     faíscas.
   - **Transferências P2P** (`TRANSFER_IN`): o giro entre pessoas.
   - **Emissão automática** (`REWARD`): presença, sequência, reações, jogos diários, bônus de
     microprojeto e prêmios.
2. Compara o uso com a **meta de giro** (`target-turnover`, padrão 5% do estoque em circulação no
   período). Uso acima da meta empurra o preço para cima; emissão dilui e empurra para baixo.
3. Aplica no máximo **1% de variação por execução** (`max-step`) e respeita piso de 1 centavo e
   teto de R$ 1,00 por faísca (`min-cents` / `max-cents`).
4. Se o valor mudou, publica uma nova linha em `coin_rate_history` — a mini bolsa mostra o
   histórico. Sem mudança, nada é gravado.

### Fórmula

```
usoPonderado = escrow + p2p × p2p-weight            (p2p-weight padrão 0,5)
usoEfetivo   = min(usoPonderado, circulação × max-usage-ratio)
razão        = usoEfetivo / (circulação × target-turnover)
diluição     = 1 + emissão / circulação
índice       = razão / diluição
variação     = clamp((índice − 1) × sensitivity, −max-step, +max-step)
novaCotação  = clamp(cotaçãoAtual × (1 + variação), min, max)
```

- **Sem circulação**, o preço não é reavaliado.
- **P2P entra com peso menor** porque é circulação, não consumo.
- **Teto de uso por janela** limita ping-pong entre contas (lavagem) a uma volta do estoque.

## Emissão automática escalada

Os valores-base continuam definidos na cotação inicial (100 faíscas = R$ 1,00). Na hora de pagar,
`CoinService.scaledReward(base)` converte para a cotação vigente **preservando o valor em reais**:

```
faíscas = base × 10.000 / cotaçãoAtualEmMicros     (mínimo 1 milésimo, piso de 0,1 centavo)
```

- Faísca subiu → a pessoa recebe **menos faíscas** pelo mesmo valor.
- Faísca caiu → recebe **mais faíscas** pelo mesmo valor.
- Escala: presença diária (5), bônus de sequência (25), reação recebida (1), prêmios dos jogos
  diários (10/5/3/1) e bônus de microprojeto (200).

**Não são escalados:** o airdrop inicial (distribuição do gênese), o comunicado histórico de
desculpas e os prêmios concedidos manualmente pela administração — são valores decididos na hora,
não emissão recorrente.

## Unidade

A cotação é guardada em **micro-reais por faísca** (`brl_micros`; 1 centavo = 10.000 µ) para
permitir passos de 1%. A API expõe:

- `brlMicros` — precisão real (novo);
- `brlCents` — leitura arredondada, mantida para compatibilidade.

## Parâmetros (env)

| Propriedade | Env | Padrão |
|---|---|---|
| `rede.coin.economy.enabled` | `COIN_ECONOMY_ENABLED` | `true` |
| `rede.coin.economy.cron` | `COIN_ECONOMY_CRON` | `0 10 3 * * *` |
| `rede.coin.economy.zone` | `COIN_ECONOMY_ZONE` | `America/Sao_Paulo` |
| `rede.coin.economy.window-days` | `COIN_ECONOMY_WINDOW_DAYS` | `30` |
| `rede.coin.economy.target-turnover` | `COIN_ECONOMY_TARGET_TURNOVER` | `0.05` |
| `rede.coin.economy.p2p-weight` | `COIN_ECONOMY_P2P_WEIGHT` | `0.5` |
| `rede.coin.economy.max-usage-ratio` | `COIN_ECONOMY_MAX_USAGE_RATIO` | `1.0` |
| `rede.coin.economy.sensitivity` | `COIN_ECONOMY_SENSITIVITY` | `0.25` |
| `rede.coin.economy.max-step` | `COIN_ECONOMY_MAX_STEP` | `0.01` |
| `rede.coin.economy.min-cents` | `COIN_ECONOMY_MIN_CENTS` | `1` |
| `rede.coin.economy.max-cents` | `COIN_ECONOMY_MAX_CENTS` | `100` |

Desligar `enabled` congela a cotação automática (a publicação manual continua funcionando).

## Observabilidade

`GET /admin/carteira/economia` (admin) devolve janela, uso de escrow, P2P, emissão, circulação,
índice e a cotação sugerida — sem publicar nada. `POST /admin/carteira/economia/recalcular`
(admin) roda o cálculo na hora e publica se o valor mudou. O worker loga
`coin_economy quote_micros=...` quando publica.