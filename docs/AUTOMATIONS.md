# Automações, LinkedIn e importação de vagas — plano

## Situação e limites externos (leia primeiro)
- **Publicar no LinkedIn (autopost) está BLOQUEADO**: a API exige um **app aprovado** pelo
  LinkedIn com o escopo `w_member_social` (processo de revisão do LinkedIn) e credenciais
  `LINKEDIN_CLIENT_ID`/`SECRET`. Sem isso, qualquer "post no LinkedIn" seria simulação — não fazemos.
- **Importar vagas do LinkedIn por scraping NÃO será feito**: viola os Termos de Uso deles.
  Importação só de fontes que permitem (RSS/JSON/CSV de parceiros, ATS com API pública, portais
  com feed oficial).
- **Importar dados de perfil do LinkedIn** pela API também depende do app aprovado (`r_liteprofile`
  / OpenID). Enquanto não houver, oferecemos importação por upload do "Exportar dados" (CSV/JSON)
  que o próprio usuário baixa do LinkedIn — sem scraping e sem credencial.

## 1. Conectar com LinkedIn (quando o app for aprovado)
- Porta `SocialPublishingProvider` (publicar, agendar, consultar) — mesma ideia da
  `IdentityVerificationProvider`: o domínio não conhece o fornecedor.
- `LinkedInProvider` implementa OAuth 2.0/OIDC com **state de uso único**, PKCE, validação de
  issuer/audience e troca de código **no backend**; tokens guardados cifrados (chave já existe:
  `REDE_SECURITY_ENCRYPTION_KEY`), nunca no frontend.
- Escopos mínimos: identidade (`openid profile`) e publicação (`w_member_social`). Nada de ler
  conexões/mensagens.
- "Sincronizar posts": publicar no Énóis → opcionalmente espelhar no LinkedIn (opt-in por post).

## 2. Agendar posts
- Tabela `scheduled_posts` (id, user_id, conteúdo, mídia, canal destino, `scheduled_at`, status
  PENDING/SENT/FAILED/CANCELED, tentativas, `provider_post_id`).
- Worker de outbox já existente processa o vencimento; publica localmente sempre e, se o canal
  externo estiver conectado, espelha. **Falha de canal externo não impede o post local.**
- Idempotência por (post_id, canal) para não publicar duas vezes em retry.

## 3. Página de automações
- Rota `/automacoes` (perfil/config): seções **Canais conectados** (LinkedIn + estado), **Posts
  agendados**, **Importação de vagas**.
- Cada canal mostra estado real do backend (conectado/indisponível) — sem botão morto. Enquanto o
  LinkedIn não estiver aprovado, aparece **indisponível**, com o motivo.

## 4. Importar vagas de plataformas externas
- Porta `JobImportSource` (buscar → normalizar → `ImportedJob{title, company, location, type, level,
  description, applyUrl, source, externalId}`). Um parser por fonte; começa pelas legítimas
  (RSS/JSON/CSV). **Sem scraping de LinkedIn.**
- Tabela `imported_jobs` com **unique(source, external_id)** para deduplicar; o usuário revisa antes
  de publicar (nada entra como vaga ativa sem confirmação, e ainda vale a política de publicação:
  reputação + identidade quando a flag estiver ligada).
- Endpoints: `GET /automations/sources`, `POST /automations/imports` (dispara), `GET
  /automations/imports` (histórico), `POST /automations/imports/{id}/publish`.

## 5. Comentários com mídia, GIF e áudio (independente do LinkedIn — pode começar já)
- Modelo de anexo no comentário: `comment_attachments` (comment_id, kind IMAGE/GIF/AUDIO/EMBED,
  url/media_id, provider, position). Um comentário pode ter 1 anexo principal + links.
- **GIF (Giphy/Tenor)**: renderizar por URL pública (sem chave) com `<img>`/`<video>`; provedor
  detectado pela URL. Sem upload de terceiros.
- **Áudio**: gravação pelo microfone (MediaRecorder) → upload como mídia (purpose `COMMENT_AUDIO`)
  + player `<audio>`; OU embed de YouTube/Spotify por URL (detecção + player).
- Segurança: whitelist de domínios de embed, `sandbox` em iframe, sem HTML cru.

## Ordem sugerida
1. Comentários com mídia/GIF/áudio/embeds (não depende de ninguém).
2. Agendamento de posts + página de automações (local primeiro).
3. Importador de vagas plugável (fontes legítimas).
4. LinkedIn (perfil + publicação) — só após app aprovado; então é plugar um bean.

## O que falta de você para destravar o LinkedIn
- Criar/ter um app no LinkedIn Developer, solicitar os escopos de **Sign In with LinkedIn** e
  **Share on LinkedIn**, passar pela revisão, e fornecer `LINKEDIN_CLIENT_ID`/`LINKEDIN_CLIENT_SECRET`
  + registrar as redirect URIs (`https://enois.ia.br/api/social/linkedin/callback`). Sem isso, fica
  indisponível — e honesto.
