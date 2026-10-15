package com.socialcoisa.rede.feed.application.port.in;

import com.socialcoisa.rede.feed.application.PostView;
import com.socialcoisa.rede.feed.domain.PostId;
import com.socialcoisa.rede.feed.domain.UserId;

/** Desfaz a exclusao logica de um post do proprio autor. */
public interface RestorePostUseCase {

	PostView restore(UserId userId, PostId postId);
}
