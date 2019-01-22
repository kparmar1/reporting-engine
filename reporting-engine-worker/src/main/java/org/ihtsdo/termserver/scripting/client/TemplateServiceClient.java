package org.ihtsdo.termserver.scripting.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.authoringtemplate.domain.*;
import org.snomed.authoringtemplate.domain.logical.*;
import org.snomed.authoringtemplate.service.LogicalTemplateParserService;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.http.client.*;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Client can either load a template from the template service, or from a local resource
 * @author Peter
 *
 */
public class TemplateServiceClient {
	
	Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private static String TEMPLATES = "/templates/";
	
	private final HttpHeaders headers;
	private final RestTemplate restTemplate;
	LogicalTemplateParserService service  = new LogicalTemplateParserService();
	ObjectMapper mapper = new ObjectMapper();
	private static final String CONTENT_TYPE = "application/json";
	private final int minViableLength = 10;
	
	public LogicalTemplate loadLogicalLocalTemplate (String templateName) throws JsonParseException, JsonMappingException, IOException {
		ClassLoader classLoader = getClass().getClassLoader();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return loadLogicalTemplate(classLoader.getResourceAsStream(templateName));
	}
	
	public LogicalTemplate loadLogicalTemplate (InputStream templateStream) throws JsonParseException, JsonMappingException, IOException {
		ConceptTemplate conceptTemplate = mapper.readValue(templateStream, ConceptTemplate.class );
		LogicalTemplate logicalTemplate = parseLogicalTemplate(conceptTemplate.getLogicalTemplate());
		return logicalTemplate;
	}
	
	public LogicalTemplate loadLogicalTemplate (String templateName) throws IOException, TermServerScriptException {
		ResponseEntity<ConceptTemplate> response = restTemplate.exchange(
				TEMPLATES + templateName,
				HttpMethod.GET,
				null,
				ConceptTemplate.class);
		ConceptTemplate conceptTemplate = response.getBody();
		return parseLogicalTemplate(conceptTemplate.getLogicalTemplate());
	}
	
	public LogicalTemplate parseLogicalTemplate (String template) throws JsonParseException, JsonMappingException, IOException {
		return service.parseTemplate(template);
	}
	
	public TemplateServiceClient(String serverUrl, String cookie) {
		headers = new HttpHeaders();
		headers.add("Cookie", cookie);
		headers.add("Accept", CONTENT_TYPE);
		
		if (serverUrl != null) {
			//Have we been passed a full url?
			int cutPoint = serverUrl.indexOf(TEMPLATES);
			if (cutPoint != -1) {
				serverUrl = serverUrl.substring(0,cutPoint);
			}
		}
		
		restTemplate = new RestTemplateBuilder()
				.rootUri(serverUrl)
				.additionalMessageConverters(new GsonHttpMessageConverter())
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
	
	public List<ConceptTemplate> getAllTemplates() {
		ResponseEntity<List<ConceptTemplate>> response = restTemplate.exchange(
				"/templates",
				HttpMethod.GET,
				null,
				new ParameterizedTypeReference<List<ConceptTemplate>>(){});
		return response.getBody();
	}

}
