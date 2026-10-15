package com.socialcoisa.rede.communities.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.communities.domain.Community;
import com.socialcoisa.rede.communities.domain.CommunityMember;
import com.socialcoisa.rede.communities.domain.MemberRole;

public interface CommunityRepository {

	void save(Community community);

	Optional<Community> findById(String id);

	List<Community> findAll(String topic, Instant before, int limit);

	/** Comunidades das quais o usuario participa (menu lateral). */
	List<Community> findByMember(String userId, int limit);

	void saveMember(CommunityMember member);

	/** Muda o papel de um membro (transferencia de administracao). */
	void updateMemberRole(String communityId, String userId, MemberRole role);

	/** Remove todos os vinculos de membros (ao excluir a comunidade, evita referencia orfa). */
	void removeAllMembers(String communityId);

	void removeMember(String communityId, String userId);

	boolean isMember(String communityId, String userId);

	int countMembers(String communityId);

	List<CommunityMember> findMembersByCommunity(String communityId);

	void delete(String communityId);
}
