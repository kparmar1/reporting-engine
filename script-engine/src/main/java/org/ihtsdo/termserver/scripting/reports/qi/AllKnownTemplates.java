package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TemplateServiceClient;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.Template;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.authoringtemplate.domain.ConceptTemplate;
import org.snomed.authoringtemplate.domain.logical.LogicalTemplate;
import org.snomed.otf.scheduler.domain.JobRun;

public abstract class AllKnownTemplates extends TermServerReport {
	
	TemplateServiceClient tsc;
	Map<String, List<Template>> domainTemplates = new HashMap<>();
	
	public static final String TEMPLATE_SERVICE = "Template Service";
	public static final String QI = "QI Project";

	public void init (JobRun run) throws TermServerScriptException {
		String templateServiceUrl = run.getMandatoryParamValue(SERVER_URL);
		tsc = new TemplateServiceClient(templateServiceUrl, run.getAuthToken());
		ReportSheetManager.targetFolderId = "1YoJa68WLAMPKG6h4_gZ5-QT974EU9ui6"; //QI / Stats
		
		String subHierarchyECL = "<< 125605004";  // QI-5 |Fracture of bone (disorder)|
		String[] templateNames = new String[] {	"templates/fracture/Fracture of Bone Structure.json",
										"templates/fracture/Fracture Dislocation of Bone Structure.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"}; 
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"templates/neoplasm/Neoplasm of Bone.json",
										"templates/fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 34014006"; //QI-15 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Virus.json",
										"templates/infection/Infection of bodysite caused by virus.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by bacteria.json",
										"templates/infection/Infection of bodysite caused by bacteria.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/infection/Infection caused by Protozoa with optional bodysite.json"};
		populateTemplates(subHierarchyECL, templateNames);
			
		subHierarchyECL = "<< 125666000";  //QI-33  |Burn (disorder)|
		templateNames = new String[] {
				"templates/burn/Burn of body structure.json",
				"templates/burn/Epidermal burn of body structure.json",
				"templates/burn/Partial thickness burn of body structure.json",
				"templates/burn/Full thickness burn of body structure.json",
				"templates/burn/Deep partial thickness burn of body structure.json",
				"templates/burn/Superficial partial thickness burn of body structure.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 74627003";  //QI-48 |Diabetic Complication|
		templateNames = new String[] {	"templates/Complication due to Diabetes Melitus.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 8098009";	// QI-45 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 283682007"; // QI-39 |Bite - wound (disorder)|
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 3218000"; //QI-67 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Fungus.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 17322007"; //QI-68 |Parasite (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Parasite.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 416886008"; //QI-106 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 125643001"; //QI-107 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 432119003 |Aneurysm (disorder)|"; //QI-143 
		templateNames = new String[] {	"templates/Aneurysm of Cardiovascular system.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< <<40733004|Infectious disease|"; //QI-153
		templateNames = new String[] {	"templates/infection/Infection NOS.json" };
		
		subHierarchyECL = "<< 399963005 |Abrasion|"; //QI-147
		templateNames = new String[] {	"templates/wound/abrasion.json" ,
										"templates/Disorder due to birth trauma.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 300935003"; //QI-147
		templateNames = new String[] {	"templates/Disorder due to birth trauma.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 52515009 |Hernia of abdominal cavity|"; //QI-172
		templateNames = new String[] {"templates/hernia/Hernia of Body Structure.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 312608009 |Laceration - injury|"; //QI-177
		templateNames = new String[] {	"templates/wound/laceration.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 3723001 |Arthritis (disorder)|"; //QI-123
		templateNames = new String[] {	"templates/Arthritis.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 428794004 |Fistula (disorder)|"; //QI-186
		templateNames = new String[] {	"templates/Fistula.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 441457006 |Cyst|"; //QI-181
		templateNames = new String[] {	"templates/Cyst.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 128477000 |Abscess (disorder)|"; //QI-213
		templateNames = new String[] {	"templates/Abscess.json",
										"templates/Abscess with Cellulitis.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 416462003 |Wound (disorder)|"; //QI-209
		templateNames = new String[] {	"templates/wound/wound of bodysite.json",
										"templates/wound/wound of bodysite due to event.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 125670008 |Foreign body (disorder)|"; //QI-156
		templateNames = new String[] {	"templates/Foreign body.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 193570009 |Cataract (disorder)|"; //MQI-7
		templateNames = new String[] {	"templates/Cataract.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 429040005 |Ulcer (disorder)|"; //QI-248
		templateNames = new String[] {	"templates/Ulcer.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 109355002 |Carcinoma in situ (disorder)|"; //QI-231
		templateNames = new String[] {	"templates/neoplasm/Carcinoma in Situ.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 763158003 |Medicinal product (product)|"; //RP-135
		templateNames = new String[] {	"templates/drugs/MP only.json",
										"templates/drugs/MP containing.json",
										"templates/drugs/MPF containing.json",
										"templates/drugs/MPF only.json",
										"templates/drugs/CD precise discrete.json",
										"templates/drugs/CD precise continuous.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 247441003 |Erythema|"; //QI-240
		templateNames = new String[] {	"templates/Erythema of body structure.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 445505000 |Contracture of joint of spine (disorder)|";
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 64572001 |Disease (disorder)|"; 
		templateNames = new String[] {	"templates/Disease.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 7890003 |Contracture of joint (disorder)|"; //QI-261
		templateNames = new String[] {	"templates/Contracture of joint minus.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 125667009 |Contusion (disorder)|"; //QI-244 
		templateNames = new String[] {	"templates/wound/contusion.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 85828009 |Autoimmune disease (disorder)|"; //QI-297
		templateNames = new String[] {	"templates/Autoimune.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 298180004 |Finding of range of joint movement (finding)|  MINUS <<  7890003 |Contracture of joint (disorder)|";
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 417893002|Deformity|"; //QI-278
		templateNames = new String[] {	"templates/Deformity - disorder.json",
				"templates/Deformity - finding.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 118616009 |Neoplastic disease of uncertain behavior (disorder)|"; //QI-253 |Neoplastic disease of uncertain behavior| 
		templateNames = new String[] {	"templates/neoplasm/Neoplastic Disease.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 20376005 |Benign neoplastic disease|"; //QI-272
		templateNames = new String[] {	"templates/neoplasm/Benign Neoplastic Disease.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 233776003 |Tracheobronchial disorder|"; //QI-268
		templateNames = new String[] {	"templates/Tracheobronchial.json" };
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 298180004 |Finding of range of joint movement (finding)| MINUS << 7890003 |Contracture of joint (disorder)|"; //QI-284
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 400006008 |Hamartoma (disorder)|"; //QI-296
		templateNames = new String[] {	"templates/Harmartoma.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		//QI-331, QI-353, QI-352, QI-329
		subHierarchyECL = "<<362975008 |Degenerative disorder (disorder)|: 116676008 |Associated morphology (attribute)| = << 32693004 |Demyelination (morphologic abnormality)|";
		templateNames = new String[] {	"templates/Degenerative disorder.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 417893002|Deformity|"; //QI-279
		templateNames = new String[] {	"templates/Deformity - finding.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 417893002|Deformity|"; //QI-279
		templateNames = new String[] {	"templates/Deformity - disorder.json"};
		populateTemplates(subHierarchyECL, templateNames);

		//QI-373, QI-376, QI-400, QI-324, QI-337
		subHierarchyECL = "<<362975008 |Degenerative disorder (disorder)|: 116676008 |Associated morphology (attribute)| = << 46595003 |Deposition (morphologic abnormality)| ";
		templateNames = new String[] {	"templates/Degenerative disorder.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 276654001 |Congenital malformation (disorder)|"; //QI-287
		templateNames = new String[] {	"templates/Congenital Malformation.json"};
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 131148009|Bleeding|"; //QI-319
		templateNames = new String[] { "templates/Bleeding - disorder.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 131148009|Bleeding|"; //QI-319
		templateNames = new String[] { "templates/Bleeding - finding.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 74627003 | Diabetic complication (disorder) |"; //QI-426
		templateNames = new String[] {	"templates/Complication due to Diabetes Melitus.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<<  282100009 |Adverse reaction caused by substance (disorder)|"; //QI-406
		templateNames = new String[] {	"templates/Adverse Reaction.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 128462008 |Secondary malignant neoplastic disease (disorder)|"; //QI-382
		templateNames = new String[] {	"templates/Secondary malignant neoplasm.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 125671007 |Rupture (morphologic abnormality)|"; //QI-498
		templateNames = new String[] {	"templates/Traumatic rupture of joint.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<<  64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  708528005 |Narrowing (morphologic abnormality)|"; //QI-507
		templateNames = new String[] {	"templates/morphologies/Narrowing.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  79644001 |Pigment alteration (morphologic abnormality)|"; //QI-518
		templateNames = new String[] {	"templates/Pigmentation.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  68790008 |Amyloid deposition (morphologic abnormality)|"; //QI-225
		templateNames = new String[] {	"templates/Amyloid.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<<  64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)|  = <<  6574001 |Necrosis (morphologic abnormality)|"; //QI-530
		templateNames = new String[] {	"templates/Necrosis.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 372087000 |Primary malignant neoplasm (disorder)|"; //QI-383
		templateNames = new String[] {	"templates/neoplasm/primary malignant neoplasm.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 64572001 |Disease (disorder)|  :  116676008 |Associated morphology (attribute)| = << 107666005 |Fluid disturbance (morphologic abnormality)|"; //QI-525
		templateNames = new String[] {	"templates/morphologies/Fluid disturbance.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 282100009 |Adverse reaction caused by substance (disorder)|";
		templateNames = new String[] {	"templates/Adverse Reaction.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 419199007 |Allergy to substance (disorder)|";  //QI-609
		templateNames = new String[] {	"templates/Allergy to Substance.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 449735000 |Structural change due to ischemia (morphologic abnormality)|"; //QI-544
		templateNames = new String[] {	"templates/morphologies/Structural change due to ischemia.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 112639008 |Protrusion (morphologic abnormality)|"; //QI-556
		templateNames = new String[] {	"templates/morphologies/Protrusion.json"};
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "< 128139000 |Inflammatory disorder (disorder)| "; //QI-619
		templateNames = new String[] {	"templates/inflammatory/General inflammatory disorder.json" };
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 363346000 |Malignant neoplastic disease (disorder)|  MINUS (<< 372087000 |Primary malignant neoplasm (disorder)|  OR <<  128462008 |Secondary malignant neoplastic disease (disorder)| ) "; //QI-387
		templateNames = new String[] {	"templates/neoplasm/Malignant Neoplasm.json" };
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 64229006 |Traumatic lesion during delivery (disorder)| "; //QI-631
		templateNames = new String[] {	"templates/Traumatic lesion.json" };
		populateTemplates(subHierarchyECL, templateNames);
		
		subHierarchyECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 41010001 |Maturation defect (morphologic abnormality)|"; //QI-565
		templateNames = new String[] { "templates/morphologies/Maturation defect.json" };
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<<404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << 25723000 |Dysplasia (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Dysplasia.json" };
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "(<<404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = ( << 4147007 |Mass (morphologic abnormality)| MINUS <<416939005 |Proliferative mass (morphologic abnormality)| ) )";
		templateNames = new String[] { "templates/morphologies/Mass.json" };
		populateTemplates(subHierarchyECL, templateNames);

		subHierarchyECL = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)|  = <<  30217000 |Proliferation (morphologic abnormality)|";
		templateNames = new String[] { "templates/morphologies/Proliferation.json" };
		populateTemplates(subHierarchyECL, templateNames);

		populateTemplatesFromTS();
		super.init(run);
	}
	
	private void populateTemplatesFromTS() throws TermServerScriptException {
		try {
			Character id = 'A';
			for (ConceptTemplate ct : tsc.getAllTemplates()) {
				String templateName = ct.getName();
				//Skip all LOINC
				if (templateName.toUpperCase().contains("LOINC") || templateName.toUpperCase().contains("OUTDATED")) {
					continue;
				}
				
				LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
				Template template = new Template(id++, lt, templateName);
				info ("Loading template " + templateName + " from TS to run against subset: " + ct.getDomain());
				
				if (ct.getDomain() == null) {
					warn("TS template " + templateName + " is not saying what domain it applies to");
				}
				//Have we seen this subset before?
				List<Template> templates = domainTemplates.get(ct.getDomain());
				if (templates == null) {
					templates = new ArrayList<>();
					domainTemplates.put(ct.getDomain(), templates);
				}
				template.setSource(TEMPLATE_SERVICE);
				templates.add(template);
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load templates from TS", e);
		}
		
	}

	private void populateTemplates(String ecl, String[] templateNames) throws TermServerScriptException {
		
			List<Template> templates = new ArrayList<>();
			char id = 'A';
			for (int x = 0; x < templateNames.length; x++, id++) {
				info ("Loading template: " + templateNames[x]);
				try {
					ConceptTemplate ct = tsc.loadLocalConceptTemplate(templateNames[x]);
					LogicalTemplate lt = tsc.parseLogicalTemplate(ct.getLogicalTemplate());
					Template template = new Template(id, lt, templateNames[x]);
					template.setSource(QI);
					template.setDocumentation(ct.getDocumentation());
					templates.add(template);
				} catch (Exception e) {
					throw new TermServerScriptException("Unable to load " + ecl + " template " + templateNames[x] + " from local resources", e);
				}
			}
			domainTemplates.put(ecl, templates);
	}
}
