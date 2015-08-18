package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.NotFoundException;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.ArbitraryJsonService;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

public class UiStateService {

	@Autowired
	private ArbitraryJsonService arbitraryJsonService;

	public void persistPanelState(String projectKey, String taskKey, String username, String panelId, String jsonState) throws IOException {
		arbitraryJsonService.write(getUserPanelPath(projectKey, taskKey, username, panelId), jsonState);
	}

	public String retrievePanelState(String projectKey, String taskKey, String username, String panelId) throws IOException {
		try {
			return arbitraryJsonService.read(getUserPanelPath(projectKey, taskKey, username, panelId));
		} catch (NoSuchFileException e) {
			throw new NotFoundException("ui-state", panelId);
		}
	}

	private String getUserPanelPath(String projectKey, String taskKey, String username, String panelId) {
		return projectKey + "/" + taskKey + "/user/" + username + "/ui-panel/" + panelId + ".json";
	}


}