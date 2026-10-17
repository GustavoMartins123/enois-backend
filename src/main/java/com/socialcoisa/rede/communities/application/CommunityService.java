package com.socialcoisa.rede.communities.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.socialcoisa.rede.communities.application.port.out.CommunityRepository;
import com.socialcoisa.rede.communities.domain.Community;
import com.socialcoisa.rede.communities.domain.CommunityMember;
import com.socialcoisa.rede.communities.domain.MemberRole;
import com.socialcoisa.rede.shared.web.ApiException;

@Service
public class CommunityService {

	private final CommunityRepository repository;
	private final Clock clock;

	public CommunityService(CommunityRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional
	public Community create(String name, String description, String topic, String cover, String avatar, String userId) {
		String id = "community_" + UUID.randomUUID().toString().replace("-", "");
		Community community = Community.create(id, name, description, topic, cover, avatar, userId, clock.instant());
		repository.save(community);
		repository.saveMember(CommunityMember.join(id, userId, MemberRole.ADMIN, clock.instant()));
		return community;
	}

	@Transactional
	public Community update(String communityId, String name, String description, String topic, String cover,
			String avatar, String bannerHtml, String userId) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!community.createdBy().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "So o criador pode atualizar a comunidade.");
		}
		String finalName = name != null ? name : community.name();
		String finalDesc = description != null ? description : community.description();
		String finalTopic = topic != null ? topic : community.topic();
		String finalCover = cover != null ? cover : community.cover();
		String finalAvatar = avatar != null ? avatar : community.avatar();
		// Capa em HTML: mesmo sanitizador da capa do perfil. Nulo = campo ausente (mantem); vazio =
		// limpa (volta para a foto); com texto = sanitiza e substitui.
		String finalBannerHtml = bannerHtml == null ? null
			: (org.springframework.util.StringUtils.hasText(bannerHtml)
				? com.socialcoisa.rede.shared.html.SafeHtml.requireProfileHtml(bannerHtml)
				: "");
		Community updated = community.update(finalName, finalDesc, finalTopic, finalCover, finalAvatar,
			finalBannerHtml, clock.instant());
		repository.save(updated);
		return updated;
	}

	@Transactional
	public CommunityMember join(String communityId, String userId) {
		if (repository.isMember(communityId, userId)) {
			throw new ApiException(409, "ALREADY_MEMBER", "Voce ja e membro desta comunidade.");
		}
		CommunityMember member = CommunityMember.join(communityId, userId, MemberRole.MEMBER, clock.instant());
		repository.saveMember(member);
		return member;
	}

	/**
	 * Sair da comunidade. O dono NAO pode simplesmente sair enquanto houver outros membros: a
	 * propriedade e o que autoriza editar/excluir, entao ele precisa transferir antes (409
	 * OWNERSHIP_TRANSFER_REQUIRED). Antes, a saida do dono promovia alguem aleatorio a ADMIN, mas o
	 * novo admin nao conseguia editar (a checagem usa o dono) e quem saiu continuava no controle —
	 * estado inconsistente e inseguro. Ser o ultimo membro apaga a comunidade.
	 */
	@Transactional
	public void leave(String communityId, String userId) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!repository.isMember(communityId, userId)) {
			throw new ApiException(404, "NOT_MEMBER", "Voce nao e membro desta comunidade.");
		}
		if (community.createdBy().equals(userId) && repository.countMembers(communityId) > 1) {
			throw new ApiException(409, "OWNERSHIP_TRANSFER_REQUIRED",
				"Transfira a administracao para outro membro antes de sair.");
		}
		repository.removeMember(communityId, userId);
		if (repository.countMembers(communityId) == 0) {
			repository.removeAllMembers(communityId);
			repository.delete(communityId);
		}
	}

	/**
	 * Transfere a propriedade para outro membro. So o dono atual transfere; o novo dono vira ADMIN e
	 * o antigo passa a membro comum (se continuar na comunidade).
	 */
	@Transactional
	public Community transferOwnership(String communityId, String currentUserId, String newOwnerId) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!community.createdBy().equals(currentUserId)) {
			throw new ApiException(403, "FORBIDDEN", "So o dono pode transferir a administracao.");
		}
		if (newOwnerId == null || newOwnerId.isBlank()) {
			throw new ApiException(400, "INVALID_NEW_OWNER", "Escolha quem vai administrar a comunidade.");
		}
		if (newOwnerId.equals(currentUserId)) {
			throw new ApiException(400, "INVALID_NEW_OWNER", "Voce ja e o administrador desta comunidade.");
		}
		if (!repository.isMember(communityId, newOwnerId)) {
			throw new ApiException(400, "NOT_MEMBER", "O novo administrador precisa ser membro da comunidade.");
		}
		Community transferred = community.transferOwnership(newOwnerId, clock.instant());
		repository.save(transferred);
		repository.updateMemberRole(communityId, newOwnerId, MemberRole.ADMIN);
		if (repository.isMember(communityId, currentUserId)) {
			repository.updateMemberRole(communityId, currentUserId, MemberRole.MEMBER);
		}
		return transferred;
	}

	/** Exclui a comunidade. So o dono; remove os vinculos de membros para nao deixar referencia orfa. */
	@Transactional
	public void deleteCommunity(String communityId, String userId) {
		Community community = repository.findById(communityId)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
		if (!community.createdBy().equals(userId)) {
			throw new ApiException(403, "FORBIDDEN", "So o dono pode excluir a comunidade.");
		}
		repository.removeAllMembers(communityId);
		repository.delete(communityId);
	}

	/** Comunidades das quais o usuario participa (menu lateral). */
	public List<Community> listMine(String userId, int limit) {
		return repository.findByMember(userId, limit);
	}

	/** Favorita (salva) uma comunidade para acesso rapido. Nao exige participar dela. */
	@Transactional
	public void favorite(String communityId, String userId) {
		getById(communityId);
		repository.addFavorite(communityId, userId, clock.instant());
	}

	@Transactional
	public void unfavorite(String communityId, String userId) {
		repository.removeFavorite(communityId, userId);
	}

	public List<Community> listFavorites(String userId, int limit) {
		return repository.findFavorites(userId, limit);
	}

	public List<Community> list(String topic, java.time.Instant before, int limit) {
		return repository.findAll(topic, before, limit);
	}

	public Community getById(String id) {
		return repository.findById(id)
			.orElseThrow(() -> new ApiException(404, "COMMUNITY_NOT_FOUND", "Comunidade nao encontrada."));
	}

	public boolean isMember(String communityId, String userId) {
		return repository.isMember(communityId, userId);
	}

	public int countMembers(String communityId) {
		return repository.countMembers(communityId);
	}

	public List<CommunityMember> findMembers(String communityId) {
		return repository.findMembersByCommunity(communityId);
	}

}
