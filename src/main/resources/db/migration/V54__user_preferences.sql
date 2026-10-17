-- Preferencias do usuario que acompanham a conta entre dispositivos.
-- O tema da colecao (identificador do front, validado no backend) e a primeira;
-- a tabela e o lugar de preferencias futuras, uma linha por usuario.
create table user_preferences (
  user_id varchar(255) not null,
  theme_id varchar(32) not null,
  updated_at timestamp not null,
  constraint pk_user_preferences primary key (user_id),
  constraint fk_user_preferences_user foreign key (user_id) references users (id)
);
