package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.schedule.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * Update: Adding a 2nd report to determine how many sufficiently defined concepts are affected by an IP
 * */
public class InitialAnalysis extends TermServerReport implements ReportClass {
	
	Concept subHierarchyStart;
	Set<Concept> subHierarchy;
	public Map<Concept, Integer> intermediatePrimitives;
	public Map<Concept, Integer> attributeUsage;
	public Map<Concept, Concept> attributeExamples;
	String[] blankColumns = new String[] {"","","",""};
	
	public InitialAnalysis(TermServerReport owner) {
		if (owner!=null) {
			setReportManager(owner.getReportManager());
		}
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		InitialAnalysis report = new InitialAnalysis(null);
		try {
			report.additionalReportColumns = "FSN, Proximal Primitive Parent, is Intermediate, Defn Status, Stated Attributes, Stated Role Groups, Inferred Role Groups, Stated Parents";
			report.secondaryReportColumns = "FSN, Can Be Sufficiently Defined (1=yes 0=no), JIRA, Comments, Authoring Task, In Subhierarchy,Total SDs affected, SD Concepts in subhierarchy, Total Primitive Concepts affected, Primitive Concept in SubHierarchy";
			report.tertiaryReportColumns = "FSN, Concepts Using Type, Example";
			report.getReportManager().setNumberOfDistinctReports(3);
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.postInit(null);
			report.runReport();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	public void runReport() throws TermServerScriptException {
		reportConceptsAffectedByIntermediatePrimitives();
		reportTotalFDsUnderIPs();
		reportAttributeUsageCounts();
	}
	
	public void postInit(String subHierarchyStr) throws TermServerScriptException {
		try {
			if (subHierarchy == null) {
				//setSubHierarchy("46866001");	//       |Fracture of lower limb (disorder)|
				//setSubHierarchy("125605004");	// QI-2  |Fracture of bone (disorder)|
				//setSubHierarchy("128294001");	// QI-8  |Chronic inflammatory disorder (disorder)|
				//setSubHierarchy("126537000");	// QI-11 |Neoplasm of bone (disorder)|
				//setSubHierarchy("34014006");	// QI-12 |Viral disease
				//setSubHierarchy("87628006");	// QI-13 |Bacterial infectious disease (disorder)|
				//setSubHierarchy("95896000");	// QI-18 |Protozoan infection (disorder)|
				//setSubHierarchy("52515009");	// QI-22 |Hernia of abdominal cavity|
				//setSubHierarchy("125666000");	// QI-22 |Burn (disorder)|
				//setSubHierarchy("74627003");	// QI-38 |Diabetic complication (disorder)|
				//setSubHierarchy("283682007");	// QI-35 |Bite - wound (disorder)|
				//setSubHierarchy("8098009");	// QI-40 |Sexually transmitted infectious disease (disorder)|
				//setSubHierarchy("3723001");	// QI-42 |Arthritis|
				//setSubHierarchy("276654001");	// QI-43 |Congenital malformation (disorder)| );
				//setSubHierarchy("3218000");	//QI-46 |Mycosis (disorder)|
				//setSubHierarchy("17322007");	//QI-49 |Disease caused by parasite|
				//setSubHierarchy("416462003");  //QI-50 |Wound (disorder)
				//setSubHierarchy("125643001");  //QI-51 |Open wound|
				setSubHierarchy("416886008");  //QI-52 |Closed wound|
			} else {
				setSubHierarchy(subHierarchyStr);
			}
			ReportSheetManager.targetFolderId = "1m7MVhMePldYrNjOvsE_WTAYcowZ4ps50"; //Team Drive: Content Reporting Artefacts / QI / Initial Analysis
			getReportManager().setReportName(getReportName());
			getReportManager().setTabNames(new String[] {	"Attribute Usage",
															"IPs with Counts",
															"Cncepts in Subhierarchy with PPPs" });
			getReportManager().initialiseReportFiles( new String[] {headers + additionalReportColumns, headers + secondaryReportColumns, headers + tertiaryReportColumns});
		} catch (Exception e) {
			throw new TermServerScriptException ("Unable to initialise " + this.getClass().getSimpleName(), e);
		}
	}
	
	public String getReportName() {
		try {
			return subHierarchyStart.getPreferredSynonym() + " - Intermediate Primitives";
		} catch (TermServerScriptException e) {
			return subHierarchyStart.getConceptId() +  " - Intermediate Primitives";
		}
	}
	
	public void setSubHierarchy(String subHierarchyStr) throws TermServerScriptException {
		subHierarchyStart = gl.getConcept(subHierarchyStr);
		this.subHierarchy = subHierarchyStart.getDescendents(NOT_SET);
		intermediatePrimitives = new HashMap<>();
		attributeUsage = new HashMap<>();
		attributeExamples = new HashMap<>();
	}
	

	public void setSubHierarchy(Set<Concept> concepts) {
		this.subHierarchy = concepts;
		intermediatePrimitives = new HashMap<>();
		attributeUsage = new HashMap<>();
		attributeExamples = new HashMap<>();
	}

	public void reportConceptsAffectedByIntermediatePrimitives() throws TermServerScriptException {
		for (Concept c : this.subHierarchy) {
			//We're only interested in fully defined concepts
			//Update:  OR leaf concepts 
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) || 
				(c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE) && 
					c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size() == 0)) {
				List<Concept> proxPrimParents = determineProximalPrimitiveParents(c);
				//Do those parents themselves have sufficiently defined ancestors ie making them intermediate primitives
				for (Concept thisPPP : proxPrimParents) {
					boolean isIntermediate = false;
					if (containsFdConcept(ancestorsCache.getAncestors(thisPPP))) {
						isIntermediate = true;
						if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
							incrementSummaryInformation("Intermediate Primitives reported on SD concepts");
						} else {
							incrementSummaryInformation("Intermediate Primitives reported on Primitive leaf concepts");
						}
						incrementSummaryInformation(thisPPP.toString());
						if (!intermediatePrimitives.containsKey(thisPPP)) {
							incrementSummaryInformation("Unique Intermediate Primitives Reported");
						}
						intermediatePrimitives.merge(thisPPP, 1, Integer::sum);
					} else {
						incrementSummaryInformation("Safely modelled count");
					}
					
					if (!quiet) {
						report (c, thisPPP.toString(), 
								isIntermediate?"Yes":"No",
								c.getDefinitionStatus(),
								Integer.toString(countAttributes(c, CharacteristicType.STATED_RELATIONSHIP)),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false).size()),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false).size()),
								getParentsWithDefnStatus(c)
								);
					}
				}
				incrementSummaryInformation("FD Concepts checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	}

	private String getParentsWithDefnStatus(Concept c) {
		StringBuffer sb = new StringBuffer();
		boolean isFirst = true;
		for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
			if (!isFirst) {
				sb.append(", ");
			} else { 
				isFirst = false;
			}
			sb.append("[")
			.append(SnomedUtils.translateDefnStatus(p.getDefinitionStatus()))
			.append("] ")
			.append(p.toString());
		}
		return sb.toString();
	}

	private int countAttributes(Concept c, CharacteristicType charType) {
		int attributes = 0;
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributes++;
			}
		}
		return attributes;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}
	
	private void reportTotalFDsUnderIPs() throws TermServerScriptException {
		intermediatePrimitives.entrySet().stream()
			.sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
			.forEach(k -> {
				try {
					reportTotalFDsUnderIP(k.getKey());
				} catch (TermServerScriptException e) {
					e.printStackTrace();
				}
			});
	}
	
	private void reportTotalFDsUnderIP(Concept intermediatePrimitive) throws TermServerScriptException {
		int totalFDsUnderIP = 0;
		int fdsInSubHierarchy = 0;
		int totalPrimitiveConceptsUnderIP = 0;
		int totalPrimitiveConceptsUnderIPInSubHierarchy = 0;
		int IPinSubHierarchy = descendantsCache.getDescendentsOrSelf(this.subHierarchyStart).contains(intermediatePrimitive) ? 1 : 0;
		for (Concept c : descendantsCache.getDescendentsOrSelf(intermediatePrimitive)) {
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				totalFDsUnderIP++;
				if (this.subHierarchy.contains(c)) {
					fdsInSubHierarchy++;
				}
			} else {
				totalPrimitiveConceptsUnderIP++;
				if (this.subHierarchy.contains(c)) {
					totalPrimitiveConceptsUnderIPInSubHierarchy++;
				}
			}
		}
		report (SECONDARY_REPORT, intermediatePrimitive, blankColumns, IPinSubHierarchy, totalFDsUnderIP, fdsInSubHierarchy, totalPrimitiveConceptsUnderIP, totalPrimitiveConceptsUnderIPInSubHierarchy);
	}
	
	
	private void reportAttributeUsageCounts() throws TermServerScriptException {
		//For every concept in the subhierarchy, get the attribute types used, and an example
		for (Concept c : this.subHierarchy) {
			for (Concept type : getAttributeTypes(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
				attributeExamples.put(type, c);
				attributeUsage.merge(type, 1, Integer::sum);
			}
		}
		
		attributeUsage.entrySet().stream()
			.sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
			.forEach(k -> reportSafely (TERTIARY_REPORT, k.getKey(), k.getValue(), attributeExamples.get(k.getKey())));
	}

	private Set<Concept> getAttributeTypes(Concept c, CharacteristicType charType) {
		return c.getRelationships(charType, ActiveState.ACTIVE)
				.stream()
				.map(r -> r.getType())
				.collect(Collectors.toSet());
	}

}
