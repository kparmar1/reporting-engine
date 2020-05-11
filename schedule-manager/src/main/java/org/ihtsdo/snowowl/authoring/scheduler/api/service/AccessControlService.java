package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.time.DateUtils;
import org.ihtsdo.otf.rest.client.authoringservices.AuthoringServicesClient;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service	
public class AccessControlService {
	
	private static int cacheTimeoutMins = 30;
	
	private Map<String, UserProjects> cache = new HashMap<>();
	
	AuthoringServicesClient authoringServices;
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	public Set<String> getProjects(String username, String serverUrl, String authToken) {
		if (!cache.containsKey(username) || cache.get(username).isExpired()) {
			UserProjects userProjects = getUserProjects(username, serverUrl, authToken);
			logger.info("Caching {}'s access to projects {}", username, userProjects.getProjects());
			cache.put(username, userProjects);
			//If the list of projects is only 1 long (ie MAIN) then we've probably a problem with 
			//configuration for that user.  Force expiry of the visible project list in this case
			if (userProjects.size() <= 1) {
				logger.info("User has access to {} projects.  Suspected configuration issue.  Expiring cache.", userProjects.size());
				userProjects.expire();
			}
		}
		UserProjects userProjects = cache.get(username);
		return userProjects.getProjects();
	}

	private UserProjects getUserProjects(String username, String serverUrl, String authToken) {
		prepareAuthServices(serverUrl, authToken);
		List<Project> projects = authoringServices.listProjects();
		Set<String> projectStrs = projects.stream().map(p -> p.getKey())
				.collect(Collectors.toSet());
		//MAIN is not a project in Jira.  OK to add since MS generally doesn't use this
		projectStrs.add("MAIN");
		return new UserProjects(projectStrs);
	}

	private void prepareAuthServices(String serverUrl, String authToken) {
		//Is this our first call?  Create AuthServices if so, else update 
		if (authoringServices == null) {
			authoringServices = new AuthoringServicesClient(serverUrl, authToken);
		} else {
			authoringServices.updateAuthToken(authToken);
		}
	}

	private class UserProjects {
		Date created;
		Set<String> projects;
		
		public UserProjects(Set<String> projects) {
			this.projects = projects;
			created = new Date();
		}
		
		public int size() {
			return projects == null ? 0 : projects.size();
		}
		
		public Set<String> getProjects() {
			return projects;
		}
		
		public boolean isExpired() {
			return DateUtils.addMinutes(created, cacheTimeoutMins).before(new Date());
		}
		
		public void expire() {
			created = DateUtils.addMinutes(created, (-1 * cacheTimeoutMins));
		}
	}
}
