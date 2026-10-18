package com.socialcoisa.rede.importlinkedin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Importador do ARQUIVO DE DADOS do LinkedIn (Configurações → "Obter uma cópia dos seus
 * dados" → ZIP). O LinkedIn exige aprovação de parceiro para entregar histórico via API,
 * mas a exportação por arquivo é um direito do próprio membro — então o usuário sobe o ZIP,
 * o énois parseia e DEVOLVE o retrato para revisão; nada é salvo automaticamente.
 *
 * Formatos encontrados no arquivo: CSVs com BOM, colunas em inglês (Profile.csv,
 * Positions.csv, Education.csv, Certifications.csv, Projects.csv) e datas em "YYYY-MM"
 * ou "MMM YYYY" (pt/en) — o parser aceita as duas.
 */
@Service
public class LinkedinImportService {

	private static final int MAX_ZIP_BYTES = 5 * 1024 * 1024;

	// ===== retrato devolvido para revisão no front =====

	public record ExperiencePreview(String title, String company, String location, String description,
			String startDate, String endDate, boolean currentlyHere) {
	}

	public record EducationPreview(String school, String degree, String field, String description,
			String startDate, String endDate, boolean currentlyHere) {
	}

	public record CertificationPreview(String name, String issuer, String description, String issueDate,
			String expirationDate, String credentialUrl) {
	}

	public record ProjectPreview(String title, String description, String url, String startDate,
			String endDate) {
	}

	/** Campos do Profile.csv que o perfil do Énóis tem onde guardar. */
	public record ProfilePreview(String name, String headline, String summary, String location, String industry) {
	}

	public record LinkedinImportPreview(List<ExperiencePreview> experiences,
			List<EducationPreview> education, List<CertificationPreview> certifications,
			List<ProjectPreview> projects,
			/** Headline/sobre/localização/indústria do Profile.csv (aplicados no perfil). */
			ProfilePreview profile,
			/** Skills.csv — entram na lista de habilidades do perfil. */
			List<String> skills,
			/** O que veio no arquivo mas NÃO tem estrutura no Énóis (idiomas, prêmios, cursos…). */
			Map<String, Integer> semEstrutura,
			int found) {
	}

	public LinkedinImportPreview parse(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(400, "FILE_REQUIRED", "Envie o arquivo .zip exportado do LinkedIn.");
		}
		if (file.getSize() > MAX_ZIP_BYTES) {
			throw new ApiException(400, "FILE_TOO_LARGE", "O arquivo do LinkedIn deve ter no maximo 5 MB.");
		}
		Map<String, String> csvs = new LinkedHashMap<>();
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(file.getBytes()))) {
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null) {
				String name = entry.getName();
				if (entry.isDirectory() || !name.endsWith(".csv")) {
					continue;
				}
				// O ZIP do LinkedIn guarda os CSVs numa pasta (".../Positions.csv") — casa pelo nome.
				String simple = name.substring(name.lastIndexOf('/') + 1);
				String content = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
				csvs.putIfAbsent(simple, stripBom(content));
				zip.closeEntry();
			}
		} catch (IOException error) {
			throw new ApiException(400, "INVALID_FILE", "Não foi possível ler o arquivo. É um .zip do LinkedIn?");
		}
		if (!StringUtils.hasText(csvs.get("Positions.csv"))) {
			throw new ApiException(400, "NOT_A_LINKEDIN_EXPORT",
				"Não encontrei os CSVs do LinkedIn dentro do arquivo. Exporte em Configurações → "
					+ "\"Obter uma cópia dos seus dados\" → arquivamento completo.");
		}

		List<ExperiencePreview> experiences = new ArrayList<>();
		for (Map<String, String> row : rows(csvs.get("Positions.csv"))) {
			String title = row.get("position title");
			String company = row.get("company name");
			if (!StringUtils.hasText(title) && !StringUtils.hasText(company)) {
				continue;
			}
			String start = parseDate(row.get("started on"));
			String end = parseDate(row.get("finished on"));
			experiences.add(new ExperiencePreview(orNull(title), orNull(company), orNull(row.get("location")),
				orNull(row.get("description")), start, end, end == null));
		}

		List<EducationPreview> education = new ArrayList<>();
		for (Map<String, String> row : rows(csvs.getOrDefault("Education.csv", ""))) {
			String school = row.get("school name");
			if (!StringUtils.hasText(school)) {
				continue;
			}
			String start = parseDate(row.get("start date"));
			String end = parseDate(row.get("end date"));
			education.add(new EducationPreview(school, orNull(row.get("degree")), orNull(row.get("field of study")),
				orNull(row.get("notes")), start, end, end == null));
		}

		List<CertificationPreview> certifications = new ArrayList<>();
		for (Map<String, String> row : rows(csvs.getOrDefault("Certifications.csv", ""))) {
			String name = row.get("name");
			if (!StringUtils.hasText(name)) {
				continue;
			}
			certifications.add(new CertificationPreview(name, orNull(row.get("authority")), null,
				parseDate(row.get("time period")), null, orNull(row.get("url"))));
		}

		List<ProjectPreview> projects = new ArrayList<>();
		for (Map<String, String> row : rows(csvs.getOrDefault("Projects.csv", ""))) {
			String title = row.get("title");
			if (!StringUtils.hasText(title)) {
				continue;
			}
			projects.add(new ProjectPreview(title, orNull(row.get("description")), orNull(row.get("url")),
				parseDate(row.get("started on")), parseDate(row.get("finished on"))));
		}

		// Publicações do LinkedIn têm casa nos PROJETOS do perfil (título, descrição, link, data).
		for (Map<String, String> row : rows(csvs.getOrDefault("Publications.csv", ""))) {
			String title = row.get("title");
			if (!StringUtils.hasText(title)) {
				continue;
			}
			projects.add(new ProjectPreview(title, orNull(row.get("description")), orNull(row.get("url")),
				parseDate(row.get("published on")), null));
		}

		// Profile.csv: só os campos que o perfil do Énóis tem onde guardar.
		ProfilePreview profile = null;
		for (Map<String, String> row : rows(csvs.getOrDefault("Profile.csv", ""))) {
			String headline = orNull(row.get("headline"));
			String summary = orNull(row.get("summary"));
			String location = orNull(row.get("geo location")) != null ? orNull(row.get("geo location"))
				: orNull(row.get("location"));
			String industry = orNull(row.get("industry"));
			String first = orNull(row.get("first name"));
			String last = orNull(row.get("last name"));
			String name = (first == null && last == null) ? null
				: ((first == null ? "" : first) + " " + (last == null ? "" : last)).trim();
			if (headline != null || summary != null || location != null || industry != null || name != null) {
				profile = new ProfilePreview(name, headline, summary, location, industry);
			}
			break;
		}

		// Skills.csv: a coluna é "name" no export atual (o antigo usava "skill name").
		List<String> skills = new ArrayList<>();
		for (Map<String, String> row : rows(csvs.getOrDefault("Skills.csv", ""))) {
			String skill = orNull(row.get("name")) != null ? orNull(row.get("name")) : orNull(row.get("skill name"));
			if (skill != null && !skills.contains(skill)) {
				skills.add(skill);
			}
		}

		// O que veio mas não tem estrutura no Énóis: contamos para a pessoa saber (nada é perdido
		// em silêncio, e conexões/mensagens NÃO entram — são dados de terceiros, LGPD).
		Map<String, Integer> semEstrutura = new LinkedHashMap<>();
		registrarSemEstrutura(semEstrutura, "Idiomas", rows(csvs.getOrDefault("Languages.csv", "")));
		registrarSemEstrutura(semEstrutura, "Prêmios", rows(csvs.getOrDefault("Honors & Awards.csv", "")));
		registrarSemEstrutura(semEstrutura, "Voluntariado", rows(csvs.getOrDefault("Volunteering.csv", "")));
		registrarSemEstrutura(semEstrutura, "Cursos", rows(csvs.getOrDefault("Courses.csv", "")));

		int found = experiences.size() + education.size() + certifications.size() + projects.size()
			+ skills.size() + (profile != null ? 1 : 0);
		return new LinkedinImportPreview(experiences, education, certifications, projects, profile, skills,
			semEstrutura, found);
	}

	private void registrarSemEstrutura(Map<String, Integer> alvo, String rotulo, List<Map<String, String>> linhas) {
		int comConteudo = 0;
		for (Map<String, String> linha : linhas) {
			if (linha.values().stream().anyMatch(StringUtils::hasText)) {
				comConteudo++;
			}
		}
		if (comConteudo > 0) {
			alvo.put(rotulo, comConteudo);
		}
	}

	// ===== CSV (com aspas e vírgula escapada) =====

	private List<Map<String, String>> rows(String csv) {
		List<Map<String, String>> rows = new ArrayList<>();
		if (!StringUtils.hasText(csv)) {
			return rows;
		}
		List<List<String>> records = new ArrayList<>();
		for (String line : csv.split("R?\\n", -1)) {
			if (line.isBlank()) {
				continue;
			}
			records.add(parseCsvLine(line));
		}
		if (records.isEmpty()) {
			return rows;
		}
		List<String> header = records.get(0);
		for (int i = 1; i < records.size(); i++) {
			Map<String, String> row = new LinkedHashMap<>();
			List<String> record = records.get(i);
			for (int c = 0; c < header.size(); c++) {
				row.put(header.get(c).trim().toLowerCase(), c < record.size() ? record.get(c).trim() : "");
			}
			rows.add(row);
		}
		return rows;
	}

	/** Uma linha de CSV com aspas ("" = aspas literal) e vírgulas dentro de campos. */
	private List<String> parseCsvLine(String line) {
		List<String> fields = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		boolean inQuotes = false;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (inQuotes) {
				if (c == '"') {
					if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
						current.append('"');
						i++;
					} else {
						inQuotes = false;
					}
				} else {
					current.append(c);
				}
			} else if (c == '"') {
				inQuotes = true;
			} else if (c == ',') {
				fields.add(current.toString());
				current.setLength(0);
			} else {
				current.append(c);
			}
		}
		fields.add(current.toString());
		return fields;
	}

	private String parseDate(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		String value = raw.trim();
		// "2020-01" já é o formato do app.
		if (value.matches("\\d{4}-\\d{2}")) {
			return value;
		}
		// "Jan 2020" / "jan de 2020" / "janeiro de 2020"
		var matcher = java.util.regex.Pattern
			.compile("(?i)^(\\w{3,12})\\.?\\s*(?:de\\s*)?(\\d{4})$").matcher(value);
		if (matcher.matches()) {
			int month = monthNumber(matcher.group(1));
			if (month > 0) {
				return "%s-%02d".formatted(matcher.group(2), month);
			}
		}
		return null;
	}

	private int monthNumber(String name) {
		String key = name.toLowerCase().replaceAll("[^a-zà-ú]", "");
		Map<String, Integer> months = Map.ofEntries(
			Map.entry("jan", 1), Map.entry("janeiro", 1), Map.entry("january", 1),
			Map.entry("fev", 2), Map.entry("fevereiro", 2), Map.entry("february", 2),
			Map.entry("mar", 3), Map.entry("marco", 3), Map.entry("março", 3), Map.entry("march", 3),
			Map.entry("abr", 4), Map.entry("abril", 4), Map.entry("april", 4),
			Map.entry("mai", 5), Map.entry("maio", 5), Map.entry("may", 5),
			Map.entry("jun", 6), Map.entry("junho", 6), Map.entry("june", 6),
			Map.entry("jul", 7), Map.entry("julho", 7), Map.entry("july", 7),
			Map.entry("ago", 8), Map.entry("agosto", 8), Map.entry("august", 8), Map.entry("aug", 8),
			Map.entry("set", 9), Map.entry("setembro", 9), Map.entry("september", 9), Map.entry("sep", 9),
			Map.entry("out", 10), Map.entry("outubro", 10), Map.entry("october", 10), Map.entry("oct", 10),
			Map.entry("nov", 11), Map.entry("novembro", 11), Map.entry("november", 11),
			Map.entry("dez", 12), Map.entry("dezembro", 12), Map.entry("december", 12), Map.entry("dec", 12));
		return months.getOrDefault(key, 0);
	}

	private String stripBom(String content) {
		return content.startsWith("﻿") ? content.substring(1) : content;
	}

	private String orNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}
}
