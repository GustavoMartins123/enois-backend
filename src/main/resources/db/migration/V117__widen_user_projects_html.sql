-- Em producao, a tabela `user_projects` foi criada antes com a coluna `html` em varchar menor
-- (5.000 caracteres). O V61 foi editado no repositorio para longtext depois de ja ter sido aplicado
-- em producao, mas o Flyway nao reaplica migracoes ja registradas (validate-on-migrate=false silencia
-- o checksum e pula). Quando um usuario tentou salvar um card de projeto com HTML de ~8.700 caracteres
-- (dentro do teto anunciado de 20.000 do front e do SafeHtml), o MariaDB recusou com "Data too long for column 'html'" (500).
--
-- Este ALTER garante que a coluna seja LONGTEXT em todos os ambientes (como posts.page_html no V57
-- e communities.banner_html no V62). De quebra:
-- - `stack` passa a text (como mapeado na JPA entity, evitando estouro com 10 tags longas + newlines)
-- - `diary` passa a longtext (50 entradas de diario em JSON com emoji podem estourar os 64 KB de text)
-- - `scheduled_posts.page_html` passa a longtext (criado como text no V88, estourava com pagina >64KB)

alter table user_projects modify column html longtext;
alter table user_projects modify column stack text not null;
alter table user_projects modify column diary longtext;
alter table scheduled_posts modify column page_html longtext;
