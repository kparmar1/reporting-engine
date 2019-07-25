package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;

import java.util.*;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Project;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;


public class AuthoringServicesClient {
	RestTemplate restTemplate;
	HttpHeaders headers;
	private final String serverUrl;
	private final String cookie;
	private static final String apiRoot = "authoring-services/";
	private static final String JSON_CONTENT_TYPE = "application/json";
	
	public AuthoringServicesClient(String serverUrl, String cookie) {
		this.serverUrl = serverUrl;
		this.cookie = cookie;
		
		//sun.util.logging.PlatformLogger.getLogger("sun.net.www.protocol.http.HttpURLConnection").setLevel(PlatformLogger.Level.ALL);
		//sun.util.logging.PlatformLogger.getLogger("sun.net.www.protocol.https.DelegateHttpsURLConnection").setLevel(PlatformLogger.Level.ALL);
		
		headers = new HttpHeaders();
		headers.add("Cookie", this.cookie );
		headers.setContentType(MediaType.APPLICATION_JSON);
		
		restTemplate = new RestTemplateBuilder()
				.rootUri(this.serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter())
				.errorHandler(new ExpressiveErrorHandler())
				.build();
	}

	public String createTask(String projectKey, String summary, String description) throws Exception {
		String endPoint = serverUrl + apiRoot + "projects/" + projectKey + "/tasks";
		JsonObject requestJson = new JsonObject();
		requestJson.addProperty("summary", summary);
		requestJson.addProperty("description", description);
		HttpEntity<Object> requestEntity = new HttpEntity<Object>(requestJson, headers);
		Task task = restTemplate.postForObject(endPoint, requestEntity, Task.class);
		return task.getKey();
	}

	public void setEditPanelUIState(String project, String taskKey, List<String> conceptIds) throws IOException {
		String endPointRoot = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey + "/ui-state/";
		String url = endPointRoot + "edit-panel";
		HttpEntity<List<String>> request = new HttpEntity<>(conceptIds, headers);
		restTemplate.postForEntity(url, request, Void.class);
	}
	
	public void setSavedListUIState(String project, String taskKey, JsonObject items) throws IOException {
		String endPointRoot = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey + "/ui-state/";
		String url = endPointRoot + "saved-list";
		HttpEntity<Object> request = new HttpEntity<>(items, headers);
		restTemplate.postForEntity(url, request, Void.class);
	}
	
	public String updateTask(String project, String taskKey, String summary, String description, String author, String reviewer) throws Exception {
		String endPoint = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey;
		
		JsonObject requestJson = new JsonObject();
		if (summary != null) {
			requestJson.addProperty("summary", summary);
		}
		
		if (description != null) {
			requestJson.addProperty("description", description);
		}
		
		if (author != null) {
			JsonObject assigneeJson = new JsonObject();
			assigneeJson.addProperty("username", author);
			requestJson.add("assignee", assigneeJson);
		}
		
		if (reviewer != null) {
			requestJson.addProperty("status", "IN_REVIEW");
			JsonArray reviewers = new JsonArray();
			JsonObject reviewerJson = new JsonObject();
			reviewerJson.addProperty("username", reviewer);
			reviewers.add(reviewerJson);
			requestJson.add("reviewers", reviewers);
		}
		
		HttpEntity<Object> requestEntity = new HttpEntity<Object>(requestJson, headers);
		restTemplate.put(endPoint, requestEntity);
		return taskKey;
	}
	
	public void deleteTask(String project, String taskKey, boolean optional) throws TermServerClientException {
		String url = serverUrl + apiRoot + "projects/" + project + "/tasks/" + taskKey;
		try {
			JsonObject requestJson = new JsonObject();
			requestJson.addProperty("status", "DELETED");
			HttpEntity<Object> request = new HttpEntity<>(requestJson, headers);
			restTemplate.exchange(url, HttpMethod.PUT, request, Concept.class);
		} catch (Exception e) {
			String errStr = "Failed to delete task - " + taskKey;
			if (optional) {
				System.out.println(errStr + ": " + e.getMessage());
			} else {
				throw new TermServerClientException (errStr, e);
			}
		}
	}

	public Project getProject(String projectStr) throws TermServerClientException {
		try {
			String url = serverUrl + apiRoot + "projects/" + projectStr;
			return restTemplate.getForObject(url, Project.class);
		} catch (RestClientException e) {
			throw new TermServerClientException("Unable to recover project: " + projectStr, e);
		}
	}
	
	public Task getTask(String taskKey) throws TermServerClientException {
		try {
			String projectStr = taskKey.substring(0, taskKey.indexOf("-"));
			String url = serverUrl + apiRoot + "projects/" + projectStr + "/tasks/" + taskKey;
			return restTemplate.getForObject(url, Task.class);
		} catch (Exception e) {
			throw new TermServerClientException("Unable to recover task " + taskKey, e);
		}
	}

	public Classification classify(String taskKey) throws TermServerClientException {
		try {
			String projectStr = taskKey.substring(0, taskKey.indexOf("-"));
			String endPoint = serverUrl + apiRoot + "projects/" + projectStr + "/tasks/" + taskKey + "/classifications";
			HttpEntity<Object> requestEntity = new HttpEntity<Object>("", headers);
			return restTemplate.postForObject(endPoint, requestEntity, Classification.class);
		} catch (Exception e) {
			throw new TermServerClientException("Unable to classify " + taskKey, e);
		}
	}
	
	public Status validate(String taskKey) throws TermServerClientException {
		try {
			String projectStr = taskKey.substring(0, taskKey.indexOf("-"));
			String url = serverUrl + apiRoot + "projects/" + projectStr + "/tasks/" + taskKey + "/validation";
			HttpEntity<String> request = new HttpEntity<>("");
			return restTemplate.postForObject(url, request, Status.class);
		} catch (Exception e) {
			throw new TermServerClientException("Unable to initiate validation on " + taskKey, e);
		}
	}

}
