package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.moderation.application.port.out.SuspiciousUserReader;

/**
 * Agrega os sinais em uma única consulta. Não usa as contagens de reputação porque elas cobrem
 * apenas parte dos alvos (posts/comentários/vagas) e ignoram denúncias contra o PERFIL.
 *
 * O LIMIT é defensivo: a triagem olha só as contas mais recentes; quem precisa de todo o
 * histórico usa o painel de denúncias normal.
 */
@Component
class JdbcSuspiciousUserReader implements SuspiciousUserReader {

	private static final String SQL = """
		select u.id, u.username, u.name, u.email, u.avatar, u.bio, u.created_at, u.email_verified_at,
			(select count(*) from posts p where p.author_id = u.id) as post_count,
			(select count(*) from comments c where c.author_id = u.id) as comment_count,
			(select count(*) from reports r
				where r.target_type = 'USER' and r.target_id = u.id
				and r.status in ('PENDING', 'APPROVED', 'AUTO_PENALIZED')) as report_count,
			case when exists (
				select 1 from moderator_applications m
				where m.user_id = u.id and m.status = 'APPROVED'
			) then 1 else 0 end as moderator
		from users u
		where u.created_at >= ?
		order by u.created_at desc
		limit ?
		""";

	private static final String SQL_BY_PATTERN = """
		select u.id, u.username, u.name, u.email, u.avatar, u.bio, u.created_at, u.email_verified_at,
			(select count(*) from posts p where p.author_id = u.id) as post_count,
			(select count(*) from comments c where c.author_id = u.id) as comment_count,
			(select count(*) from reports r
				where r.target_type = 'USER' and r.target_id = u.id
				and r.status in ('PENDING', 'APPROVED', 'AUTO_PENALIZED')) as report_count,
			case when exists (
				select 1 from moderator_applications m
				where m.user_id = u.id and m.status = 'APPROVED'
			) then 1 else 0 end as moderator
		from users u
		where u.created_at >= ?
			and (%s)
		order by u.created_at desc
		limit ?
		""";

	private final JdbcTemplate jdbc;

	JdbcSuspiciousUserReader(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public List<Candidate> findMatching(List<String> patterns, Instant since, int limit) {
		List<String> clean = patterns.stream()
			.filter(pattern -> pattern != null && !pattern.isBlank())
			.map(pattern -> "%" + pattern.trim().toLowerCase() + "%")
			.toList();
		if (clean.isEmpty()) {
			return List.of();
		}
		// Padrão vem de configuração (nunca do usuário): a cláusula é montada a partir dela.
		String clause = String.join(" or ",
			clean.stream().map(p -> "lower(u.username) like ? or lower(u.name) like ?").toList());
		List<Object> args = new java.util.ArrayList<>();
		args.add(Timestamp.from(since));
		for (String pattern : clean) {
			args.add(pattern);
			args.add(pattern);
		}
		args.add(Math.max(1, limit));
		return jdbc.query(SQL_BY_PATTERN.formatted(clause), (rs, rowNum) -> new Candidate(
			rs.getString("id"),
			rs.getString("username"),
			rs.getString("name"),
			rs.getString("email"),
			rs.getString("avatar"),
			rs.getString("bio"),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("email_verified_at") == null ? null : rs.getTimestamp("email_verified_at").toInstant(),
			rs.getLong("post_count"),
			rs.getLong("comment_count"),
			rs.getLong("report_count"),
			rs.getInt("moderator") == 1), args.toArray());
	}

	@Override
	public List<Candidate> findRecent(Instant since, int limit) {
		return jdbc.query(SQL, (rs, rowNum) -> new Candidate(
			rs.getString("id"),
			rs.getString("username"),
			rs.getString("name"),
			rs.getString("email"),
			rs.getString("avatar"),
			rs.getString("bio"),
			rs.getTimestamp("created_at").toInstant(),
			rs.getTimestamp("email_verified_at") == null ? null : rs.getTimestamp("email_verified_at").toInstant(),
			rs.getLong("post_count"),
			rs.getLong("comment_count"),
			rs.getLong("report_count"),
			rs.getInt("moderator") == 1), Timestamp.from(since), limit);
	}
}
