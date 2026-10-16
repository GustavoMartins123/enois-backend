# Segurança do Énóis

## Reporte responsável de vulnerabilidades
Encontrou uma falha? **Não abra issue pública.** Envie para **seguranca@enois.ia.br** com:
- descrição do problema e o impacto potencial;
- passos para reproduzir (ambiente, requisição/payload mínimo);
- se possível, uma prova de conceito que **não** acesse dados de terceiros nem cause indisponibilidade.

**Não** inclua em reportes, issues, prints ou PRs: dados pessoais de usuários, credenciais, tokens,
segredos de sessão ou detalhes exploráveis em público.

Retorno em até **5 dias úteis**. Pedimos um prazo de correção proporcional ao risco antes de qualquer
divulgação, e damos crédito a quem reportar, se quiser.

## Escopo
Em escopo: API pública (`/api`), frontend (`enois.ia.br`), autenticação, autorização, isolamento de
dados entre usuários/aplicações, integrações oficiais e o portal de documentação.

Fora de escopo: engenharia social, phishing contra pessoas do time, DDoS/ataques de volume, e testes
contra infraestrutura de terceiros. Testes automatizados agressivos só com autorização prévia por
escrito e **nunca** em dados de produção.

## Princípios que a plataforma adota
- **Negar por padrão**: só o que tem autorização explícita é exposto.
- **Autorização no backend** em toda operação protegida (nunca só no frontend).
- **DTOs com campos explícitos** — nenhuma entidade interna é serializada direto.
- **Minimização (LGPD)**: poucos dados, retenção proporcional, sem documentos/biometria no Énóis.
- **Credibilidade e identidade** são decididas só no servidor; o cliente não as declara.
- **Sem automação de interação social**: nada de publicar post/comentar/curtir automaticamente.

## Referências
OWASP API Security Top 10 e OWASP ASVS 5.0 (nível 2 como referência mínima de verificação). Isso
**não** equivale a certificação nem substitui uma avaliação de segurança independente.

## Rotação e revogação
Revogação de sessão e de credenciais de integração é suportada pelo backend; segredos ficam em
gerenciador/secret do ambiente, nunca no repositório, no bundle do frontend ou nos logs.
