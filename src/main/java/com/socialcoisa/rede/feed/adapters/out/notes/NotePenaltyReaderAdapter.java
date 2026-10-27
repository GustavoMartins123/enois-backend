package com.socialcoisa.rede.feed.adapters.out.notes;

import org.springframework.stereotype.Component;

import com.socialcoisa.rede.feed.application.port.out.PostNoteRepository;
import com.socialcoisa.rede.feed.domain.PostNoteStatus;
import com.socialcoisa.rede.profile.application.port.out.NotePenaltyReader;

@Component
class NotePenaltyReaderAdapter implements NotePenaltyReader {

	private final PostNoteRepository notes;

	NotePenaltyReaderAdapter(PostNoteRepository notes) {
		this.notes = notes;
	}

	@Override
	public int countDeniedByUser(String userId) {
		return notes.countByAuthorAndStatus(userId, PostNoteStatus.DENIED);
	}
}