package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.drugs.Ingredient;
import org.ihtsdo.termserver.scripting.reports.TermContainsXReport;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

public class ValidateDrugModeling extends TermServerReport implements ReportClass {
	
	Concept [] solidUnits = new Concept [] { PICOGRAM, NANOGRAM, MICROGRAM, MILLIGRAM, GRAM };
	Concept [] liquidUnits = new Concept [] { MILLILITER, LITER };
	String[] semTagHiearchy = new String[] { "(product)", "(medicinal product)", "(medicinal product form)", "(clinical drug)" };
	
	private static final String[] badWords = new String[] { "preparation", "agent", "+"};
	private static final String remodelledDrugIndicator = "Product containing";
	private static final String BOSS_FAIL = "BoSS failed to relate to ingredient";
	
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(ValidateDrugModeling.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3";  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";   //DRUGS-267
		super.init(run);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.DRUGS),
						"Drugs validation",
						"This report checks for a number of potential inconsistencies in the Medicinal Product hierarchy.",
						params);
	}
	
	public void runJob() throws TermServerScriptException {
		validateDrugsModeling();
		validateSubstancesModeling();
	}
	
	private void validateDrugsModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = MEDICINAL_PRODUCT.getDescendents(NOT_SET);
		ConceptType[] allDrugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.CLINICAL_DRUG };
		ConceptType[] cds = new ConceptType[] { ConceptType.CLINICAL_DRUG };  //DRUGS-267
		initialiseSummaryInformation(BOSS_FAIL);
		
		for (Concept concept : subHierarchy) {
			DrugUtils.setConceptType(concept);
			
			if (concept.getConceptId().equals("769993004")) {
				//debug ("Check here");
			}
			
			// DRUGS-281, DRUGS-282
			validateIngredientsInFSN(concept, allDrugTypes);  
			
			// DRUGS-267
			validateIngredientsAgainstBoSS(concept);
			
			// DRUGS-296 
			if (concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) && 
				concept.getParents(CharacteristicType.STATED_RELATIONSHIP).get(0).equals(MEDICINAL_PRODUCT)) {
				validateStatedVsInferredAttributes(concept, HAS_ACTIVE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(concept, HAS_PRECISE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(concept, HAS_MANUFACTURED_DOSE_FORM, allDrugTypes);
			}
			
			//DRUGS-518
			if (SnomedUtils.isConceptType(concept, cds)) {
				checkForInferredGroupsNotStated(concept);
			}
			
			//DRUGS-51?
			if (concept.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				validateConcentrationStrength(concept);
			}
			
			// DRUGS-288
			validateAttributeValueCardinality(concept, HAS_ACTIVE_INGRED);
			
			//DRUGS-93
			checkForBadWords(concept);  
			
			//DRUGS-629
			checkForSemTagViolations(concept);
		}
		info ("Drugs validation complete.");
	}

	/**
	 * For Pattern 2A Drugs (liquids) where we have both a presentation strength and a concentration
	 * report these values and confirm if the units change between the two, and if the calculation is correct
	 * @param concept
	 * @return
	 * @throws TermServerScriptException 
	 */
	private void validateConcentrationStrength(Concept c) throws TermServerScriptException {
		//For each group, do we have both a concentration and a presentation?
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			Ingredient i = DrugUtils.getIngredientDetails(c, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
			if (i.presStrength != null && i.concStrength != null) {
				boolean unitsChange = false;
				boolean issueDetected = false;
				//Does the unit change between presentation and strength?
				if (!i.presNumeratorUnit.equals(i.concNumeratorUnit) || ! i.presDenomUnit.equals(i.concDenomUnit)) {
					unitsChange = true;
				}
				
				//Normalise the numbers
				BigDecimal presStrength = new BigDecimal(DrugUtils.getConceptAsNumber(i.presStrength));
				BigDecimal concStrength = new BigDecimal(DrugUtils.getConceptAsNumber(i.concStrength));
				if (!i.presNumeratorUnit.equals(i.concNumeratorUnit)) {
					concStrength = concStrength.multiply(calculateUnitFactor (i.presNumeratorUnit, i.concNumeratorUnit));
				}
				
				BigDecimal presDenomQuantity = new BigDecimal (DrugUtils.getConceptAsNumber(i.presDenomQuantity));
				BigDecimal concDenomQuantity = new BigDecimal (DrugUtils.getConceptAsNumber(i.concDenomQuantity));
				if (!i.presDenomUnit.equals(i.concDenomUnit)) {
					concDenomQuantity = concDenomQuantity.multiply(calculateUnitFactor (i.presDenomUnit, i.concDenomUnit));
				}
				
				//Do they give the same ratio when we divide
				BigDecimal presRatio = presStrength.divide(presDenomQuantity, 3, RoundingMode.HALF_UP);
				BigDecimal concRatio = concStrength.divide(concDenomQuantity, 3, RoundingMode.HALF_UP);
				
				if (!presRatio.equals(concRatio)) {
					issueDetected = true;
					incrementSummaryInformation(ISSUE_COUNT);
				}
				report (c, i.substance, i.presToString(), i.concToString(), unitsChange, issueDetected, issueDetected? presRatio + " vs " + concRatio : "");
			}
		}
	}

	private BigDecimal calculateUnitFactor(Concept unit1, Concept unit2) {
		BigDecimal factor = new BigDecimal(1);  //If we don't work out a different, multiple so strength unchanged
		//Is it a solid or liquid?
		
		int unit1Idx = ArrayUtils.indexOf(solidUnits, unit1);
		int unit2Idx = -1;
		
		if (unit1Idx != -1) {
			unit2Idx = ArrayUtils.indexOf(solidUnits, unit2);
		} else {
			//Try liquid
			unit1Idx = ArrayUtils.indexOf(liquidUnits, unit1);
			if (unit1Idx != -1) {
				unit2Idx = ArrayUtils.indexOf(liquidUnits, unit2);
			}
		}
		
		if (unit1Idx != -1) {
			if (unit2Idx == -1) {
				throw new IllegalArgumentException("Units lost between " + unit1 + " and " + unit2 );
			} else if (unit1Idx == unit2Idx) {
				throw new IllegalArgumentException("Units previously detected different between " + unit1 + " and " + unit2 );
			}
			
			factor = unit1Idx > unit2Idx ? new BigDecimal(0.001D) : new BigDecimal(1000) ; 
		}
		return factor;
	}

	private void validateSubstancesModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = SUBSTANCE.getDescendents(NOT_SET);
		for (Concept concept : subHierarchy) {
			DrugUtils.setConceptType(concept);
			validateDisposition(concept);
			checkForBadWords(concept);  //DRUGS-93
		}
		info ("Substances validation complete.");
	}
	
	//Ensure that all stated dispositions exist as inferred, and visa-versa
	private void validateDisposition(Concept concept) throws TermServerScriptException {
		validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.STATED_RELATIONSHIP);

		//If this concept has one or more hasDisposition attributes, check if the inferred parent has the same.
		if (concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_DISPOSITION, ActiveState.ACTIVE).size() > 0) {
			validateAttributeViewsMatch (concept, HAS_DISPOSITION, CharacteristicType.INFERRED_RELATIONSHIP);
			checkForOddlyInferredParent(concept, HAS_DISPOSITION);
		}
	}

	private void validateAttributeViewsMatch(Concept concept,
			Concept attributeType,
			CharacteristicType fromCharType) throws TermServerScriptException {
		//Check that all relationships of the given type "From" match "To"
		CharacteristicType toCharType = fromCharType.equals(CharacteristicType.STATED_RELATIONSHIP)? CharacteristicType.INFERRED_RELATIONSHIP : CharacteristicType.STATED_RELATIONSHIP;
		for (Relationship r : concept.getRelationships(fromCharType, attributeType, ActiveState.ACTIVE)) {
			if (findRelationship(concept, r, toCharType) == null) {
				String msg = fromCharType.toString() + " has no counterpart";
				report (concept, msg, r.toString());
				incrementSummaryInformation(ISSUE_COUNT);
			}
		}
	}

	/**
	 * list of concepts that have an inferred parent with a stated attribute 
	 * that is not the same as the that of the concept.
	 * @return
	 * @throws TermServerScriptException 
	 */
	private void checkForOddlyInferredParent(Concept concept, Concept attributeType) throws TermServerScriptException {
		//Work through inferred parents
		for (Concept parent : concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//Find all STATED attributes of interest
			for (Relationship parentAttribute : parent.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
				
				//Does our original concept have that attribute?  Report if not.
				if (null == findRelationship(concept, parentAttribute, CharacteristicType.STATED_RELATIONSHIP)) {
					String msg ="Inferred parent has a stated attribute not stated in child.";
					report (concept, msg, parentAttribute.toString());
					incrementSummaryInformation(ISSUE_COUNT);
				}
			}
		}
	}

	private Relationship findRelationship(Concept concept, Relationship exampleRel, CharacteristicType charType) {
		//Find the first relationship matching the type, target and activeState
		for (Relationship r : concept.getRelationships(charType, exampleRel.getType(),  ActiveState.ACTIVE)) {
			if (r.getTarget().equals(exampleRel.getTarget())) {
				return r;
			}
		}
		return null;
	}

	/*
	Need to identify and update:
		FSN beginning with "Product containing" that includes any of the following in any active description:
		agent
		+
		preparation
		product (except in the semantic tag)
	 */
	private void checkForBadWords(Concept concept) throws TermServerScriptException {
		//Check if we're product containing and then look for bad words
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtils.deconstructFSN(term)[0];
			}
			for (String badWord : badWords ) {
				if (term.contains(badWord)) {
					//Exception, MP PT will finish with word "product"
					if (concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) && d.isPreferred() && badWord.equals("product")) {
						continue;
					} else {
						String msg = "Term contains bad word: " + badWord;
						incrementSummaryInformation(ISSUE_COUNT);
						report (concept, msg, concept.getFsn().contains(remodelledDrugIndicator), d.toString());
					}
				}
			}
		}
	}

	private void validateStatedVsInferredAttributes(Concept concept,
			Concept attributeType, ConceptType[] drugTypes) throws TermServerScriptException {
		if (drugTypes==null || SnomedUtils.isConceptType(concept, drugTypes)) {
			List<Relationship> statedAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			List<Relationship> infAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			if (statedAttributes.size() != infAttributes.size()) {
				String msg = "Cardinality mismatch stated vs inferred " + attributeType;
				String data = "(s" + statedAttributes.size() + " i" + infAttributes.size() + ")";
				incrementSummaryInformation(ISSUE_COUNT);
				report (concept, msg, data);
			} else {
				for (Relationship statedAttribute : statedAttributes) {
					boolean found = false;
					for (Relationship infAttribute : infAttributes) {
						if (statedAttribute.getTarget().equals(infAttribute.getTarget())) {
							found = true;
							break;
						}
					}
					if (!found) {
						String msg = "Stated " + statedAttribute.getType() + " is not present in inferred view";
						String data = statedAttribute.toString();
						incrementSummaryInformation(ISSUE_COUNT);
						report (concept, msg, data);
					}
				}
			}
		}
	}

	private void validateIngredientsAgainstBoSS(Concept concept) throws TermServerScriptException {
		List<Relationship> bossAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_BOSS, ActiveState.ACTIVE);
		//Check BOSS attributes against active ingredients - must be in the same relationship group
		List<Relationship> ingredientRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE);
		for (Relationship bRel : bossAttributes) {
			incrementSummaryInformation("BoSS attributes checked");
			boolean matchFound = false;
			Concept boSS = bRel.getTarget();
			for (Relationship iRel : ingredientRels) {
				Concept ingred = iRel.getTarget();
				if (bRel.getGroupId() == iRel.getGroupId()) {
					boolean isSelf = boSS.equals(ingred);
					boolean isSubType = gl.getDescendantsCache().getDescendents(boSS).contains(ingred);
					boolean isModificationOf = DrugUtils.isModificationOf(ingred, boSS);
					
					if (isSelf || isSubType || isModificationOf) {
						matchFound = true;
						if (isSubType) {
							incrementSummaryInformation("Active ingredient is a subtype of BoSS");
							String issue = "Active ingredient is a subtype of BoSS.  Expected modification.";
							incrementSummaryInformation(ISSUE_COUNT);
							report (concept, issue, ingred, boSS);
						} else if (isModificationOf) {
							incrementSummaryInformation("Valid Ingredients as Modification of BoSS");
						} else if (isSelf) {
							incrementSummaryInformation("BoSS matches ingredient");
						}
					}
				}
			}
			if (!matchFound) {
				String issue = "Basis of Strength not equal or subtype of active ingredient, neither is active ingredient a modification of the BoSS";
				report (concept, issue, null, boSS);
				incrementSummaryInformation(BOSS_FAIL);
				incrementSummaryInformation(ISSUE_COUNT);
			}
		}
	}

	private void validateIngredientsInFSN(Concept c, ConceptType[] drugTypes) throws TermServerScriptException {
		//Only check FSN for certain drug types (to be expanded later)
		if (!SnomedUtils.isConceptType(c, drugTypes)) {
			incrementSummaryInformation("Concepts ignored - wrong type");
		}
		incrementSummaryInformation("Concepts validated to ensure ingredients correct in FSN");
		Description currentFSN = c.getFSNDescription();
		termGenerator.setQuiet(true);
		Concept clone = c.clone();
		termGenerator.ensureDrugTermsConform(null, clone, CharacteristicType.STATED_RELATIONSHIP);
		Description proposedFSN = clone.getFSNDescription();
		
		if (!currentFSN.getTerm().equals(proposedFSN.getTerm())) {
			String issue = "FSN did not match expected pattern";
			String differences = findDifferences (currentFSN.getTerm(), proposedFSN.getTerm());
			report (c, issue, proposedFSN.getTerm(), differences);
			incrementSummaryInformation(ISSUE_COUNT);
		}
	}
	
	private String findDifferences(String actual, String expected) {
		String differences = "";
		//For each word, see if it exists in the other 
		String[] actuals = actual.split(" ");
		String[] expecteds = expected.split(" ");
		int maxLoop = (actuals.length>expecteds.length)?actuals.length:expecteds.length;
		for (int x=0; x < maxLoop; x++) {
			if (actuals.length > x) {
				if (! expected.contains(actuals[x])) {
					differences += actuals[x] + " ";
				}
			}
			
			if (expecteds.length > x) {
				if (! actual.contains(expecteds[x])) {
					differences += expecteds[x] + " ";
				}
			}
		}
		return differences;
	}

	private void validateAttributeValueCardinality(Concept concept, Concept activeIngredient) throws TermServerScriptException {
		checkforRepeatedAttributeValue(concept, CharacteristicType.INFERRED_RELATIONSHIP, activeIngredient);
		checkforRepeatedAttributeValue(concept, CharacteristicType.STATED_RELATIONSHIP, activeIngredient);
	}

	private void checkforRepeatedAttributeValue(Concept c, CharacteristicType charType, Concept activeIngredient) throws TermServerScriptException {
		Set<Concept> valuesEncountered = new HashSet<Concept>();
		for (Relationship r : c.getRelationships(charType, activeIngredient, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				String msg = "Multiple " + charType + " instances of active ingredient";
				report(c, msg, target.toString());
				incrementSummaryInformation(ISSUE_COUNT);
			}
			valuesEncountered.add(target);
		}
	}
	
	private void report (Concept c, String data, String detail) throws TermServerScriptException {
		super.report(c, c.getConceptType(), data, detail);
	}
	
	private int checkForInferredGroupsNotStated(Concept c) throws TermServerScriptException {
		RelationshipGroup unmatchedGroup = null;
		Concept playsRole = gl.getConcept("766939001 |Plays role (attribute)|");
		//Work through all inferred groups and see if they're subsumed by a stated group
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);
		
		nextGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			//We expect "Plays Role" to be inherited, so filter those out 
			inferredGroup = filter(playsRole, inferredGroup);
			//Can we find a matching (or less specific but otherwise matching) stated group?
			for (RelationshipGroup statedGroup : statedGroups) {
				statedGroup = filter(playsRole, statedGroup);
				if (groupMatches(inferredGroup, statedGroup)) {
					continue nextGroup;
				}
			}
			//If we get to here, then an inferred group has not been matched by a stated one
			unmatchedGroup = inferredGroup;
		}
		
		if (unmatchedGroup != null) {
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			//Which inferred relationship is not also stated?
			List<Relationship> unmatched = new ArrayList<>();
			for (Relationship r : unmatchedGroup.getRelationships()) {
				if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
					unmatched.add(r);
				}
			}
				String unmatchedStr = unmatched.stream().map(r -> r.toString(true)).collect(Collectors.joining(",\n"));
				incrementSummaryInformation(ISSUE_COUNT);
				report (c, semTag, c.getDefinitionStatus(),
						c.toExpression(CharacteristicType.STATED_RELATIONSHIP),
						c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP), unmatchedStr);
		}
		return unmatchedGroup == null ? 0 : 1;
	}
	
	private RelationshipGroup filter(Concept filterType, RelationshipGroup group) {
		RelationshipGroup filteredGroup = new RelationshipGroup(group.getGroupId());
		for (Relationship r : group.getRelationships()) {
			if (!r.getType().equals(filterType)) {
				filteredGroup.addRelationship(r);
			}
		}
		return filteredGroup;
	}

	private boolean groupMatches(RelationshipGroup a, RelationshipGroup b) {
		if (a.getRelationships().size() != b.getRelationships().size()) {
			return false;
		}
		//Can we find a match for every relationship? Ignore groupId
		nextRelationship:
		for (Relationship relA : a.getRelationships()) {
			for (Relationship relB : b.getRelationships()) {
				if (relA.getType().equals(relB.getType()) && 
					relA.getTarget().equals(relB.getTarget())) {
					continue nextRelationship;
				}
			}
			//If we get here then we've failed to find a match for relA
			return false;
		}
		return true;
	}
	
	
	private void checkForSemTagViolations(Concept c) throws TermServerScriptException {
		//Ensure that the hierarchical level of this semantic tag is the same or deeper than those of the parent
		int tagLevel = getTagLevel(c);
		for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			int parentTagLevel = getTagLevel(p);
			if (tagLevel < parentTagLevel) {
				report (c, "Has higher level semantic tag than parent", p);
				incrementSummaryInformation(ISSUE_COUNT);
			}
		}
	}

	private int getTagLevel(Concept c) throws TermServerScriptException {
		String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		for (int i=0; i < semTagHiearchy.length; i++) {
			if (semTagHiearchy[i].equals(semTag)) {
				return i;
			}
		}
		//throw new TermServerScriptException("Unable to find semantic tag level for: " + c);
		error("Unable to find semantic tag level for: " + c, null);
		return NOT_SET;
	}
}
