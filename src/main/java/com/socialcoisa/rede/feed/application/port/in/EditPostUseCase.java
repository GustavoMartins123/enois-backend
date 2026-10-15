package com.socialcoisa.rede.feed.application.port.in;

import com.socialcoisa.rede.feed.application.EditPostCommand;
import com.socialcoisa.rede.feed.application.PostView;

public interface EditPostUseCase {

	PostView edit(EditPostCommand command);
}
