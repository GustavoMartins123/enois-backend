-- Posts do tipo PAGE: o autor cola um HTML e ele vira uma mini pagina renderizada dentro do post.
-- O backend sanitiza antes de gravar (allowlist de tags/atributos + CSS), mas a coluna guarda
-- exatamente o que foi sanitizado, para que o render (iframe sandboxed) nao precise reprocessar.
-- varchar(12000) cabe no limite de linha do MySQL/MariaDB em utf8mb4 e aceita o mesmo DDL no H2.
alter table posts add column page_html varchar(12000);
