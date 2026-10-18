package com.socialcoisa.rede.companies.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.socialcoisa.rede.companies.application.port.out.CompanyRepository;
import com.socialcoisa.rede.companies.domain.Company;
import com.socialcoisa.rede.companies.domain.CompanyMember;
import com.socialcoisa.rede.companies.domain.CompanyRole;
import com.socialcoisa.rede.shared.web.ApiException;

/**
 * Paginas empresariais. Autorizacao por papel acontece AQUI (nunca no front); dono e unico e a
 * transferencia e explicita. O slug nasce do nome e e unico — colisao ganha sufixo numerico.
 */
@Service
public class CompanyService {

	private final CompanyRepository companies;
	private final Clock clock;

	public CompanyService(CompanyRepository companies, Clock clock) {
		this.companies = companies;
		this.clock = clock;
	}

	@Transactional
	public Company create(String userId, String name, String description, String sector, String size, String location,
			String website) {
		if (!StringUtils.hasText(name) || name.trim().length() < 2) {
			throw new ApiException(400, "INVALID_COMPANY_NAME", "Nome da empresa e obrigatorio.");
		}
		String slug = uniqueSlug(slugFrom(name));
		Company company = Company.create("comp_" + UUID.randomUUID().toString().replace("-", ""), slug,
			name.trim(), StringUtils.hasText(description) ? description.trim() : null,
			StringUtils.hasText(sector) ? sector.trim() : null,
			StringUtils.hasText(size) ? size.trim() : null,
			StringUtils.hasText(location) ? location.trim() : null,
			StringUtils.hasText(website) ? website.trim() : null,
			userId, clock.instant());
		Company saved = companies.save(company);
		companies.saveMember(CompanyMember.create("cmemb_" + UUID.randomUUID().toString().replace("-", ""),
			saved.id(), userId, CompanyRole.OWNER, clock.instant()));
		return saved;
	}

	@Transactional
	public Company update(String companyId, String userId, String name, String description, String sector, String size,
			String location, String website, String logo, String cover) {
		Company company = company(companyId);
		requireRole(company, userId, CompanyRole.EDITOR);
		Company updated = company.update(
			StringUtils.hasText(name) ? name.trim() : null,
			StringUtils.hasText(description) ? description.trim() : description,
			sector, size, location,
			StringUtils.hasText(website) ? website.trim() : website,
			StringUtils.hasText(logo) ? logo.trim() : logo,
			StringUtils.hasText(cover) ? cover.trim() : cover,
			clock.instant());
		return companies.save(updated);
	}

	@Transactional(readOnly = true)
	public Company bySlug(String slug) {
		return companies.findBySlug(slug)
			.orElseThrow(() -> new ApiException(404, "COMPANY_NOT_FOUND", "Pagina empresarial nao encontrada."));
	}

	@Transactional(readOnly = true)
	public Company company(String companyId) {
		return companies.findById(companyId)
			.orElseThrow(() -> new ApiException(404, "COMPANY_NOT_FOUND", "Pagina empresarial nao encontrada."));
	}

	@Transactional(readOnly = true)
	public List<Company> ownedBy(String userId) {
		return companies.findByOwner(userId, 50);
	}

	@Transactional(readOnly = true)
	public List<Company> listAll(int limit) {
		return companies.listAll(limit);
	}

	@Transactional(readOnly = true)
	public List<CompanyMember> members(String companyId) {
		return companies.membersOf(companyId);
	}

	@Transactional(readOnly = true)
	public CompanyRole roleOf(String companyId, String userId) {
		return companies.findMember(companyId, userId).map(CompanyMember::role).orElse(null);
	}

	@Transactional
	public void requireRole(Company company, String userId, CompanyRole minimum) {
		CompanyRole role = roleOf(company.id(), userId);
		if (role == null) {
			throw new ApiException(403, "NOT_A_MEMBER", "Voce nao faz parte desta pagina empresarial.");
		}
		if (company.ownedBy(userId)) {
			return; // dono faz tudo
		}
		boolean enough = switch (minimum) {
			case OWNER -> false;
			case ADMIN -> role == CompanyRole.ADMIN;
			case EDITOR -> role == CompanyRole.ADMIN || role == CompanyRole.EDITOR;
			case RECRUITER -> role == CompanyRole.ADMIN || role == CompanyRole.EDITOR
				|| role == CompanyRole.RECRUITER;
		};
		if (!enough) {
			throw new ApiException(403, "FORBIDDEN", "Seu papel nao permite esta acao.");
		}
	}

	@Transactional
	public CompanyMember addMember(String companyId, String userId, CompanyRole role) {
		Company company = company(companyId);
		if (companies.findMember(companyId, userId).isPresent()) {
			throw new ApiException(409, "ALREADY_MEMBER", "Esta pessoa ja faz parte da pagina.");
		}
		return companies.saveMember(CompanyMember.create(
			"cmemb_" + UUID.randomUUID().toString().replace("-", ""), company.id(), userId, role, clock.instant()));
	}

	private String slugFrom(String name) {
		return name.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", "-")
			.replaceAll("(^-|-$)", "");
	}

	private String uniqueSlug(String base) {
		String slug = StringUtils.hasText(base) ? base : "empresa";
		String candidate = slug;
		int n = 2;
		while (companies.existsBySlug(candidate)) {
			candidate = slug + "-" + n;
			n++;
		}
		return candidate;
	}
}
