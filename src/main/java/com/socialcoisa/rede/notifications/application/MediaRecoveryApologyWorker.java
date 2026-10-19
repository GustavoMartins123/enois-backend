package com.socialcoisa.rede.notifications.application;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.socialcoisa.rede.coin.application.CoinService;
import com.socialcoisa.rede.media.application.port.out.MediaUploadSigner;
import com.socialcoisa.rede.notifications.application.port.out.SystemAnnouncementRepository;
import com.socialcoisa.rede.profile.application.port.out.UserProfileRepository;

/**
 * Pedido de desculpas do incidente de armazenamento (ImageKit comprometido em 19/10/2026).
 *
 * <p>A condição de disparo é a RECUPERAÇÃO de verdade: o upload assinado do S3 só existe quando
 * o storage novo está configurado (bean {@link MediaUploadSigner}); a leitura das mídias antigas
 * continua pelos endereços já gravados. Quando o S3 entra, este worker manda, UMA vez:</p>
 * <ul>
 *   <li>uma notificação para cada usuário (que também vira push pelo evento de notificação);</li>
 *   <li>100 faíscas de crédito, com referência idempotente por conta.</li>
 * </ul>
 *
 * <p>O comunicado é marcado em {@code system_announcements}: reprocessar (restart, nova rodada)
 * não duplica o envio. Enquanto o S3 não estiver configurado, o worker não faz NADA — é o sinal
 * de que as fotos ainda não voltaram.</p>
 */
@Component
public class MediaRecoveryApologyWorker {

	private static final Logger LOG = LoggerFactory.getLogger(MediaRecoveryApologyWorker.class);

	public static final String ANNOUNCEMENT_KEY = "desculpa-armazenamento-2026-10";
	public static final String TITULO = "Pedimos desculpas pelo ocorrido";
	public static final String CORPO = "Tivemos um problema com variáveis de ambiente devido à alta demanda. "
		+ "Como forma de agradecer a paciência, estamos enviando 100 faíscas para cada usuário. "
		+ "Pedimos perdão pelo inconveniente e reforçamos nosso compromisso de ter uma rede "
		+ "transparente e livre para todos.";
	public static final String HREF = "/carteira";
	public static final String CTA = "Ver carteira";
	public static final long BONUS = CoinService.FAIS(100L);

	private static final int PAGE = 200;

	private final Optional<MediaUploadSigner> uploadSigner;
	private final UserProfileRepository profiles;
	private final CoinService coins;
	private final NotificationService notifications;
	private final SystemAnnouncementRepository announcements;
	private final Clock clock;

	MediaRecoveryApologyWorker(Optional<MediaUploadSigner> uploadSigner, UserProfileRepository profiles,
			CoinService coins, NotificationService notifications, SystemAnnouncementRepository announcements,
			Clock clock) {
		this.uploadSigner = uploadSigner;
		this.profiles = profiles;
		this.coins = coins;
		this.notifications = notifications;
		this.announcements = announcements;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "${rede.communications.recovery-delay-ms:1800000}",
		initialDelayString = "${rede.communications.recovery-initial-delay-ms:120000}")
	public void announceIfStorageRecovered() {
		if (announcements.isSent(ANNOUNCEMENT_KEY)) {
			return;
		}
		if (uploadSigner.isEmpty()) {
			// S3 ainda não configurado: as fotos novas ainda não voltaram — não é o momento.
			return;
		}
		int announced = 0;
		int offset = 0;
		while (true) {
			List<String> ids = profiles.findAllIds(offset, PAGE);
			if (ids.isEmpty()) {
				break;
			}
			for (String userId : ids) {
				try {
					String ref = ANNOUNCEMENT_KEY + ":" + userId;
					if (!coins.hasReward(ref)) {
						coins.reward(userId, BONUS, ref, "Desculpas pelo problema no armazenamento de mídia");
					}
					notifications.create(userId, "SISTEMA", "sistema", TITULO, CORPO, HREF, CTA);
					announced++;
				}
				catch (RuntimeException perUser) {
					// Uma conta problemática não pode impedir o comunicado das demais.
					LOG.warn("Falha ao comunicar o usuário {} sobre a recuperação do armazenamento: {}",
						userId, perUser.getMessage());
				}
			}
			if (ids.size() < PAGE) {
				break;
			}
			offset += PAGE;
		}
		announcements.markSent(ANNOUNCEMENT_KEY, clock.instant());
		LOG.info("Comunicado de desculpas enviado: {} usuários, {} faíscas cada.", announced,
			BONUS / CoinService.SCALE);
	}
}
