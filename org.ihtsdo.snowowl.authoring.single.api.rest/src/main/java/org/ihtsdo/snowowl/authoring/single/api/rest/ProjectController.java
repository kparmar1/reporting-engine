package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.review.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import us.monoid.json.JSONException;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletRequest;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class ProjectController extends AbstractSnomedRestService {

	@Autowired
	private TaskService taskService;

	@ApiOperation(value="List authoring projects")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects", method= RequestMethod.GET)
	public List<AuthoringProject> listProjects() throws JiraException, IOException, JSONException, RestClientException {
		return taskService.listProjects();
	}

	@ApiOperation(value="Retrieve an authoring project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}", method= RequestMethod.GET)
	public AuthoringProject retrieveProject(@PathVariable final String projectKey) throws JiraException, RestClientException, JSONException, IOException {
		return taskService.retrieveProject(projectKey);
	}

	@ApiOperation(value="List tasks within a project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listTasks(@PathVariable final String projectKey) throws JiraException, RestClientException {
		return taskService.listTasks(projectKey);
	}

	@ApiOperation(value="List authenticated user's tasks across projects")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/my-tasks", method= RequestMethod.GET)
	public List<AuthoringTask> listMyTasks() throws JiraException, RestClientException {
		UserDetails details = ControllerHelper.getUserDetails();
		return taskService.listMyTasks(details.getUsername());
	}

	@ApiOperation(value="Retrieve a task within a project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}", method= RequestMethod.GET)
	public AuthoringTask retrieveTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws JiraException, RestClientException {
		return taskService.retrieveTask(projectKey, taskKey);
	}

	@ApiOperation(value="Retrieve the review list for a task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review", method= RequestMethod.GET)
	public AuthoringTaskReview retrieveTaskReview(@PathVariable final String projectKey, @PathVariable final String taskKey, HttpServletRequest request) throws ExecutionException, InterruptedException {
		return taskService.retrieveTaskReview(projectKey, taskKey, Collections.list(request.getLocales()));
	}

	@ApiOperation(value="Create a task within a project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks", method= RequestMethod.POST)
	public AuthoringTask createTask(@PathVariable final String projectKey, @RequestBody final AuthoringTaskCreateRequest taskCreateRequest) throws JiraException, ServiceException {
		return taskService.createTask(projectKey, taskCreateRequest);
	}

	@ApiOperation(value = "Mark task as ready for review")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/review", method = RequestMethod.POST)
	public void startValidation(@PathVariable final String projectKey, @PathVariable final String taskKey) throws JiraException,
			JSONException, IOException, BusinessServiceException {
		taskService.startReview(projectKey, taskKey);
	}

	/** This is planned for a future sprint
	@ApiOperation(value="Playing with MRCM rules.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/mrcm", method= RequestMethod.GET)
	public int listMRCMRules() throws IOException {
		SnomedPredicateBrowser predicateBrowser = ApplicationContext.getInstance().getService(SnomedPredicateBrowser.class);
		IBranchPath mainPath = BranchPathUtils.createMainPath();
		Collection<PredicateIndexEntry> predicate = predicateBrowser.getPredicates(mainPath, "361083003", null);
		return predicate.size();
	}
	**/

}