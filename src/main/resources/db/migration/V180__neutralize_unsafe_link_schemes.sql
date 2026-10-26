-- Links stored before the scheme allowlist are rendered as href.
-- A value "has a scheme" when, after dropping tab/CR/LF (browsers ignore them inside a URL), leading
-- spaces and leading non-printable characters, it starts with letters/digits/+.- followed by ':'.
-- http, https and mailto are kept, and so is host:port without a scheme ("meusite.com:8080").
-- What happens to existing rows:
--   * single-link columns: a value with any other scheme (javascript:, data:, vbscript:...) is set to
--     null. Values without a scheme ("meusite.com", "meusite.com:8080/x") are kept.
--   * user_external_links: the same rule deletes the offending link rows (other links stay).
--   * link lists kept as one text (developer_profiles.links, corporate_profiles.external_links, entries
--     separated by comma/space/newline): the whole value is set to null when any entry has such a
--     scheme. Text that merely contains "data:" inside a word or after other text is kept.
-- Markdown links inside free text are not rewritten here; the front renders legacy ones through
-- its safe-href helper and new ones are refused by the API.
-- The patterns avoid backslashes so MariaDB (PCRE) and H2 (java.util.regex) read them the same way.

update companies set website = null
 where lower(trim(replace(replace(replace(website, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update users set portfolio = null
 where lower(trim(replace(replace(replace(portfolio, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update users set resume_url = null
 where lower(trim(replace(replace(replace(resume_url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update user_certifications set credential_url = null
 where lower(trim(replace(replace(replace(credential_url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update community_projects set url = null
 where lower(trim(replace(replace(replace(url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update community_projects set repo_url = null
 where lower(trim(replace(replace(replace(repo_url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update user_projects set repo_url = null
 where lower(trim(replace(replace(replace(repo_url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update games set website_url = null
 where lower(trim(replace(replace(replace(website_url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update games set support_url = null
 where lower(trim(replace(replace(replace(support_url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update service_offers set link = null
 where lower(trim(replace(replace(replace(link, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update events set stream_url = null
 where lower(trim(replace(replace(replace(stream_url, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

delete from user_external_links
 where lower(trim(replace(replace(replace(external_link, char(9), ''), char(10), ''), char(13), ''))) regexp '^[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$))[a-z][a-z0-9+.-]*:';

update developer_profiles set links = null
 where lower(links) regexp concat('(^|[', ' ,', char(9), char(10), char(13), '])[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$|[', ' ,', char(9), char(10), char(13), ']))[a-z][a-z0-9+.-]*:');

update corporate_profiles set external_links = null
 where lower(external_links) regexp concat('(^|[', ' ,', char(9), char(10), char(13), '])[^ -~]*(?!(https?|mailto):)(?![a-z0-9.-]+:[0-9]+([/?#]|$|[', ' ,', char(9), char(10), char(13), ']))[a-z][a-z0-9+.-]*:');
