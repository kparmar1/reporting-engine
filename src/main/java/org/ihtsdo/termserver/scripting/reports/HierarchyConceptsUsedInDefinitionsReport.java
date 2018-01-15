package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts in a hierarchy
 * - or from a list
 * - optionally using a specific attribute
 * that are used in the definition of other concepts.
 * DRUGS-445
 */
public class HierarchyConceptsUsedInDefinitionsReport extends TermServerScript{
	
	String hierarchy = "49062001"; // |Device (physical object)|
	Concept attributeType = null; // Not currently needed because concepts coming from file
	Set<Concept> ignoredHierarchies;
	Set<String> alreadyReported = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		HierarchyConceptsUsedInDefinitionsReport report = new HierarchyConceptsUsedInDefinitionsReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportConceptsUsedInDefinition();
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportConceptsUsedInDefinition() throws TermServerScriptException {

		//Concept sourceHierarchy = gl.getConcept(hierarchy);
		//Set<Concept> sourceConcepts = filterActive(sourceHierarchy.getDescendents(NOT_SET));
		List<Component> sourceConcepts = processFile();
		
		println ("Active source concepts number " + sourceConcepts.size());
		Multiset<String> tags = HashMultiset.create();
		for (Concept thisConcept : filterActive(gl.getAllConcepts())) {
			//What hierarchy is this concept in?
			Concept thisHierarchy = getTopLevel(thisConcept);
			if (thisHierarchy == null) {
				debug ("Unable to determine top level hierarchy for: "  + thisConcept);
			}
			//Skip ignored hierarchies
			if (ignoredHierarchies.contains(thisHierarchy)) {
				continue;
			}
			//Ignore concepts checking themselves
			if (sourceConcepts.contains(thisConcept) || !thisConcept.isActive()) {
				continue;
			}
			for (Relationship thisRelationship : thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)){
				//Does this relationship use one of our source concepts as a target?
				if (sourceConcepts.contains(thisRelationship.getTarget())) {
					//Only report each source / hierarchy / attribute combination once
					String source_hierarchy_attribute = thisRelationship.getTarget().getConceptId() + "_" +  thisHierarchy.getConceptId() + "_" + thisRelationship.getType().getConceptId();
					if (true /*!alreadyReported.contains(source_hierarchy_attribute)*/) {
						report (thisRelationship.getTarget(), thisConcept, thisRelationship.getType());
						tags.add(SnomedUtils.deconstructFSN(thisConcept.getFsn())[1]);
						alreadyReported.add(source_hierarchy_attribute);
						break;
					}
				}
			}
		}
		
		for (String tag : tags.elementSet()) {
			println ("\t" + tag + ": " + tags.count(tag));
		}
	}

	private Concept getTopLevel(Concept thisConcept) throws TermServerScriptException {
		//Is this itself a top level concept?
		if (thisConcept.getDepth() == 1) {
			return thisConcept;
		}
		
		Set<Concept> ancestors = thisConcept.getAncestors(NOT_SET);
		for (Concept ancestor : ancestors) {
			if (ancestor.getDepth() == 1) {
				return ancestor;
			}
		}
		return null;
	}

	private Set<Concept> filterActive(Collection<Concept> collection) {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : collection ) {
			if (thisConcept.isActive()) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}

	protected void report (Concept c, Concept usedIn, Concept via) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						usedIn + QUOTE_COMMA_QUOTE +
						via + QUOTE_COMMA +
						usedIn.getDefinitionStatus();
		
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		/*print ("Concepts in which Hierarchy? [" + hierarchy + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			hierarchy = response;
		}*/
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, UsedToDefine, InAttribute, Defn_Status");
		
		ignoredHierarchies = new HashSet<>();
		ignoredHierarchies.add (gl.getConcept("105590001")); // |Substance (substance)|
		ignoredHierarchies.add (gl.getConcept("373873005")); // |Pharmaceutical / biologic product (product)|
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
	}
}