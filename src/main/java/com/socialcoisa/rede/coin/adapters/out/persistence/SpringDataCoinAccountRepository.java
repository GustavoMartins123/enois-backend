package com.socialcoisa.rede.coin.adapters.out.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCoinAccountRepository extends JpaRepository<CoinAccountJpaEntity, String> {

	@org.springframework.data.jpa.repository.Modifying
	@org.springframework.data.jpa.repository.Query("UPDATE CoinAccountJpaEntity a SET a.balance = :newBalance, " +
		"a.version = a.version + 1 WHERE a.accountId = :id AND a.version = :expectedVersion")
	int updateBalanceIfVersion(@org.springframework.data.repository.query.Param("id") String id,
		@org.springframework.data.repository.query.Param("newBalance") long newBalance,
		@org.springframework.data.repository.query.Param("expectedVersion") long expectedVersion,
		@org.springframework.data.repository.query.Param("nextVersion") long nextVersion);

}
