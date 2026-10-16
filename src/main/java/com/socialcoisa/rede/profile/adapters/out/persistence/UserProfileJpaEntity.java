package com.socialcoisa.rede.profile.adapters.out.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.socialcoisa.rede.profile.domain.UserProfile;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "users", indexes = @Index(name = "idx_users_name", columnList = "name"))
class UserProfileJpaEntity {

	@Id
	private String id;

	@Column(nullable = false, unique = true, length = 80)
	private String username;

	@Column(nullable = false, length = 160)
	private String name;

	@Column(length = 180)
	private String headline;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(length = 160)
	private String company;

	@Column(length = 160)
	private String location;

	@Column(length = 500)
	private String avatar;

	@Column(length = 500)
	private String banner;

	@Column(length = 1024)
	private String bio;

	/** Bio em HTML/CSS, ja sanitizada (ver SafeHtml). */
	@Column(name = "bio_html", columnDefinition = "longtext")
	private String bioHtml;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_skills", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "skill", nullable = false, length = 800)
	private List<String> skills = new ArrayList<>();

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_goals", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "goal", nullable = false, length = 160)
	private List<String> goals = new ArrayList<>();

	@Column(length = 160)
	private String availability;

	@Column(length = 500)
	private String portfolio;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "user_external_links", joinColumns = @JoinColumn(name = "user_id"))
	@Column(name = "external_link", nullable = false, length = 500)
	private List<String> externalLinks = new ArrayList<>();

	@Column(name = "open_to_work", nullable = false)
	private boolean openToWork;

	@Column(name = "is_mentor", nullable = false)
	private boolean mentor;

	@Column(nullable = false)
	private int reputation;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "terms_accepted_at")
	private Instant termsAcceptedAt;

	@Column(name = "email_verified_at")
	private Instant emailVerifiedAt;

	@Column(name = "totp_enabled_at")
	private Instant totpEnabledAt;

	@Column(name = "email_otp_enabled_at")
	private Instant emailOtpEnabledAt;

	@Column(name = "federated_second_factor_at")
	private Instant federatedSecondFactorAt;

	protected UserProfileJpaEntity() {
	}

	static UserProfileJpaEntity fromDomain(UserProfile profile) {
		UserProfileJpaEntity entity = new UserProfileJpaEntity();
		entity.id = profile.id();
		entity.username = profile.username();
		entity.name = profile.name();
		entity.headline = profile.headline();
		entity.email = profile.email();
		entity.company = profile.company();
		entity.location = profile.location();
		entity.avatar = profile.avatar();
		entity.banner = profile.banner();
		entity.bio = profile.bio();
		entity.bioHtml = profile.bioHtml();
		entity.skills = new ArrayList<>(profile.skills());
		entity.goals = new ArrayList<>(profile.goals());
		entity.availability = profile.availability();
		entity.portfolio = profile.portfolio();
		entity.externalLinks = new ArrayList<>(profile.externalLinks());
		entity.openToWork = profile.openToWork();
		entity.mentor = profile.mentor();
		entity.reputation = profile.reputation();
		entity.createdAt = profile.createdAt();
		entity.updatedAt = profile.updatedAt();
		entity.termsAcceptedAt = profile.termsAcceptedAt();
		entity.emailVerifiedAt = profile.emailVerifiedAt();
		entity.totpEnabledAt = profile.totpEnabledAt();
		entity.emailOtpEnabledAt = profile.emailOtpEnabledAt();
		entity.federatedSecondFactorAt = profile.federatedSecondFactorAt();
		return entity;
	}

	UserProfile toDomain() {
		UserProfile profile = UserProfile.restore(id, username, email, name, headline, company, location, avatar, banner,
			bio, bioHtml, skills, goals, availability, portfolio, externalLinks, openToWork, mentor, createdAt,
			updatedAt);
		profile.setCounts(profile.connectionsCount(), profile.followersCount(), reputation);
		profile.acceptTerms(termsAcceptedAt);
		if (emailVerifiedAt != null) {
			profile.markEmailVerified(emailVerifiedAt);
		}
		if (totpEnabledAt != null) {
			profile.enableTotp(totpEnabledAt);
		}
		if (emailOtpEnabledAt != null) {
			profile.enableEmailOtp(emailOtpEnabledAt);
		}
		if (federatedSecondFactorAt != null) {
			profile.enableFederatedSecondFactor(federatedSecondFactorAt);
		}
		return profile;
	}
}
