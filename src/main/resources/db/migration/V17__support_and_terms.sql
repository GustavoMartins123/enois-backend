-- Consentimento de termos/idade no cadastro (guarda o instante do aceite).
alter table users add column terms_accepted_at timestamp null;

-- Pedidos de suporte (atendimento humano).
--
-- Sem FK para users de proposito: o banco de producao usa utf8mb4_unicode_ci como collation
-- padrao enquanto a tabela users e utf8mb4_uca1400_ai_ci, e uma FK entre collations diferentes
-- falha com "Foreign key constraint is incorrectly formed" (errno 150) derrubando o boot.
-- A integridade e garantida na aplicacao (a exclusao de conta anonimiza o usuario, nao apaga).
-- O charset/collation tambem e explicito para nao depender do default do servidor.
create table support_requests (
  id varchar(255) not null,
  user_id varchar(255) not null,
  subject varchar(160) not null,
  message varchar(4000) not null,
  contact_email varchar(255) not null,
  status varchar(32) not null,
  created_at timestamp not null,
  primary key (id),
  key idx_support_requests_created (created_at),
  key idx_support_requests_status (status),
  key idx_support_requests_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_uca1400_ai_ci;
