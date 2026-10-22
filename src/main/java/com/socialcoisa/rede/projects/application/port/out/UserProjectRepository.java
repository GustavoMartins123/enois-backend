package com.socialcoisa.rede.projects.application.port.out;

import java.util.List;
import java.util.Optional;

import com.socialcoisa.rede.projects.domain.UserProject;

public interface UserProjectRepository {

	List<UserProject> findByUser(String userId);

	Optional<UserProject> findById(String id);

	UserProject save(UserProject project);

	void delete(String id);
}
