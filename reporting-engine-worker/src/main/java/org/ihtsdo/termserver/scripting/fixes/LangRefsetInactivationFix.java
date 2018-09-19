package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;

import java.util.*;

/**
 * Fix script to inactivate the language reference sets of inactive descriptions found in a validation report
 */
public class LangRefsetInactivationFix extends TermServerScript implements RF2Constants {

	public static void main(String[] args) throws Exception {
		LangRefsetInactivationFix fix = new LangRefsetInactivationFix();
		fix.init(args);
		fix.fixAll();
	}
		
	public void fixAll () throws Exception {
		String validationReportUrl = url + "snowowl/ihtsdo-sca/projects/" + project + "/validation";
		info(validationReportUrl);

		Set<Concept> concepts = new LinkedHashSet<>();

		JSONObject validationReport = resty.json(validationReportUrl).toObject();
		JSONObject report = validationReport.getJSONObject("report");
		JSONObject rvfValidationResult = report.getJSONObject("rvfValidationResult");
		JSONObject sqlTestResult = rvfValidationResult.getJSONObject("sqlTestResult");
		JSONArray assertionsFailed = sqlTestResult.getJSONArray("assertionsFailed");
		for (int i = 0; i < assertionsFailed.length(); i++) {
			JSONObject assertionFailed = assertionsFailed.getJSONObject(i);
			if (assertionFailed.getString("assertionText").equals("Members are inactive for inactive descriptions in the language refset snapshot file.")) {
				JSONArray firstNInstances = assertionFailed.getJSONArray("firstNInstances");
				for (int j = 0; j < firstNInstances.length(); j++) {
					JSONObject jsonObject = firstNInstances.getJSONObject(j);
					String conceptId = ((Long)jsonObject.getLong("conceptId")).toString();
					concepts.add(new Concept(conceptId, NA));
				}
			}
		}

		if (!concepts.isEmpty()) {
			info("Concepts in the report that need fixing - " + concepts);

			String branchPath = "MAIN/" + project;
			for (Concept concept : concepts) {
				doFix(concept, branchPath);
			}
		} else {
			info("No lang refset failures found in report " + validationReportUrl);
		}
	}

	public void doFix(Concept concept, String branchPath) throws TermServerScriptException {
		try{
			JSONObject conceptObj = tsClient.getConcept(concept.getConceptId(), branchPath).object();
			boolean fixed = false;
			JSONArray descriptions = conceptObj.getJSONArray("descriptions");
			for (int i = 0; i < descriptions.length(); i++) {
				JSONObject description = descriptions.getJSONObject(i);
				if (!description.getBoolean("active")) {
					if (description.has("acceptabilityMap")) {
						JSONObject acceptabilityMap = description.getJSONObject("acceptabilityMap");
						if (acceptabilityMap != null && acceptabilityMap.length() > 0) {
							description.remove("acceptabilityMap");
							description.putOnce("acceptabilityMap", new JSONObject());
							fixed = true;
						}
					}
				}
			}
			if (fixed) {
				info("Fixing " + concept.getConceptId());
				tsClient.updateConcept(conceptObj, branchPath);
			} else {
				info("No issue with " + concept.getConceptId());
			}
		}catch (Exception e) {
			throw new TermServerScriptException("Failed to fix issue", e);
		}
		
	}

	@Override
	public String getScriptName() {
		return "LangRefsetInactivationFix";
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}