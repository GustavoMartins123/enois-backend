-- Links stored before the scheme allowlist (http/https only for URL fields) are rendered as href.
-- What happens to existing rows:
--   * single-link columns: a value that has a scheme (contains ':') but does not start with
--     http:// or https:// is set to null. Values without any scheme (e.g. "meusite.com") are kept.
--   * user_external_links: the same rule deletes the offending link rows (other links stay).
--   * link lists kept as one text (developer_profiles.links, corporate_profiles.external_links):
--     the whole value is set to null when it contains a script: or data: scheme anywhere.
-- Markdown links inside free text are not rewritten here; the front renders legacy ones through
-- its safe-href helper and new ones are refused by the API.

update companies set website = null
 where website like '%:%' and lower(trim(website)) not like 'http://%' and lower(trim(website)) not like 'https://%';

update users set portfolio = null
 where portfolio like '%:%' and lower(trim(portfolio)) not like 'http://%' and lower(trim(portfolio)) not like 'https://%';

update users set resume_url = null
 where resume_url like '%:%' and lower(trim(resume_url)) not like 'http://%' and lower(trim(resume_url)) not like 'https://%';

update user_certifications set credential_url = null
 where credential_url like '%:%' and lower(trim(credential_url)) not like 'http://%'
   and lower(trim(credential_url)) not like 'https://%';

update community_projects set url = null
 where url like '%:%' and lower(trim(url)) not like 'http://%' and lower(trim(url)) not like 'https://%';

update community_projects set repo_url = null
 where repo_url like '%:%' and lower(trim(repo_url)) not like 'http://%' and lower(trim(repo_url)) not like 'https://%';

update user_projects set repo_url = null
 where repo_url like '%:%' and lower(trim(repo_url)) not like 'http://%' and lower(trim(repo_url)) not like 'https://%';

update games set website_url = null
 where website_url like '%:%' and lower(trim(website_url)) not like 'http://%'
   and lower(trim(website_url)) not like 'https://%';

update games set support_url = null
 where support_url like '%:%' and lower(trim(support_url)) not like 'http://%'
   and lower(trim(support_url)) not like 'https://%';

update service_offers set link = null
 where link like '%:%' and lower(trim(link)) not like 'http://%' and lower(trim(link)) not like 'https://%';

update events set stream_url = null
 where stream_url like '%:%' and lower(trim(stream_url)) not like 'http://%'
   and lower(trim(stream_url)) not like 'https://%';

delete from user_external_links
 where external_link like '%:%' and lower(trim(external_link)) not like 'http://%'
   and lower(trim(external_link)) not like 'https://%';

update developer_profiles set links = null
 where lower(links) like '%script:%' or lower(links) like '%data:%';

update corporate_profiles set external_links = null
 where lower(external_links) like '%script:%' or lower(external_links) like '%data:%';
