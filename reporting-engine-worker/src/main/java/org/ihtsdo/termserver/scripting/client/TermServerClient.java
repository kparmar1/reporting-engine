package org.ihtsdo.termserver.scripting.client;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public class TermServerClient {
	
	public enum ExtractType {
		DELTA, SNAPSHOT, FULL;
	};

	public enum ExportType {
		PUBLISHED, UNPUBLISHED, MIXED;
	}
	
	public static SimpleDateFormat YYYYMMDD = new SimpleDateFormat("yyyyMMdd");
	public static final int MAX_TRIES = 3;
	public static final int retry = 15;
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.registerTypeAdapter(Relationship.class, new RelationshipSerializer());
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	private final RestTemplate restTemplate;
	private final HttpHeaders headers;
	private final String url;
	private static final String ALL_CONTENT_TYPE = "*/*";
	//private static final String SNOWOWL_CONTENT_TYPE = "application/json";
	private final Set<SnowOwlClientEventListener> eventListeners;
	private Logger logger = LoggerFactory.getLogger(getClass());
	public static boolean supportsIncludeUnpublished = true;

	public TermServerClient(String serverUrl, String cookie) {
		this.url = serverUrl;
		eventListeners = new HashSet<>();
		
		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", ALL_CONTENT_TYPE);
		
		restTemplate = new RestTemplateBuilder()
				.rootUri(this.url)
				.additionalMessageConverters(new GsonHttpMessageConverter(gson))
				.errorHandler(new ExpressiveErrorHandler())
				.build();
		
		//Add a ClientHttpRequestInterceptor to the RestTemplate
		restTemplate.getInterceptors().add(new ClientHttpRequestInterceptor(){
			@Override
			public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
				request.getHeaders().addAll(headers);
				return execution.execute(request, body);
			}
		}); 
	}
	
	public Branch getBranch(String branchPath) throws TermServerClientException {
		try {
			String url = getBranchesPath(branchPath);
			logger.debug("Recovering branch information from " + url);
			return restTemplate.getForObject(url, Branch.class);
		} catch (RestClientException e) {
			throw new TermServerClientException(translateRestClientException(e));
		}
	}

	private Throwable translateRestClientException(RestClientException e) {
		//Can we find some known error in the text returned so that we don't have
		//to return the whole thing?
		String message = e.getMessage();
		String mainContent = StringUtils.substringBetween(message, "<section class=\"mainContent contain\">", "</section>");
		if (mainContent != null) {
			return new TermServerScriptException("Returned from TS: " + mainContent);
		}
		return e;
	}

	public Concept createConcept(Concept concept, String branchPath) throws TermServerClientException {
		try {
			HttpEntity<Concept> requestEntity = new HttpEntity<>(concept, headers);
			Concept newConcept = restTemplate.postForObject(getConceptBrowserPath(branchPath), requestEntity, Concept.class);
			logger.info("Created concept " + newConcept);
			return newConcept;
		} catch (Exception e) {
			throw new TermServerClientException("Failed to create concept: " + gson.toJson(concept), e);
		}
	}

	public Concept updateConcept(Concept c, String branchPath) throws TermServerClientException {
		HttpEntity<Concept> response = null;
		Concept updatedConcept = null;
		try {
			Preconditions.checkNotNull(c.getConceptId());
			String url = getConceptBrowserPath(branchPath) + "/" +  c.getConceptId();
			boolean updatedOK = false;
			int tries = 0;
			while (!updatedOK) {
				try {
					HttpEntity<Concept> requestEntity = new HttpEntity<>(c, headers);
					response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, Concept.class);
					updatedOK = true;
					logger.info("Updated concept " + c);
					updatedConcept = response.getBody();
					if (updatedConcept == null) {
						throw new TermServerClientException ("Received unexpected: " + response.toString());
					}
				} catch (Exception e) {
					tries++;
					if (tries >= MAX_TRIES) {
						throw new TermServerClientException("Failed to update concept " + c + " after " + tries + " attempts due to " + e.getMessage(), e);
					}
					logger.debug("Update of concept failed, trying again....",e);
					Thread.sleep(30*1000); //Give the server 30 seconds to recover
				}
			}
			return updatedConcept;
		} catch (Exception e) {
			throw new TermServerClientException(e);
		}
	}

	public Concept getConcept(String sctid, String branchPath) throws TermServerClientException {
		try {
			return restTemplate.getForObject(getConceptBrowserPath(branchPath) + "/" + sctid, Concept.class);
		} catch (RestClientException e) {
			throw new TermServerClientException(e);
		}
	}

	public void deleteConcept(String sctId, String branchPath) throws TermServerClientException {
		try {
			restTemplate.delete(getConceptsPath(sctId, branchPath));
			logger.info("Deleted concept " + sctId + " from " + branchPath);
		} catch (RestClientException e) {
			throw new TermServerClientException(e);
		}
	}
	
	public ConceptCollection getConcepts(String ecl, String branchPath, String searchAfter, int limit) {
		String url = getConceptsPath(branchPath) + "?active=true&limit=" + limit + "&ecl=" + ecl;
		if (!StringUtils.isEmpty(searchAfter)) {
			url += "&searchAfter=" + searchAfter;
		}
		System.out.println("Calling " + url);
		return restTemplate.getForObject(url, ConceptCollection.class);
	}

	private String getBranchesPath(String branchPath) {
		return  url + "/branches/" + branchPath;
	}
	
	private String getConceptsPath(String sctId, String branchPath) {
		return url + "/" + branchPath + "/concepts/" + sctId;
	}

	private String getConceptsPath(String branchPath) {
		return url + "/" + branchPath + "/concepts";
	}

	private String getConceptBrowserPath(String branchPath) {
		return url + "/browser/" + branchPath + "/concepts";
	}
	
	private String getConceptBrowserValidationPath(String branchPath) {
		return url + "/browser/" + branchPath + "/validate/concept";
	}

	public String createBranch(String parent, String branchName) throws TermServerClientException {
		try {
			JsonObject requestJson = new JsonObject();
			requestJson.addProperty("parent", parent);
			requestJson.addProperty("name", branchName);
			HttpEntity<Object> requestEntity = new HttpEntity<>(requestJson, headers);
			restTemplate.postForEntity(url + "/branches", requestEntity, Branch.class);
			final String branchPath = parent + "/" + branchName;
			logger.info("Created branch {}", branchPath);
			for (SnowOwlClientEventListener eventListener : eventListeners) {
				eventListener.branchCreated(branchPath);
			}
			return branchPath;
		} catch (Exception e) {
			throw new TermServerClientException(e);
		}
	}

	public void deleteBranch(String branchPath) throws TermServerClientException {
		try {
			restTemplate.delete(url + "/branches/" + branchPath);
			logger.info("Deleted branch {}", branchPath);
		} catch (RestClientException e) {
			throw new TermServerClientException(e);
		}
	}

	public List<Concept> search(String query, String branchPath) throws TermServerClientException {
		try {
			String url = this.url + "/browser/" + branchPath + "/descriptions?query=" + query;
			ResponseEntity<List<Concept>> response = restTemplate.exchange(
					url,
					HttpMethod.GET,
					null,
					new ParameterizedTypeReference<List<Concept>>(){});
			return response.getBody();
		} catch (RestClientException e) {
			throw new TermServerClientException(e);
		}
	}

	public List<Concept> searchWithPT(String query, String branchPath) throws TermServerClientException {
		try {
			String url = this.url + "/browser/" + branchPath + "/descriptions-pt?query=" + query;
			ResponseEntity<List<Concept>> response = restTemplate.exchange(
					url,
					HttpMethod.GET,
					null,
					new ParameterizedTypeReference<List<Concept>>(){});
			return response.getBody();
		} catch (RestClientException e) {
			throw new TermServerClientException(e);
		}
	}

	public void addEventListener(SnowOwlClientEventListener eventListener) {
		eventListeners.add(eventListener);
	}

	/**
	 * Returns id of classification
	 * @param branchPath
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws InterruptedException
	 */
	public String classifyAndWaitForComplete(String branchPath) throws TermServerClientException {
		try {
			final JsonObject json = new JsonObject();
			json.addProperty("reasonerId", "org.semanticweb.elk.elk.reasoner.factory");
			String url = this.url + "/" + branchPath + "/classifications";
			System.out.println(url);
			System.out.println(json);
			ResponseEntity<JsonObject> response = restTemplate.getForEntity(url, JsonObject.class);
			final String location = response.getHeaders().getFirst("Location");
			System.out.println("location " + location);

			String status;
			do {
				final JsonObject jsonect = restTemplate.getForObject(location, JsonObject.class);
				status = jsonect.get("status").getAsString();
			} while (("SCHEDULED".equals(status) || "RUNNING".equals(status) && sleep(10)));

			if ("COMPLETED".equals(status)) {
				return location.substring(location.lastIndexOf("/"));
			} else {
				throw new TermServerClientException("Unexpected classification state " + status);
			}
		} catch (Exception e) {
			throw new TermServerClientException(e);
		}
	}

	private boolean sleep(int seconds) throws InterruptedException {
		Thread.sleep(1000 * seconds);
		return true;
	}

	public File export(String branchPath, String effectiveDate, ExportType exportType, ExtractType extractType, File saveLocation)
			throws TermServerClientException {
		JsonObject json = prepareExportJSON(branchPath, effectiveDate, exportType, extractType);
		logger.info ("Initiating export with {}",json);
		String exportLocationURL = initiateExport(json);
		File recoveredArchive = recoverExportedArchive(exportLocationURL, saveLocation);
		return recoveredArchive;
	}
	
	private JsonObject prepareExportJSON(String branchPath, String effectiveDate, ExportType exportType, ExtractType extractType)
			throws TermServerClientException {
		JsonObject json = new JsonObject();
		try {
			json.addProperty("type", extractType.toString());
			json.addProperty("branchPath", branchPath);
			switch (exportType) {
				case MIXED:  //Snapshot allows for both published and unpublished, where unpublished
					//content would get the transient effective Date
					if (!extractType.equals(ExtractType.SNAPSHOT)) {
						throw new TermServerClientException("Export type " + exportType + " not recognised");
					}
					if (supportsIncludeUnpublished) {
						json.addProperty("includeUnpublished", true);
					}
				case UNPUBLISHED:
					//Now leaving effective date blank if not specified
					if (effectiveDate != null) {
						//String tet = (effectiveDate == null) ?YYYYMMDD.format(new Date()) : effectiveDate;
						json.addProperty("transientEffectiveTime", effectiveDate);
					}
					break;
				case PUBLISHED:
					if (effectiveDate == null) {
						throw new TermServerClientException("Cannot export published data without an effective date");
					}
					json.addProperty("deltaStartEffectiveTime", effectiveDate);
					json.addProperty("deltaEndEffectiveTime", effectiveDate);
					json.addProperty("transientEffectiveTime", effectiveDate);
					break;
				
				default:
					throw new TermServerClientException("Export type " + exportType + " not recognised");
			}
		} catch (RestClientException e) {
			throw new TermServerClientException("Failed to prepare JSON for export request.", e);
		}
		return json;
	}

	private String initiateExport(JsonObject json) throws TermServerClientException {
		try {
			HttpEntity<JsonObject> request = new HttpEntity<>(json, headers);
			String exportLocation = restTemplate.postForLocation(url + "/exports", request).toString();
			logger.info ("Recovering export from {}",exportLocation);
			return exportLocation + "/archive";
		} catch (RestClientException e) {
			throw new TermServerClientException("Failed to initiate export", e);
		}
	}

	private File recoverExportedArchive(String exportLocationURL, File saveLocation) throws TermServerClientException {
		try {
			logger.info("Recovering exported archive from {}", exportLocationURL);
			if (saveLocation == null) {
				saveLocation = File.createTempFile("ts-extract", ".zip");
			}
			RequestCallback requestCallback = request -> request.getHeaders()
				.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL));
			
			// Streams the response instead of loading it all in memory
			final Path path = saveLocation.toPath();
			ResponseExtractor<Void> responseExtractor = response -> {
				Files.copy(response.getBody(), path, StandardCopyOption.REPLACE_EXISTING);
				return null;
			};
			restTemplate.execute(exportLocationURL, HttpMethod.GET, requestCallback, responseExtractor);
			logger.debug("Extract saved to {}", saveLocation.getAbsolutePath());
			return saveLocation;
		} catch (IOException e) {
			throw new TermServerClientException("Unable to recover exported archive from " + exportLocationURL, e);
		}
	}

	public void deleteRefsetMember(String langRefMemberId, String branch, boolean toForce) throws TermServerClientException {
		try {
			String url = getRefsetMemberUpdateUrl(langRefMemberId, branch, toForce);
			restTemplate.delete(url);
			logger.info("deleted refset member id:" + langRefMemberId);
		} catch (RestClientException e) {
			throw new TermServerClientException(e);
		}
	}
	
	private String getRefsetMemberUrl(String refSetMemberId, String branch) {
		return this.url + "/" + branch + "/members/" + refSetMemberId;
	}
	
	private String getRefsetMemberUpdateUrl(String refSetMemberId, String branch, boolean toForce) {
		return getRefsetMemberUrl(refSetMemberId, branch) + "?force=" + toForce;
	}

	public Refset loadRefsetEntries(String branchPath, String refsetId, String referencedComponentId) throws TermServerClientException {
		try {
			String url = this.url + "/" + branchPath + "/members?referenceSet=" + refsetId + "&referencedComponentId=" + referencedComponentId;
			return restTemplate.getForObject(url, Refset.class);
		} catch (Exception e) {
			throw new TermServerClientException("Unable to recover refset for " + refsetId + " - " + referencedComponentId, e);
		}
	}

	public void updateRefsetMember(String branchPath, RefsetEntry refsetEntry, boolean forceUpdate) throws TermServerClientException {
		try {
			String url = this.url + "/" + branchPath + "/members/" + refsetEntry.getId();
			if (forceUpdate) {
				url += "?force=true";
			}
			HttpEntity<RefsetEntry> requestEntity = new HttpEntity<>(refsetEntry, headers);
			restTemplate.exchange(url, HttpMethod.PUT, requestEntity, Concept.class);
		} catch (RestClientException e) {
			throw new TermServerClientException("Unable to update refset entry " + refsetEntry + " due to " + e.getMessage(), e);
		}
	}
	

	public void waitForCompletion(String branchPath, Classification classification) throws TermServerClientException {
		try {
			String url = this.url + "/" + branchPath + "/classifications/" + classification.getId();
			Status status = new Status("Unknown");
			long sleptSecs = 0;
			do {
				status = restTemplate.getForObject(url, Status.class);
				if (!status.isFinalState()) {
					Thread.sleep(retry * 1000);
					sleptSecs += retry;
					if (sleptSecs % 60 == 0) {
						System.out.println("Waited for " + sleptSecs + " for classification " + classification.getId() + " on " + branchPath);
					}
				}
			} while (!status.isFinalState());
		} catch (Exception e) {
			throw new TermServerClientException("Unable to recover status of classification " + classification.getId() + " due to " + e.getMessage(), e);
		}
	}

	public DroolsResponse[] validateConcept(Concept c, String branchPath) {
		String url = getConceptBrowserValidationPath(branchPath);
		HttpEntity<Concept> request = new HttpEntity<>(c, headers); 
		return restTemplate.postForObject(url, request, DroolsResponse[].class);
	}

	public String getUrl() {
		return this.url;
	}

}
