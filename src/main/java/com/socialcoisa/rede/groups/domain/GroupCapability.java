package com.socialcoisa.rede.groups.domain;

/**
 * Uma ação que alguém pode (ou não) fazer dentro de um grupo.
 *
 * A lista é a fonte da verdade da autorização: cada endpoint pergunta por uma CAPACIDADE em vez de
 * comparar papéis na mão, então a regra mora num lugar só e mudar a régua não exige caçar `if` pelo
 * módulo inteiro.
 *
 * As quatro últimas existem para serem NEGADAS a todo mundo — são as proibições que o plano escreve
 * com todas as letras (nem o dono apaga prova de denúncia, desliga o AutoMod, mexe na auditoria ou
 * se dá privilégio global). Tê-las aqui faz um endpoint que pergunte por elas receber "não", em vez
 * de ninguém ter pensado no assunto.
 */
public enum GroupCapability {
	/** Ver publicações, comentários, tópicos internos e a lista de membros. */
	VIEW_PRIVATE_CONTENT,
	/** Publicar nos tópicos permitidos. */
	PUBLISH,
	/** Comentar e reagir. */
	INTERACT,
	/** Denunciar conteúdo interno. */
	REPORT_CONTENT,
	/** Denunciar a apresentação pública do grupo — o visitante também pode. */
	REPORT_GROUP,
	/** Remover ou ocultar publicações e comentários. */
	MODERATE_CONTENT,
	/** Fixar e desafixar publicações. */
	PIN_CONTENT,
	/** Criar, editar, reordenar e arquivar tópicos. */
	MANAGE_TOPICS,
	/** Remover membros. */
	MANAGE_MEMBERS,
	/** Ver as solicitações de ingresso. */
	VIEW_JOIN_REQUESTS,
	/** Aprovar ou rejeitar solicitações de ingresso. */
	DECIDE_JOIN_REQUESTS,
	/** Editar nome, descrição, logo e cabeçalho. */
	EDIT_GROUP,
	/** Trocar o modo de ingresso. */
	CONFIGURE_JOIN,
	/** Nomear e remover administradores. */
	MANAGE_ROLES,
	/** Passar a propriedade do grupo para outra pessoa. */
	TRANSFER_OWNERSHIP,
	/** Encerrar o grupo. */
	CLOSE_GROUP,

	/** Apagar, esconder ou invalidar uma denúncia. Negado a todos: a denúncia é da plataforma. */
	DELETE_REPORT_EVIDENCE,
	/** Desligar o AutoMod. Negado a todos: a moderação automática não é opcional para o grupo. */
	DISABLE_AUTOMOD,
	/** Alterar registro de auditoria. Negado a todos. */
	CHANGE_AUDIT_RECORDS,
	/** Conceder a si próprio privilégio global. Negado a todos. */
	GRANT_GLOBAL_PRIVILEGES
}
