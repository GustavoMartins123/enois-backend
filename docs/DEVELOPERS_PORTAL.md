# Portal de desenvolvedores — `developers.enois.ia.br`

## Estado
- **Conteúdo pronto**: a documentação inicial é a rota pública `/developers` do front (arquivo
  `src/routes/developers.tsx`). Ela documenta apenas o que existe, com exemplos sintéticos.
- **Subdomínio NÃO publicado ainda**: falta **DNS + TLS + roteamento** (infraestrutura). Enquanto isso
  a página fica acessível em `https://enois.ia.br/developers`.
- **OpenAPI/referência completa**: em preparação (`/api/v1` + spec validada no CI). Não documentamos
  nada fictício.

## O que falta para servir no subdomínio (passos de infra — dependem do responsável)
1. **DNS**: criar `developers.enois.ia.br` como **CNAME** para o host do ingress do front (ou um
   registro A para o mesmo IP/load balancer que serve `enois.ia.br`).
2. **TLS**: emitir/usar certificado para `developers.enois.ia.br` (mesmo `cert-manager`/CA do
   domínio raiz, ou certificado wildcard `*.enois.ia.br`).
3. **Ingress/Host rule**: adicionar um host `developers.enois.ia.br` no ingress do front apontando
   para o mesmo serviço, com **rewrite** para `/developers` (ou servir o app e deixar a rota responder
   em `/`). Garantir HTTPS forçado.
4. **Cabeçalhos de segurança** no host novo: HSTS, `X-Content-Type-Options: nosniff`,
   `X-Frame-Options: DENY`, `Referrer-Policy`, e CSP compatível com a página (é conteúdo estático; não
   habilita console interativo com credenciais).
5. **Links canônicos**: trocar as referências de docs para `https://developers.enois.ia.br` (sem
   apontar para localhost/dev). Ex.: `rede.docs.url`.
6. **Nada sensível**: a página é pública e **não** expõe variáveis de ambiente, tokens, segredos,
   detalhes de infraestrutura nem dados reais de usuários — só exemplos com valores fictícios.

## Regras que a doc deve respeitar (antiabuso)
- A API pública **não** expõe criação de post para execução automática; a publicação final fica sob
  controle humano. Ver `PUBLISHING_POLICY.md`.
- Documentação **não** pode divergir do backend: quando o OpenAPI entrar, o CI **falha** se divergir.

## Domínio no código (quando as etapas acima existirem)
- `rede.docs.url=https://developers.enois.ia.br` (expor em `/identity`/config pública se necessário).
- Não usar `localhost` em nenhum exemplo publicado.
