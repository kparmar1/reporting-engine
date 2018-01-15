package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports if concepts could potentially be promimal primitive modelled ie FD to top of hierarchy
 */
public class ProximatePrimitiveModellingPossibleReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	String publishedArchive;
	String[] hierarchies = {"64572001"}; //Disease (disorder)
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ProximatePrimitiveModellingPossibleReport report = new ProximatePrimitiveModellingPossibleReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportIntermediatePrimitives();
		} catch (Exception e) {
			println("Report failed due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportIntermediatePrimitives() throws TermServerScriptException {
		for (String hiearchySCTID : hierarchies) {

			int fdToTopCount = 0;
			int immedPrimParentCount = 0;
			int alreadyModelledCorrectlyCount = 0;
			int notImmediatePrimitiveCount = 0;
			
			Concept hierarchy = gl.getConcept(hiearchySCTID);
			Set<Concept> outsideSubHierarchy = hierarchy.getAncestors(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, true);
			Set<Concept> allHierarchy = hierarchy.getDescendents(NOT_SET, CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
			Set<Concept> allActiveFD = filterActiveFD(allHierarchy);
			println (hierarchy + " - " + allActiveFD.size() + "(FD) / " + allHierarchy.size() + "(Active)");
			
			for (Concept thisConcept : allActiveFD) {
				boolean alreadyModelledCorrectly = false;
				boolean fdToTop = false;
				boolean immedPrimParent = false;
				boolean notImmediatePrimitive = false;
				List<Concept>parents = thisConcept.getParents(CharacteristicType.STATED_RELATIONSHIP); 
				//If we have a single stated parent of disease, then we're modelled correctly
				if (parents.size() == 1 && parents.get(0).getConceptId().equals(hiearchySCTID)) {
					alreadyModelledCorrectlyCount++;
					alreadyModelledCorrectly = true;
				} else {
					//See if ancestors up to subhierarchy start (remove outside of that) are all fully defined
					Set<Concept> ancestors = thisConcept.getAncestors(NOT_SET, CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false);
					ancestors.removeAll(outsideSubHierarchy);
					if (allFD(ancestors)) {
						fdToTopCount++;
						fdToTop = true;
					} else {
						if (!allFD(parents)) {
							immedPrimParentCount ++;
							immedPrimParent = true;
						} else {
							notImmediatePrimitiveCount++;
							notImmediatePrimitive = true;
						}
					}
				}
				report(thisConcept, SnomedUtils.deconstructFSN(thisConcept.getFsn())[1], alreadyModelledCorrectly, fdToTop, immedPrimParent, notImmediatePrimitive);
			}
			println ("\tAlready modelled correctly: " + alreadyModelledCorrectlyCount);
			println ("\tFully defined to subhierarchy top: " + fdToTopCount);
			println ("\tHas immediate primitive parent: " + immedPrimParentCount);
			println ("\tNot-immediate primitive ancestor: " + notImmediatePrimitiveCount);
		}
		
	}

	private boolean allFD(Collection<Concept> concepts) {
		boolean allFD = true;
		for (Concept concept : concepts) {
			if (!concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				allFD = false;
				break;
			}
		}
		return allFD;
	}

	private Set<Concept> filterActiveFD(Set<Concept> fullSet) {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : fullSet ) {
			if (thisConcept.isActive() && thisConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}

	protected void report (Concept c, String semtag, boolean one, boolean two, boolean three, boolean four) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						semtag + QUOTE_COMMA +
						one + COMMA +
						two + COMMA + 
						three + COMMA +
						four;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-z")) {
				publishedArchive = args[++x];
			}
		}
		String hierarchiesStr = StringUtils.join(hierarchies,",");
		print ("Concepts in which Hierarchies? [" + hierarchiesStr + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			hierarchies = response.split(",");
		}
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, Sem_Tag, alreadyModelledCorrectly, FDToTop, immedPrimParent, notImmediatePrimitive");
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
	}
}