package com.socialcoisa.rede.groups.domain;

import java.util.List;

/**
 * Quem pode o quê dentro de um grupo: uma tabela só, papel por capacidade.
 *
 * Papel nulo é visitante, e visitante alcança apenas a apresentação pública do grupo (nome,
 * descrição, identidade visual, modo de ingresso) — mais denunciar essa apresentação. Publicações,
 * comentários, reações, anexos, lista de membros, tópicos internos, solicitações de ingresso e
 * relatórios de moderação não existem para ele, nem que ele saiba o id de cada um.
 *
 * O administrador local administra o grupo; não administra a plataforma. Por isso o que o plano
 * lista como proibição dele (apagar prova de denúncia, desligar o AutoMod, alterar auditoria,
 * conceder privilégio global) está aqui como capacidade que NINGUÉM do grupo tem.
 */
public final class GroupPermissions {

	private GroupPermissions() {
	}

	public static boolean can(GroupRole role, GroupCapability capability) {
		if (role == null) {
			return capability == GroupCapability.REPORT_GROUP;
		}
		return switch (capability) {
			case VIEW_PRIVATE_CONTENT, PUBLISH, INTERACT, REPORT_CONTENT, REPORT_GROUP -> true;
			case MODERATE_CONTENT, PIN_CONTENT -> role == GroupRole.OWNER || role == GroupRole.ADMIN
					|| role == GroupRole.MODERATOR;
			case MANAGE_TOPICS, MANAGE_MEMBERS, VIEW_JOIN_REQUESTS, DECIDE_JOIN_REQUESTS, EDIT_GROUP,
					CONFIGURE_JOIN -> role == GroupRole.OWNER || role == GroupRole.ADMIN;
			case MANAGE_ROLES, TRANSFER_OWNERSHIP, CLOSE_GROUP -> role == GroupRole.OWNER;
			case DELETE_REPORT_EVIDENCE, DISABLE_AUTOMOD, CHANGE_AUDIT_RECORDS, GRANT_GLOBAL_PRIVILEGES -> false;
		};
	}

	/**
	 * Tirar a propriedade de alguém só é permitido se sobrar autoridade: o último dono não é
	 * rebaixado nem removido. Sem esta trava, uma remoção — ou uma sessão que caiu no meio da
	 * operação — deixaria o grupo sem quem possa editá-lo, que é o estado inconsistente do qual não
	 * se sai sem intervenção no banco.
	 */
	public static boolean canDropOwnership(List<GroupMember> members, String userId) {
		return members.stream()
			.anyMatch(member -> member.role() == GroupRole.OWNER && !member.userId().equals(userId));
	}
}
