package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;

/**
 * Reports all terms that contain the specified text
 */
public class ValidateTaxonomyIntegrity extends TermServerScript{
	
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String matchText = "+"; 
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ValidateTaxonomyIntegrity report = new ValidateTaxonomyIntegrity();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.validateTaxonomyIntegrity();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
			for (String err : report.criticalErrors) {
				info (err);
			}
		}
	}

	private void validateTaxonomyIntegrity() throws TermServerScriptException {
		Collection<Concept> concepts = gl.getAllConcepts();
		info ("Validating all concepts");
		long issuesEncountered = 0;
		long conceptsValidated = 0;
		for (Concept c : concepts) {
			//issuesEncountered += validateRelationships(c, CharacteristicType.INFERRED_RELATIONSHIP);
			//issuesEncountered += validateRelationships(c, CharacteristicType.STATED_RELATIONSHIP);
			//issuesEncountered += validateRelationships(c, CharacteristicType.ADDITIONAL_RELATIONSHIP);
		
			issuesEncountered += validateFsnAcceptability (c);
			if (++conceptsValidated%10000==0) {
				//println ("" + conceptsValidated);
			}
		}
		addSummaryInformation("Concepts checked", concepts.size());
		addSummaryInformation("Issues encountered", issuesEncountered);
	}

	//Confirm that the active FSN has 1 x US acceptability == preferred
	private long validateFsnAcceptability(Concept c) throws TermServerScriptException {
		int issues = 0;
		List<Description> fsns = c.getDescriptions(Acceptability.BOTH, DescriptionType.FSN, ActiveState.ACTIVE);
		if (fsns.size() != 1) {
			String msg = "Concept has " + fsns.size() + " active fsns";
			report (c, msg);
			issues++;
		} else {
			String msg = "[" + fsns.get(0).getDescriptionId() + "]: ";
			List<LangRefsetEntry> langRefEntries = fsns.get(0).getLangRefsetEntries(ActiveState.BOTH, US_ENG_LANG_REFSET);
			if (langRefEntries.size() != 1) {
				if (langRefEntries.size() == 2) {
					List<LangRefsetEntry> uslangRefEntries = fsns.get(0).getLangRefsetEntries(ActiveState.BOTH, US_ENG_LANG_REFSET, SCTID_US_MODULE);
					List<LangRefsetEntry> corelangRefEntries = fsns.get(0).getLangRefsetEntries(ActiveState.BOTH, US_ENG_LANG_REFSET, SCTID_CORE_MODULE);
					if (uslangRefEntries.size() > 1 || corelangRefEntries.size() >1) {
						msg += "Two acceptabilities in the same module";
						report (c, msg);
						issues++;
					} else {
						if (!uslangRefEntries.get(0).isActive() && corelangRefEntries.get(0).isActive() ) {
							long usET = Long.parseLong(uslangRefEntries.get(0).getEffectiveTime());
							long coreET = Long.parseLong(corelangRefEntries.get(0).getEffectiveTime());
							msg += "US langrefset entry inactivated " + (usET > coreET ? "after":"before") + " core row activated - " + usET;
							report (c, msg);
							issues++;
						} else {
							msg += "Unexpected configuration of us and core lang refset entries";
							report (c, msg);
							issues++;
						}
					}
				} else {
					msg += "FSN has " + langRefEntries.size() + " US acceptability values.";
					report (c, msg);
					issues++;
				}
			} else if (!langRefEntries.get(0).getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
				msg += "FSN has an acceptability that is not Preferred.";
				report (c, msg);
				issues++;
			} else if (!langRefEntries.get(0).isActive()) {
				msg += "FSN's US acceptability is inactive.";
				report (c, msg);
				issues++;
			}
		}
		return issues;
	}

	private long validateRelationships(Concept c, CharacteristicType charType) {
		long issues = 0;
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			//Check for a Definition Status since it's the only thing that's only provided 
			//by the concept file
			if (r.getSource().getDefinitionStatus() == null ) {
				String msg = "Non-existent source (" + r.getSourceId() + " - " + r.getRelationshipId() + ") in " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			} else if (!r.getSource().isActive()) {
				String msg = "Inactive source (" + r.getSourceId() + " - " + r.getRelationshipId() + ") in " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			}
			
			if (r.getType().getDefinitionStatus() == null) {
				String msg = "Non-existent Type (" + r.getType().getConceptId() + " - " + r.getRelationshipId() + ") in " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			} else if (!r.getType().isActive()) {
				String msg = "Inactive Type (" + r.getType().getConceptId() + " - " + r.getRelationshipId() + ") in " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			}
			
			if (r.getTarget().getDefinitionStatus() == null) {
				String msg = "Non-existent target (" + r.getTarget().getConceptId() + " - " + r.getRelationshipId() + ") in " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			} else if (!r.getTarget().isActive()) {
				String msg = "Inactive target (" + r.getTarget().getConceptId() + " - " + r.getRelationshipId() + ") in " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			}
		}
		
		//Also check inactive relationships for non-existent concepts
		for (Relationship r : c.getRelationships(charType, ActiveState.INACTIVE)) {
			//Check for an FSN to ensure Concept fully exists
			if (r.getSource().getDefinitionStatus() == null) {
				String msg = "Non-existent source (" + r.getSourceId() + " - " + r.getRelationshipId() + ") in inactive " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			} 
			
			if (r.getType().getDefinitionStatus() == null) {
				String msg = "Non-existent Type (" + r.getType().getConceptId() + " - " + r.getRelationshipId() + ") in inactive " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			}
			
			if (r.getTarget().getDefinitionStatus() == null) {
				String msg = "Non-existent target (" + r.getTarget().getConceptId() + " - " + r.getRelationshipId() + ") in inactive " + charType + " relationship: " + r;
				report (c, msg);
				issues++;
			}
		}
		return issues;
	}

	protected void report (Concept c, String issue) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						issue + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		info ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, Issue");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
