package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobCategory;
import org.snomed.otf.scheduler.domain.JobParameter;
import org.snomed.otf.scheduler.domain.JobParameters;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobType;
import org.springframework.util.StringUtils;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 */
public class PrepMisalignedConcepts extends TemplateFix implements ReportClass {
	
	Map<Concept, List<String>> conceptDiagnostics = new HashMap<>();
	public static final String INCLUDE_COMPLEX = "Include complex cases";
	
	public PrepMisalignedConcepts() {
		super(null);
	}

	protected PrepMisalignedConcepts(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		PrepMisalignedConcepts app = new PrepMisalignedConcepts(null);
		try {
			//app.includeComplexTemplates = true;
			ReportSheetManager.targetFolderId = "1uywo1VGAIh7MMY7wCn2yEj312OQCjt9J"; // QI / Misaligned Concepts
			app.init(args);
			//app.getArchiveManager().allowStaleData = true;
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.runJob();
		} catch (Exception e) {
			info("Failed to produce Misaligned Concepts Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	public void runJob() throws TermServerScriptException {
		processFile();
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(TEMPLATE)
					.withType(JobParameter.Type.TEMPLATE)
					.withMandatory()
				.add(SUB_HIERARCHY)
					.withType(JobParameter.Type.CONCEPT)
				.add(ECL)
					.withType(JobParameter.Type.ECL)
				.add(INCLUDE_COMPLEX)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.build();
		
		return new Job(	new JobCategory(JobType.REPORT, JobCategory.QI),
						"Template Compliance",
						"This report lists concepts which match the specified selection (either a subhierarchy OR an ECL expression) " +
						"which DO NOT comply with the selected template.",
						params, ProductionStatus.HIDEME);
	}
	
	protected void init(JobRun jobRun) throws TermServerScriptException {
		
		selfDetermining = true;
		reportNoChange = false;
		runStandAlone = true; 
		populateEditPanel = true;
		populateTaskDescription = true;
		additionalReportColumns = "CharacteristicType, MatchedTemplate, Template Diagnostic";
		if (exclusionWords == null) {
			exclusionWords = new ArrayList<>();
		}
		
		super.init(jobRun);
		
		//Have we been called via the reporting platform?
		if (jobRun != null) {
			
			includeComplexTemplates = jobRun.getParameters().getMandatoryBoolean(INCLUDE_COMPLEX);
		
			subHierarchyECL = jobRun.getParamValue(ECL);
			if (StringUtils.isEmpty(subHierarchyECL)) {
				subHierarchyECL = "<< " + jobRun.getMandatoryParamValue(SUB_HIERARCHY);
			}

			String templateUrlStr = jobRun.getMandatoryParamValue(TEMPLATE);
			try {
				URL templateUrl = new URL(templateUrlStr);
				tsc = new TemplateServiceClient(templateUrlStr, authenticatedCookie);
				templates.add(loadTemplate('A',templateUrl));
			} catch (Exception e) {
				throw new TermServerScriptException("Unable to load template from " + templateUrlStr, e);
			}
			
			//Ensure our ECL matches more than 0 concepts before we import SNOMED - expensive!
			//This will also cache the result
			if (findConcepts(project.getBranchPath(), subHierarchyECL).size() == 0) {
				throw new TermServerScriptException(subHierarchyECL + " returned 0 rows");
			}
		}
	}

	protected void init(String[] args) throws TermServerScriptException {
		init((JobRun)null);
		/*
		subHierarchyECL = "<<125605004";  // QI-5 |Fracture of bone (disorder)|
		templateNames = new String[] {	"fracture/Fracture of Bone Structure.json",
										"fracture/Fracture Dislocation of Bone Structure.json",
										"fracture/Pathologic fracture of bone due to Disease.json",
										"fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json",
										"fracture/Traumatic abnormality of spinal cord structure co-occurrent and due to fracture morphology of vertebral bone structure.json",
										//"Injury of finding site due to birth trauma.json"
										 };
		
		subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"}; 
		setExclusions(new String[] {"40733004|Infectious disease|"});
		exclusionWords.add("arthritis");
		
		subHierarchyStr =  "126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"templates/Neoplasm of Bone.json",
										"templates/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};
		
		subHierarchyStr =  "34014006"; //QI-15 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Virus.json",
										"templates/infection/Infection of bodysite caused by virus.json"};
		
		subHierarchyECL = "<<87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Bacteria.json",
										"templates/infection/Infection of bodysite caused by bacteria.json"};
		 
		subHierarchyECL = "<<95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/infection/Infection caused by Protozoa with optional bodysite.json"};
			
		subHierarchyECL = "<<125666000";  //QI-33  |Burn (disorder)|

		excludeHierarchies = new String[] { "426284001" } ; // |Chemical burn (disorder)| 
		templateNames = new String[] {
				"templates/burn/Burn of body structure.json",
				"templates/burn/Epidermal burn of body structure.json",
				"templates/burn/Partial thickness burn of body structure.json",
				"templates/burn/Full thickness burn of body structure.json",
				"templates/burn/Deep partial thickness burn of body structure.json",
				"templates/burn/Superficial partial thickness burn of body structure.json"};
		
		subHierarchyECL = "<<74627003";  //QI-48 |Diabetic Complication|
		templateNames = new String[] {	"templates/Complication co-occurrent and due to Diabetes Melitus.json",
										"templates/Complication co-occurrent and due to Diabetes Melitus - Minimal.json"};
		
		subHierarchyECL = "<<8098009";	// QI-45 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		
		subHierarchyECL = "<<283682007"; // QI-39 |Bite - wound (disorder)|
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		
		subHierarchyECL = "<<3218000"; //QI-67 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Fungus.json"};
		
		subHierarchyECL = "<<17322007"; //QI-68 |Parasite (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Parasite.json"};
		
		subHierarchyECL = "<<416886008"; //QI-106 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json"
				};
		
		subHierarchyECL = "<<125643001"; //QI-107 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite due to event.json" };
		exclusionWords.add("complication");
		exclusionWords.add("fracture");
		setExclusions(new String[] {"399963005 |Abrasion (disorder)|", "312608009 |Laceration - injury|"});
		includeDueTos = true;
		
		subHierarchyECL = "<<128545000"; //QI-75 |Hernia of abdominal wall (disorder)|
		//subHierarchyECL = "<<773623000";
		templateNames = new String[] {	"templates/Hernia of abdominal wall.json"};
		excludeHierarchies = new String[] { "236037000 |Incisional hernia (disorder)|" };
		exclusionWords = new ArrayList<String>();
		exclusionWords.add("gangrene");
		exclusionWords.add("obstruction");
		
		subHierarchyECL = "<<432119003 |Aneurysm (disorder)|"; //QI-143 
		templateNames = new String[] {	"templates/Aneurysm of Cardiovascular system.json" };
		
		subHierarchyECL = "<<40733004|Infectious disease|"; //QI-153
		templateNames = new String[] {	"templates/infection/Infection NOS.json" };
		setExclusions(new String[] {"87628006 |Bacterial infectious disease (disorder)|","34014006 |Viral disease (disorder)|",
				"3218000 |Mycosis (disorder)|","8098009 |Sexually transmitted infectious disease (disorder)|", 
				"17322007 |Disease caused by parasite (disorder)|", "91302008 |Sepsis (disorder)|"});
		exclusionWords.add("shock");
		
		subHierarchyECL = "<<399963005 |Abrasion|"; //QI-147
		templateNames = new String[] {	"templates/wound/abrasion.json" ,
										"templates/Disorder due to birth trauma.json"};
		setExclusions(new String[] {"118938008 |Disorder of mouth (disorder)|"};
		includeDueTos = true;
		
		subHierarchyECL = "300935003 OR 206203003 OR 276623000"; //QI-147
		templateNames = new String[] {	"templates/Disorder due to birth trauma.json" };
		includeDueTos = true;
		
		
		subHierarchyECL = "<< 52515009 |Hernia of abdominal cavity|"; //QI-172
		setExclusions(new String[] {"236037000 |Incisional hernia (disorder)|", 
									"372070002 |Gangrenous disorder (disorder)|",
									"309753005 |Hiatus hernia with obstruction (disorder)|"});
		templateNames = new String[] {"templates/Hernia of Body Structure.json" };
		exclusionWords.add("gangrene");
		exclusionWords.add("obstruction");
		exclusionWords.add("obstructed");
		*/
		subHierarchyECL = "<<312608009 |Laceration - injury|"; //QI-177
		templateNames = new String[] {	"templates/wound/laceration.json" };
		includeDueTos = true;
		
		super.init(args);
		
		//Ensure our ECL matches more than 0 concepts before we import SNOMED - expensive!
		//This will also cache the result
		if (findConcepts(project.getBranchPath(), subHierarchyECL).size() == 0) {
			throw new TermServerScriptException(subHierarchyECL + " returned 0 rows");
		}
	}
	
	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		report(task, concept);
		return CHANGE_MADE;
	}

	private void report(Task t, Concept c) throws TermServerScriptException {
		//Collect the diagnostic information about why this concept didn't match any templates as a string
		String diagnosticStr = String.join("\n", conceptDiagnostics.get(c));
		report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, diagnosticStr);
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		
		//Start with the whole subHierarchy and remove concepts that match each of our templates
		List<Concept> unalignedConcepts = findConcepts(project.getBranchPath(), subHierarchyECL);
		
		//Set<Concept> unalignedConcepts = Collections.singleton(gl.getConcept("415771000"));
		Set<Concept> ignoredConcepts = new HashSet<>();
		
		for (Template template : templates) {
			Set<Concept> matches = findTemplateMatches(template);
			incrementSummaryInformation("Matched templates",matches.size());
			unalignedConcepts.removeAll(matches);
			int beforeCount = unalignedConcepts.size();
			unalignedConcepts.removeAll(exclusions);
			int afterCount = unalignedConcepts.size();
			addSummaryInformation("Excluded due to subHierarchy rules", (beforeCount - afterCount));
		}
		
		for (Concept c : unalignedConcepts) {
			if (!c.getConceptId().equals("186868000")) {
				//continue;
			}
			if (isExcluded(c)) {
				ignoredConcepts.add(c);
			} else {
				List<String> diagnostics = new ArrayList<String>();
				conceptDiagnostics.put(c, diagnostics);
				String msg = "Cardinality mismatch: " +  (c.getIssues().isEmpty()?" N/A" : c.getIssues());
				debug (c + ".  " + msg);
				diagnostics.add(msg);
				diagnostics.add("Relationship Group mismatches:");
				for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
					//is this group purely inferred?  Add an indicator if so 
					String purelyInferredIndicator = groupPurelyInferred(c,g)?"^":"";
					msg = "    " + purelyInferredIndicator + g;
					debug (msg);
					diagnostics.add(msg);
				}
				incrementSummaryInformation("Concepts identified as not matching any template");
				incrementSummaryInformation(ISSUE_COUNT);
			} 
		}
		unalignedConcepts.removeAll(ignoredConcepts);
		return asComponents(unalignedConcepts);
	}

	//return true if this inferred group does not have a stated counterpart
	private boolean groupPurelyInferred(Concept c, RelationshipGroup ig) {
		for (RelationshipGroup sg : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			if (ig.equals(sg)) {
				return false;
			}
		}
		return true;
	}

}
