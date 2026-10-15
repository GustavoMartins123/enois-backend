-- Consentimento de termos/idade no cadastro (guarda o instante do aceite).
alter table users add column terms_accepted_at timestamp null;

-- Pedidos de suporte (atendimento humano).
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
  constraint fk_support_requests_user foreign key (user_id) references users (id)
);
