package com.socialcoisa.rede.importresume;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.socialcoisa.rede.importlinkedin.LinkedinImportService;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Importador de CURRICULO em arquivo (PDF ou texto). Terceira porta de entrada do retrato
 * profissional, ao lado do ZIP do LinkedIn e do texto colado do perfil: a pessoa sobe o
 * curriculo, o enois extrai o texto e DEVOLVE o retrato para revisao — nada e salvo
 * automaticamente.
 *
 * Reaproveita o {@link LinkedinImportService.LinkedinImportPreview} como formato de saida (e o
 * `parseTexto` como extrator de secoes): o curriculo vira texto puro (via PDFBox ou leitura
 * direta) e segue o mesmo caminho do "colar perfil". O front usa as mesmas telas de revisao
 * das outras duas origens.
 *
 * Formatos aceitos: PDF (ate 10 MB e 20 paginas) e .txt/.md. DOC/DOCX nao entram agora:
 * exigiriam o POI inteiro para um formato que os editores exportam em PDF num clique — o
 * erro orienta a converter.
 */
@Service
public class ResumeImportService {

	private static final int MAX_FILE_BYTES = 10 * 1024 * 1024;

	private static final int MAX_PAGES = 20;

	private static final int MAX_TXT_BYTES = 512 * 1024;

	private static final int MAX_TEXT_CHARS = 120_000;

	private final LinkedinImportService linkedinImportService;

	public ResumeImportService(LinkedinImportService linkedinImportService) {
		this.linkedinImportService = linkedinImportService;
	}

	/**
	 * Extrai o texto do curriculo e devolve o retrato para revisao (mesmo formato do import do
	 * LinkedIn). Conteudo ilegivel ou vazio e erro 400 com orientacao, nunca retrato vazio.
	 */
	public LinkedinImportService.LinkedinImportPreview parse(MultipartFile file) {
		return linkedinImportService.parseTexto(extractText(file));
	}

	/**
	 * So o texto puro do arquivo (para o prefill da inscricao, que cruza com os campos da
	 * vaga em vez de montar o retrato do perfil).
	 */
	public String extractText(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(400, "FILE_REQUIRED", "Envie o seu curriculo em PDF ou texto.");
		}
		if (file.getSize() > MAX_FILE_BYTES) {
			throw new ApiException(400, "FILE_TOO_LARGE", "O curriculo deve ter no maximo 10 MB.");
		}
		byte[] bytes;
		try {
			bytes = file.getBytes();
		}
		catch (IOException error) {
			throw new ApiException(400, "INVALID_FILE", "Nao foi possivel ler o arquivo. Tente de novo.");
		}
		String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
		String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
		String text;
		if (isPdf(bytes, name, contentType)) {
			text = textOfPdf(bytes);
		} else if (name.endsWith(".txt") || name.endsWith(".md") || contentType.startsWith("text/")) {
			text = textOfPlain(bytes, file.getOriginalFilename());
		} else {
			throw new ApiException(400, "UNSUPPORTED_FORMAT",
				"Formato nao suportado. Envie o curriculo em PDF ou texto. DOC/DOCX: exporte em PDF no seu editor.");
		}
		if (!StringUtils.hasText(text) || text.trim().length() < 30) {
			throw new ApiException(400, "UNREADABLE_RESUME",
				"Nao encontrei texto legivel nesse arquivo. Se for PDF escaneado (imagem), exporte de novo como PDF com texto.");
		}
		if (text.length() > MAX_TEXT_CHARS) {
			text = text.substring(0, MAX_TEXT_CHARS);
		}
		return text;
	}

	/** PDF pela assinatura magica `%PDF` (nao so pela extensao, que o usuario pode errar). */
	private boolean isPdf(byte[] bytes, String name, String contentType) {
		if (bytes.length > 5 && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F'
				&& bytes[4] == '-') {
			return true;
		}
		return name.endsWith(".pdf") || contentType.equals("application/pdf");
	}

	private String textOfPlain(byte[] bytes, String originalName) {
		if (bytes.length > MAX_TXT_BYTES) {
			throw new ApiException(400, "FILE_TOO_LARGE", "O arquivo de texto deve ter no maximo 512 KB.");
		}
		String text = new String(bytes, StandardCharsets.UTF_8);
		if (!StringUtils.hasText(text)) {
			throw new ApiException(400, "INVALID_FILE",
				"O arquivo " + (originalName == null ? "" : originalName) + " parece vazio ou nao e texto.");
		}
		return text;
	}

	/**
	 * Texto do PDF via PDFBox (so leitura). Protegido contra bomba de descompressao: limite de
	 * paginas e de tamanho de saida — curriculo real tem 1-3 paginas.
	 */
	private String textOfPdf(byte[] bytes) {
		try (PDDocument document = Loader.loadPDF(bytes)) {
			if (document.isEncrypted()) {
				throw new ApiException(400, "UNREADABLE_RESUME",
					"Esse PDF esta protegido por senha. Exporte uma copia sem senha para importar.");
			}
			if (document.getNumberOfPages() > MAX_PAGES) {
				throw new ApiException(400, "FILE_TOO_LARGE",
					"O curriculo deve ter no maximo " + MAX_PAGES + " paginas.");
			}
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			String text = stripper.getText(document);
			return text == null ? "" : text;
		}
		catch (ApiException apiException) {
			throw apiException;
		}
		catch (IOException error) {
			throw new ApiException(400, "INVALID_FILE", "Nao foi possivel ler o PDF. E um PDF valido?");
		}
	}

	// ===== PREFILL DA INSCRICAO =====
	//
	// A vaga define os campos do formulario (`JobApplicationField`: rotulo + tipo + obrigatorio).
	// O curriculo nao responde por ela — mas adianta o que da: o nome/e-mail vao para a mensagem,
	// os telefones/links viram resposta dos campos de LINK com rotulo compativel, e o que sobra
	// e devolvido como "nao preenchido" para o front destacar, nao como erro.

	/** Uma resposta sugerida, ou o motivo de um campo ter ficado sem sugestao. */
	public record FieldSuggestion(String fieldId, String label, String suggestedValue, String reason) {
	}

	public record ApplyPrefillView(String jobId, String jobTitle, String suggestedMessage,
			List<FieldSuggestion> fields, int filled, int total) {
	}

	/**
	 * Monta o prefill da inscricao: cruza os campos da vaga com o texto do curriculo. Nunca
	 * escreve candidatura — so sugere; a inscricao continua sendo o POST existente, com a pessoa
	 * revisando antes.
	 */
	public ApplyPrefillView prefill(String jobId, String jobTitle, List<JobField> fields, String resumeText) {
		List<FieldSuggestion> suggestions = new ArrayList<>();
		String text = resumeText == null ? "" : resumeText;
		Map<String, String> found = extractContact(text);
		String email = found.get("email");
		String phone = found.get("phone");
		String link = found.get("link");
		for (JobField field : fields) {
			String label = field.label() == null ? "" : field.label().toLowerCase();
			String value = null;
			String reason = null;
			if (field.type() == JobFieldType.LINK) {
				if (looksLike(label, "linkedin", "github", "portfolio", "portfólio", "site", "link", "url")) {
					value = link;
					reason = value == null ? "NO_LINK_FOUND" : null;
				} else if (looksLike(label, "telefone", "celular", "whatsapp", "contato", "fone")) {
					value = phone != null ? phone : email;
					reason = value == null ? "NO_CONTACT_FOUND" : null;
				} else if (looksLike(label, "email", "e-mail")) {
					value = email;
					reason = value == null ? "NO_CONTACT_FOUND" : null;
				} else {
					value = link != null ? link : email;
					reason = value == null ? "NO_CONTACT_FOUND" : null;
				}
			} else if (field.type() == JobFieldType.NUMBER
					&& looksLike(label, "ano", "experiencia", "experiência", "tempo")) {
				value = yearsOfExperience(text);
				reason = value == null ? "NO_YEARS_FOUND" : null;
			} else {
				reason = "MANUAL_FIELD";
			}
			suggestions.add(new FieldSuggestion(field.id(), field.label(), value, reason));
		}
		int filled = (int) suggestions.stream().filter(s -> StringUtils.hasText(s.suggestedValue())).count();
		return new ApplyPrefillView(jobId, jobTitle,
			email != null ? "Ola! Segue minha candidatura. Meu e-mail: " + email + "." : null,
			suggestions, filled, suggestions.size());
	}

	/** Campo da vaga no formato que o prefill precisa (rotulo + tipo + obrigatoriedade). */
	public record JobField(String id, String label, JobFieldType type, boolean required) {
	}

	public enum JobFieldType {
		TEXT, TEXTAREA, NUMBER, LINK
	}

	private boolean looksLike(String label, String... keys) {
		String normalized = java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "");
		for (String key : keys) {
			String normalizedKey = java.text.Normalizer.normalize(key, java.text.Normalizer.Form.NFD)
				.replaceAll("\\p{M}", "");
			if (normalized.contains(normalizedKey)) {
				return true;
			}
		}
		return false;
	}

	/** E-mail, telefone BR e primeiro link util (LinkedIn/GitHub/portfolio antes de rede social). */
	Map<String, String> extractContact(String text) {
		Map<String, String> found = new LinkedHashMap<>();
		var email = java.util.regex.Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+").matcher(text);
		if (email.find()) {
			found.put("email", email.group());
		}
		var phone = java.util.regex.Pattern
			.compile("(\\+?55\\s?)?(\\(?\\d{2}\\)?\\s?)?\\d{4,5}[-.\\s]?\\d{4}")
			.matcher(text);
		while (phone.find()) {
			String digits = phone.group().replaceAll("\\D", "");
			if (digits.length() >= 10 && digits.length() <= 13) {
				found.put("phone", phone.group().trim());
				break;
			}
		}
		var link = java.util.regex.Pattern.compile("https?://\\S+").matcher(text);
		String fallback = null;
		while (link.find()) {
			String url = link.group().replaceAll("[.,;\\)]+$", "");
			String lower = url.toLowerCase();
			if (lower.contains("linkedin.com/in") || lower.contains("github.com") || lower.contains("portfolio")
					|| lower.contains("behance") || lower.contains("medium.com")) {
				found.put("link", url);
				break;
			}
			if (fallback == null && !lower.contains("facebook.com") && !lower.contains("instagram.com")
					&& !lower.contains("twitter.com") && !lower.contains("x.com")) {
				fallback = url;
			}
		}
		if (!found.containsKey("link") && fallback != null) {
			found.put("link", fallback);
		}
		return found;
	}

	/**
	 * "X anos de experiencia" por extenso no texto ("5 anos de experiência", "3+ anos"). Heuristica
	 * de teto 50: numero solto maior que isso e ano, nao experiencia.
	 */
	String yearsOfExperience(String text) {
		var matcher = java.util.regex.Pattern
			.compile("(?i)(\\d{1,2})\\s*\\+?\\s*anos?(?:\\s+de)?\\s+experi[êe]ncia")
			.matcher(text);
		if (matcher.find()) {
			try {
				int years = Integer.parseInt(matcher.group(1));
				if (years >= 0 && years <= 50) {
					return String.valueOf(years);
				}
			}
			catch (NumberFormatException notANumber) {
				return null;
			}
		}
		return null;
	}
}
