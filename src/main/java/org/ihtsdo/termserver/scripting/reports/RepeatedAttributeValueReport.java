package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

/**
 * Reports all terms that contain the specified text
 */
public class RepeatedAttributeValueReport extends TermServerReport {
	
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	String targetAttributeStr = "127489000"; // |Has active ingredient (attribute)|
	String matchText = "+"; 
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		RepeatedAttributeValueReport report = new RepeatedAttributeValueReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.runRepeatedAttributeValueReport();
		} catch (Exception e) {
			info("Failed to produce RepeatedAttributeValueReport Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void runRepeatedAttributeValueReport() throws TermServerScriptException {
		Collection<Concept> subHierarchy = gl.getConcept(subHierarchyStr).getDescendents(NOT_SET);
		info ("Validating all relationships");
		long issuesEncountered = 0;
		for (Concept c : subHierarchy) {
			if (c.isActive()) {
				issuesEncountered += checkforRepeatedAttributeValue(c, CharacteristicType.INFERRED_RELATIONSHIP);
				issuesEncountered += checkforRepeatedAttributeValue(c, CharacteristicType.STATED_RELATIONSHIP);
			}
		}
		addSummaryInformation("Concepts checked", subHierarchy.size());
		addSummaryInformation("Issues encountered", issuesEncountered);
	}

	private long checkforRepeatedAttributeValue(Concept c, CharacteristicType charType) throws TermServerScriptException {
		long issues = 0;
		Concept targetAttribute = gl.getConcept(targetAttributeStr);
		Set<Concept> valuesEncountered = new HashSet<Concept>();
		for (Relationship r : c.getRelationships(charType, targetAttribute, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				String msg = "Multiple " + charType + " instances of destination " + target;
				report(c, msg);
				issues++;
			}
			valuesEncountered.add(target);
		}
		return issues;
	}

	protected void report (Concept c, String issue) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						issue + QUOTE;
		writeToReportFile(line);
	}
	
}
