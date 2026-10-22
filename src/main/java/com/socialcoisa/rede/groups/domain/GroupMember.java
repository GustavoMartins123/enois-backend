package com.socialcoisa.rede.groups.domain;

import java.time.Instant;

/**
 * A associação de uma pessoa a um grupo, com o papel dela.
 *
 * Sem id próprio: a identidade é o par (grupo, pessoa), e é ele que o banco torna único. Não existe
 * associação duplicada — entrar duas vezes não é um estado possível.
 */
public class GroupMember {

	private final String groupId;
	private final String userId;
	private final GroupRole role;
	private final Instant joinedAt;

	private GroupMember(String groupId, String userId, GroupRole role, Instant joinedAt) {
		this.groupId = groupId;
		this.userId = userId;
		this.role = role;
		this.joinedAt = joinedAt;
	}

	public static GroupMember join(String groupId, String userId, GroupRole role, Instant now) {
		return new GroupMember(groupId, userId, role, now);
	}

	public static GroupMember restore(String groupId, String userId, GroupRole role, Instant joinedAt) {
		return new GroupMember(groupId, userId, role, joinedAt);
	}

	public String groupId() {
		return groupId;
	}

	public String userId() {
		return userId;
	}

	public GroupRole role() {
		return role;
	}

	public Instant joinedAt() {
		return joinedAt;
	}

	/** Troca o papel (promover, rebaixar) preservando a data de entrada. */
	public GroupMember withRole(GroupRole newRole) {
		return new GroupMember(groupId, userId, newRole, joinedAt);
	}
}
