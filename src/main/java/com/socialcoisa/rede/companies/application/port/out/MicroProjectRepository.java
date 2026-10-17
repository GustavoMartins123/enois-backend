package com.socialcoisa.rede.companies.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.companies.domain.MicroProject;
import com.socialcoisa.rede.companies.domain.MicroProjectProposal;
import com.socialcoisa.rede.companies.domain.ProposalStatus;

public interface MicroProjectRepository {

	MicroProject save(MicroProject project);

	Optional<MicroProject> findById(String id);

	/** Vitrine: projetos abertos, mais recentes primeiro. */
	List<MicroProject> findOpen(int limit);

	/** Todos os projetos de uma empresa (painel e pagina publica). */
	List<MicroProject> findByCompany(String companyId, int limit);

	MicroProjectProposal saveProposal(MicroProjectProposal proposal);

	Optional<MicroProjectProposal> findProposalById(String id);

	boolean existsProposalByUser(String projectId, String userId);

	List<MicroProjectProposal> proposalsOfProject(String projectId);

	List<MicroProjectProposal> proposalsOfUser(String userId, int limit);
}
