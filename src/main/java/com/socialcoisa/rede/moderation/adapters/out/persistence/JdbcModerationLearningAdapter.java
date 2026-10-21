package com.socialcoisa.rede.moderation.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.socialcoisa.rede.moderation.application.port.out.ModerationLearningRepository;

@Repository
class JdbcModerationLearningAdapter implements ModerationLearningRepository {

	private final JdbcTemplate jdbc;

	JdbcModerationLearningAdapter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(Feedback feedback) {
		jdbc.update("""
			insert into moderation_feedback (id, report_id, decision, source, decided_by, signals, score, created_at)
			values (?, ?, ?, ?, ?, ?, ?, ?)
			""", feedback.id(), feedback.reportId(), feedback.decision(), feedback.source(),
			feedback.decidedBy(), feedback.signals(), feedback.score(), Timestamp.from(feedback.createdAt()));
	}

	@Override
	public List<Feedback> recent(int limit) {
		return jdbc.query("""
			select id, report_id, decision, source, decided_by, signals, score, created_at
			from moderation_feedback order by created_at desc limit ?
			""", (rs, rowNum) -> new Feedback(
			rs.getString("id"), rs.getString("report_id"), rs.getString("decision"),
			rs.getString("source"), rs.getString("decided_by"), rs.getString("signals"),
			(Integer) rs.getObject("score"), rs.getTimestamp("created_at").toInstant()),
			Math.max(1, limit));
	}

	@Override
	public Map<String, Integer> weights() {
		Map<String, Integer> weights = new HashMap<>();
		jdbc.query("select signal_key, weight from moderation_signal_weights",
			rs -> { weights.put(rs.getString("signal_key"), rs.getInt("weight")); });
		return Map.copyOf(weights);
	}

	@Override
	public void upsertWeight(String signal, int weight, int samples, Instant now) {
		Integer current = jdbc.query("select samples from moderation_signal_weights where signal_key = ?",
			rs -> rs.next() ? rs.getInt(1) : null, signal);
		if (current == null) {
			jdbc.update("""
				insert into moderation_signal_weights (signal_key, weight, samples, updated_at)
				values (?, ?, ?, ?)
				""", signal, weight, samples, Timestamp.from(now));
		}
		else {
			jdbc.update("""
				update moderation_signal_weights set weight = ?, samples = ?, updated_at = ? where signal_key = ?
				""", weight, samples, Timestamp.from(now), signal);
		}
	}
}
