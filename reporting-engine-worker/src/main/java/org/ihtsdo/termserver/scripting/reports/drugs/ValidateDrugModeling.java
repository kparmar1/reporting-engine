package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.File;
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
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.TermGenerator;
import org.snomed.otf.scheduler.domain.*;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class ValidateDrugModeling extends TermServerReport implements ReportClass {
	
	Concept [] solidUnits = new Concept [] { PICOGRAM, NANOGRAM, MICROGRAM, MILLIGRAM, GRAM };
	Concept [] liquidUnits = new Concept [] { MILLILITER, LITER };
	String[] semTagHiearchy = new String[] { "(product)", "(medicinal product)", "(medicinal product form)", "(clinical drug)" };
	
	private static final String[] badWords = new String[] { "preparation", "agent", "+"};
	
	private Concept[] doseFormTypes = new Concept[] {HAS_MANUFACTURED_DOSE_FORM};
	private Map<Concept, Boolean> acceptableMpfDoseForms = new HashMap<>();
	private Map<Concept, Boolean> acceptableCdDoseForms = new HashMap<>();	
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	private Map<Concept,Concept> grouperSubstanceUsage = new HashMap<>();
	
	TermGenerator termGenerator = new DrugTermGenerator(this);
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(ValidateDrugModeling.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1wtB15Soo-qdvb0GHZke9o_SjFSL_fxL3";  //DRUGS/Validation
		additionalReportColumns = "FSN, SemTag, Issue, Data, Detail";  //DRUGS-267
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Detail",
				"Issue, Count"};
		String[] tabNames = new String[] {	"Issues",
				"Summary"};
		populateAcceptableDoseFormMaps();
		populateGrouperSubstances();
		super.postInit(tabNames, columnHeadings, false);
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
		populateSummaryTab();
		info("Summary tab complete, all done.");
	}
	
	private void validateDrugsModeling() throws TermServerScriptException {
		Set<Concept> subHierarchy = gl.getDescendantsCache().getDescendents(MEDICINAL_PRODUCT);
		ConceptType[] allDrugTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_ONLY, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.MEDICINAL_PRODUCT_FORM_ONLY, ConceptType.CLINICAL_DRUG };
		ConceptType[] cds = new ConceptType[] { ConceptType.CLINICAL_DRUG };  //DRUGS-267
		
		//for (Concept concept : Collections.singleton(gl.getConcept("778271007"))) {
		for (Concept concept : subHierarchy) {
			DrugUtils.setConceptType(concept);
			
			//INFRA-4159 Seeing impossible situation of no stated parents
			if (concept.getParents(CharacteristicType.STATED_RELATIONSHIP).size() == 0) {
				String issueStr = "Concept appears to have no stated parents";
				initialiseSummaryInformation(issueStr);
				report (concept, issueStr);
				continue;
			}
			
			//DRUGS-585
			if (isMP(concept) || isMPF(concept)) {
				validateNoModifiedSubstances(concept);
			}
			
			//DRUGS-784
			if (concept.getConceptType().equals(ConceptType.CLINICAL_DRUG) || 
					isMPF(concept)) {
				validateAcceptableDoseForm(concept);
			}
			
			// DRUGS-281, DRUGS-282, DRUGS-269
			if (!concept.getConceptType().equals(ConceptType.PRODUCT)) {
				validateTerming(concept, allDrugTypes);  
			}
			
			//DRUGS-267
			validateIngredientsAgainstBoSS(concept);
			
			//DRUGS-793
			if (!concept.getConceptType().equals(ConceptType.PRODUCT)) {
				checkForBossGroupers(concept);
				checkForPaiGroupers(concept);
			}
			
			//DRUGS-296 
			if (concept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) && 
				concept.getParents(CharacteristicType.STATED_RELATIONSHIP).get(0).equals(MEDICINAL_PRODUCT)) {
				validateStatedVsInferredAttributes(concept, HAS_ACTIVE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(concept, HAS_PRECISE_INGRED, allDrugTypes);
				validateStatedVsInferredAttributes(concept, HAS_MANUFACTURED_DOSE_FORM, allDrugTypes);
			}
			
			//DRUGS-603: DRUGS-686 - Various modelling rules
			if (concept.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				validateCdModellingRules(concept);
			}
			
			//DRUGS-518
			if (SnomedUtils.isConceptType(concept, cds)) {
				checkForInferredGroupsNotStated(concept);
			}
			
			//DRUGS-51?
			if (concept.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				validateConcentrationStrength(concept);
			}
			
			//DRUGS-288
			validateAttributeValueCardinality(concept, HAS_ACTIVE_INGRED);
			
			//DRUGS-93, DRUGS-759
			checkForBadWords(concept);  
			
			//DRUGS-629
			checkForSemTagViolations(concept);
		}
		info ("Drugs validation complete");
	}

	private boolean isMP(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_ONLY);
	}
	
	private boolean isMPF(Concept concept) {
		return concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) || 
				concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM_ONLY);
	}

	private void populateGrouperSubstances() throws TermServerScriptException {
		//DRUGS-793 Ingredients of "(product)" Medicinal products will be
		//considered 'grouper substances' that should not be used as BoSS 
		for (Concept c : gl.getDescendantsCache().getDescendents(MEDICINAL_PRODUCT)) {
			DrugUtils.setConceptType(c);
			if (c.getConceptType().equals(ConceptType.PRODUCT)) {
				for (Concept substance : DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
					if (!grouperSubstanceUsage.containsKey(substance)) {
						grouperSubstanceUsage.put(substance, c);
					}
				}
			}
		}
	}

	private void checkForBossGroupers(Concept c) throws TermServerScriptException {
		String issueStr = "Grouper substance used as BoSS";
		initialiseSummary(issueStr);
		for (Concept boss : SnomedUtils.getTargets(c, new Concept[] {HAS_BOSS}, CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (grouperSubstanceUsage.containsKey(boss)) {
				report (c, issueStr, boss, " identified as grouper in ", grouperSubstanceUsage.get(boss));
			}
		}
	}
	
	private void checkForPaiGroupers(Concept c) throws TermServerScriptException {
		String issueStr = "Grouper substance used as PAI";
		initialiseSummary(issueStr);
		for (Concept pai : SnomedUtils.getTargets(c, new Concept[] {HAS_PRECISE_INGRED}, CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (grouperSubstanceUsage.containsKey(pai)) {
				report (c, issueStr, pai, " identified as grouper in ", grouperSubstanceUsage.get(pai));
			}
		}
	}

	private void validateAcceptableDoseForm(Concept c) throws TermServerScriptException {
		String issueStr1 = c.getConceptType() + " uses unlisted dose form";
		String issueStr2 = c.getConceptType() + " uses unacceptable dose form";
		initialiseSummary(issueStr1);
		initialiseSummary(issueStr2);
		
		Map<Concept, Boolean> acceptableDoseForms;
		if (isMPF(c)) {
			acceptableDoseForms = acceptableMpfDoseForms;
		} else {
			acceptableDoseForms = acceptableCdDoseForms;
		}
		
		acceptableDoseForms.put(gl.getConcept("785898006 |Conventional release solution for irrigation (dose form)|"), Boolean.TRUE);
		acceptableDoseForms.put(gl.getConcept("785910004 |Prolonged-release intralesional implant (dose form)|"), Boolean.TRUE);
		
		Concept thisDoseForm = SnomedUtils.getTarget(c, doseFormTypes, UNGROUPED, CharacteristicType.INFERRED_RELATIONSHIP);
		//Is this dose form acceptable?
		if (acceptableDoseForms.containsKey(thisDoseForm)) {
			if (acceptableDoseForms.get(thisDoseForm).equals(Boolean.FALSE)) {
				report (c, issueStr2, thisDoseForm);
			}
		} else {
			report (c, issueStr1, thisDoseForm);
		}
	}

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
		
		int total = issueSummaryMap.entrySet().stream()
				.map(e -> e.getValue())
				.collect(Collectors.summingInt(Integer::intValue));
		reportSafely (SECONDARY_REPORT, (Component)null, "TOTAL", total);
	}

	/**
	*	Acutation should be modeled with presentation strength and unit of presentation.
	*	Has presentation strength denominator unit (attribute) cannot be 258773002|Milliliter (qualifier value)
	*	Has concentration strength denominator unit (attribute) cannot be 732936001|Tablet (unit of presentation)
	*	Has presentation strength denominator unit (attribute) cannot be 258684004|milligram (qualifier value)
	*	Has concentration strength numerator unit (attribute) cannot be 258727004|milliequivalent (qualifier value)
	 * @throws TermServerScriptException 
	*/
	private void validateCdModellingRules(Concept c) throws TermServerScriptException {
		String issueStr = "Group contains > 1 presentation/concentration strength";
		String issue2Str = "Group contains > 1 presentation/concentration strength";
		String issue3Str = "Invalid drugs model";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		initialiseSummary(issue3Str);
		
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (g.isGrouped()) {
				List<Relationship> ps = g.getType(HAS_PRES_STRENGTH_VALUE);
				List<Relationship> psdu = g.getType(HAS_PRES_STRENGTH_DENOM_UNIT);
				List<Relationship> csdu = g.getType(HAS_CONC_STRENGTH_DENOM_UNIT);
				List<Relationship> csnu = g.getType(HAS_CONC_STRENGTH_UNIT);
				if (psdu.size() > 1 || csdu.size() > 1) {
					report(c, issueStr, g);
					return;
				} 
				if (c.getFsn().toLowerCase().contains("actuation")) {
					if (ps.size() < 1 || psdu.size() < 1) {
						report(c, issue2Str, g);
						return;
					}
				}
				if (psdu.size() == 1 && psdu.get(0).getTarget().equals(MILLILITER)) {
					report (c, issue3Str, psdu.get(0));
				}
				if (csdu.size() == 1 && csdu.get(0).getTarget().equals(gl.getConcept("732936001|Tablet|"))) {
					report (c, issue3Str, csdu.get(0));
				}
				if (psdu.size() == 1 && psdu.get(0).getTarget().equals(MILLIGRAM)) {
					report (c, issue3Str, psdu.get(0));
				}
				if (csnu.size() == 1 && csnu.get(0).getTarget().equals(gl.getConcept("258727004|milliequivalent|"))) {
					report (c, issue3Str, csdu.get(0));
				}
				if (csnu.size() == 1 && csnu.get(0).getTarget().equals(gl.getConcept("258728009|microequivalent|"))) {
					report (c, issue3Str, csdu.get(0));
				}
				if (csnu.size() == 1 && csnu.get(0).getTarget().equals(gl.getConcept("258718000|millimole|"))) {
					report (c, issue3Str, csdu.get(0));
				}
			}
		}
	}

	private void validateNoModifiedSubstances(Concept c) throws TermServerScriptException {
		String issueStr = c.getConceptType() + " has modified ingredient";
		initialiseSummary(issueStr);
		//Check all ingredients for any that themselves have modification relationships
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(HAS_PRECISE_INGRED) || r.getType().equals(HAS_ACTIVE_INGRED) ) {
				Concept ingredient = r.getTarget();
				for (Relationship ir :  ingredient.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (ir.getType().equals(IS_MODIFICATION_OF)) {
						report (c, issueStr, ingredient, "is modification of", ir.getTarget());
					}
				}
			}
		}
	}

	/**
	 * For Pattern 2A Drugs (liquids) where we have both a presentation strength and a concentration
	 * report these values and confirm if the units change between the two, and if the calculation is correct
	 * @param concept
	 * @return
	 * @throws TermServerScriptException 
	 */
	private void validateConcentrationStrength(Concept c) throws TermServerScriptException {
		String issueStr = "Presentation/Concentration mismatch";
		initialiseSummary(issueStr);
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
				}
				report (c, issueStr, i.substance, i.presToString(), i.concToString(), unitsChange, issueDetected, issueDetected? presRatio + " vs " + concRatio : "");
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
				String issueStr = "Term contains bad word: " + badWord;
				initialiseSummary(issueStr);
				if (term.contains(badWord)) {
					//Exception, MP PT will finish with word "product"
					if (concept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT) && d.isPreferred() && badWord.equals("product")) {
						continue;
					} else {
						if (badWord.equals("+") && isPlusException(term)) {
							continue;
						}
						report (concept, issueStr, d.toString());
						return;
					}
				}
			}
		}
	}

	private boolean isPlusException(String term) {
		//Various rules that allow a + to exist next to other characters
		if (term.contains("^+") ||
			term.contains("+)") ||
			term.contains("+)") ||
			term.contains("+]") ||
			term.contains("(+")) {
			return true;
		}
		return false;
	}

	private void validateStatedVsInferredAttributes(Concept concept,
			Concept attributeType, ConceptType[] drugTypes) throws TermServerScriptException {
		String issueStr = "Cardinality mismatch stated vs inferred " + attributeType;
		String issue2Str = "Stated X is not present in inferred view";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		if (drugTypes==null || SnomedUtils.isConceptType(concept, drugTypes)) {
			List<Relationship> statedAttributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			List<Relationship> infAttributes = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
			if (statedAttributes.size() != infAttributes.size()) {
				String data = "(s" + statedAttributes.size() + " i" + infAttributes.size() + ")";
				report (concept, issueStr, data);
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
						issue2Str = "Stated " + statedAttribute.getType() + " is not present in inferred view";
						String data = statedAttribute.toString();
						report (concept, issue2Str, data);
					}
				}
			}
		}
	}

	private void validateIngredientsAgainstBoSS(Concept concept) throws TermServerScriptException {
		String issueStr  = "Active ingredient is a subtype of BoSS.  Expected modification.";
		String issue2Str = "Basis of Strength not equal or subtype of active ingredient, neither is active ingredient a modification of the BoSS";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
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
							report (concept, issueStr, ingred, boSS);
						} else if (isModificationOf) {
							incrementSummaryInformation("Valid Ingredients as Modification of BoSS");
						} else if (isSelf) {
							incrementSummaryInformation("BoSS matches ingredient");
						}
					}
				}
			}
			if (!matchFound) {
				report (concept, issue2Str, boSS);
			}
		}
	}

	private void validateTerming(Concept c, ConceptType[] drugTypes) throws TermServerScriptException {
		//Only check FSN for certain drug types (to be expanded later)
		if (!SnomedUtils.isConceptType(c, drugTypes)) {
			incrementSummaryInformation("Concepts ignored - wrong type");
		}
		incrementSummaryInformation("Concepts validated to ensure ingredients correct in FSN");
		Description currentFSN = c.getFSNDescription();
		termGenerator.setQuiet(true);
		
		//Create a clone to be retermed, and then we can compare descriptions with the original	
		Concept clone = c.clone();
		termGenerator.ensureTermsConform(null, clone, CharacteristicType.STATED_RELATIONSHIP);
		Description proposedFSN = clone.getFSNDescription();
		compareTerms(c, "FSN", currentFSN, proposedFSN);
		Description ptUS = clone.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description ptGB = clone.getPreferredSynonym(GB_ENG_LANG_REFSET);
		if (ptUS == null || ptUS.getTerm() == null || ptGB == null || ptGB.getTerm() == null) {
			debug ("Debug here - hit a null");
		}
		if (ptUS.getTerm().equals(ptGB.getTerm())) {
			compareTerms(c, "PT", c.getPreferredSynonym(US_ENG_LANG_REFSET), ptUS);
		} else {
			compareTerms(c, "US-PT", c.getPreferredSynonym(US_ENG_LANG_REFSET), ptUS);
			compareTerms(c, "GB-PT", c.getPreferredSynonym(GB_ENG_LANG_REFSET), ptGB);
		}
	}
	
	private void compareTerms(Concept c, String termName, Description actual, Description expected) throws TermServerScriptException {
		String issueStr = termName + " does not meet expectations";
		String issue2Str = termName + " case significance does not meet expectations";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		if (!actual.getTerm().equals(expected.getTerm())) {
			String differences = findDifferences (actual.getTerm(), expected.getTerm());
			report (c, issueStr, expected.getTerm(), differences, actual);
		} else if (!actual.getCaseSignificance().equals(expected.getCaseSignificance())) {
			String detail = "Expected: " + SnomedUtils.translateCaseSignificanceFromEnum(expected.getCaseSignificance());
			detail += ", Actual: " + SnomedUtils.translateCaseSignificanceFromEnum(actual.getCaseSignificance());
			report (c, issue2Str, detail, actual);
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
					differences += actuals[x] + " vs ";
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
		String issueStr = "Multiple " + charType + " instances of active ingredient";
		initialiseSummary(issueStr);
		
		Set<Concept> valuesEncountered = new HashSet<Concept>();
		for (Relationship r : c.getRelationships(charType, activeIngredient, ActiveState.ACTIVE)) {
			//Have we seen this value for the target attribute type before?
			Concept target = r.getTarget();
			if (valuesEncountered.contains(target)) {
				report(c, issueStr, target.toString());
			}
			valuesEncountered.add(target);
		}
	}
	
	private int checkForInferredGroupsNotStated(Concept c) throws TermServerScriptException {
		String issueStr = "Inferred group not stated";
		initialiseSummary(issueStr);
		
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
			//Which inferred relationship is not also stated?
			List<Relationship> unmatched = new ArrayList<>();
			for (Relationship r : unmatchedGroup.getRelationships()) {
				if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size() == 0) {
					unmatched.add(r);
				}
			}
				String unmatchedStr = unmatched.stream().map(r -> r.toString(true)).collect(Collectors.joining(",\n"));
				report (c, issueStr,
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
		String issueStr =  "Has higher level semantic tag than parent";
		initialiseSummary(issueStr);
		//Ensure that the hierarchical level of this semantic tag is the same or deeper than those of the parent
		int tagLevel = getTagLevel(c);
		for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			int parentTagLevel = getTagLevel(p);
			if (tagLevel < parentTagLevel) {
				report (c, issueStr, p);
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
	
	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}
	
	protected void report (Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		super.report (PRIMARY_REPORT, c, details);
	}
	
	private void populateAcceptableDoseFormMaps() throws TermServerScriptException {
		String fileName = "resources/acceptable_dose_forms.tsv";
		debug ("Loading " + fileName );
		try {
			List<String> lines = Files.readLines(new File(fileName), Charsets.UTF_8);
			boolean isHeader = true;
			for (String line : lines) {
				String[] items = line.split(TAB);
				if (!isHeader) {
					Concept c = gl.getConcept(items[0]);
					acceptableMpfDoseForms.put(c, items[2].equals("yes"));
					acceptableCdDoseForms.put(c, items[3].equals("yes"));
				} else {
					isHeader = false;
				}
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + fileName, e);
		}
	}
	
}
