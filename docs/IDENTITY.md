# Verificação de identidade (Énóis)

## Objetivo
Permitir que a conta comprove controlar uma **identidade civil verificada por um provedor
confiável**, com selo no perfil — **sem** o Énóis virar repositório de documentos.

## Arquitetura (independente de fornecedor)
- Porta `identity.application.port.out.IdentityVerificationProvider` (iniciar, consultar desfecho,
  cancelar). O domínio não conhece gov.br nem endpoint de fornecedor.
- `IdentityVerificationProviderRegistry` injeta provedores. **Em produção hoje a lista é vazia** →
  a verificação fica **indisponível** e `start` responde `503 IDENTITY_PROVIDER_UNAVAILABLE`
  (fail-safe: nunca aprova por simulação).
- Estados explícitos com transições controladas: `NOT_VERIFIED, PENDING, VERIFIED, REJECTED,
  EXPIRED, REVOKED, ERROR`. O front nunca define `VERIFIED` — só o retorno confirmado do provedor.
- Só o backend altera estado; não existe rota que aceite estado vindo do cliente.

## Dados persistidos (`identity_verifications`, V35)
Mínimos: `user_id` (único), `provider`, `state`, `attempt_id`, `reason_code`, `started_at`,
`completed_at`, `expires_at`, `last_validated_at`, `consent_version`, `updated_at`.

**Deliberadamente NÃO persistimos**: CPF, RG, documentos, selfies, biometria, imagens faciais,
access/refresh/ID tokens do provedor, respostas OAuth completas, payloads do provedor.

## API
- `GET /identity` — estado do próprio usuário.
- `POST /identity/verification` — inicia (503 sem provedor). Corpo: `{ "consentVersion": "v1" }`.
- `POST /identity/verification/refresh` — consulta o provedor e aplica o desfecho confirmado.
- `DELETE /identity/verification` — desvincula (vira `REVOKED`).
- `GET /identity/eligibility` — decisão central de publicação de vaga.

## Política central de vagas
`jobs.application.JobPublishingPolicy`: `reputação >= 150` **E** identificidade verificada.
A exigência de identidade fica atrás de `rede.identity.enforcement-enabled` (**padrão false**):
sem provedor autorizado, exigi-la bloquearia TODA publicação em produção. A decisão (o que falta)
é exposta mesmo com a exigência desligada.

## Situação do gov.br (BLOQUEADO por dependência externa)
O ecossistema de identidade digital do gov.br **não é aberto a qualquer serviço privado**:
depende de elegibilidade, autorização e homologação. **Não houve** integração, credenciais ou
endpoints oficiais usados — nada de simulação. Para habilitar de forma legítima:
1. Confirmar elegibilidade do Énóis junto ao programa gov.br (ou contratar provedor privado
   legítimo de verificação de identidade, ex.: bancos/fintechs com API de KYC).
2. Obter credenciais/homologação e implementar **um** bean `IdentityVerificationProvider` para o
   provedor (validação de state/PKCE/assinatura conforme o protocolo). O domínio não muda.
3. Ligar `IDENTITY_ENFORCEMENT_ENABLED=true` quando quiser exigir identidade para publicar vagas.

## Testes
`IdentityVerificationServiceTests` cobre: provedor indisponível (fail-safe, 503, não verifica),
início → PENDING (pendência não verifica), desfecho VERIFIED só pelo provedor, recusa não verifica,
revogação desverifica e transição inválida rejeitada.
