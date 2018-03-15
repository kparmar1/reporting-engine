package org.ihtsdo.termserver.scripting.util;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript.ReportActionType;
import org.ihtsdo.termserver.scripting.TermServerScript.Severity;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.Acceptability;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CaseSignificance;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.DescriptionType;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.InactivationIndicator;

public class DrugTermGenerator implements RF2Constants{
	
	private TermServerScript parent;
	private boolean quiet = false;
	
	private String [] forceCS = new String[] { "N-" };
	private String[] vitamins = new String[] {" A ", " B ", " C ", " D ", " E ", " G "};
	
	public DrugTermGenerator (TermServerScript parent) {
		this.parent = parent;
	}
	
	/*protected int normalizeDrugTerms(Task task, Concept concept, String newSemanticTag) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {

			String replacementTerm = d.getTerm();
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			ensureCaptialization(d);

			//If this is an FSN, make sure we start with the "Product containing" prefix
			//and force the semantic tag
			if (isFSN) {
				if (!replacementTerm.startsWith(productPrefix)) {
					//If we're not case sensitive, make the first letter lower case
					if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
						replacementTerm = SnomedUtils.deCapitalize(replacementTerm);
					}
					replacementTerm = productPrefix + replacementTerm;
				}
				String[] fsnParts = SnomedUtils.deconstructFSN(replacementTerm);
				replacementTerm = fsnParts[0] + " " + newSemanticTag;
			}
			
			//Have we made any changes?  Create a new description if so
			Description replacement = d.clone(null);
			replacement.setTerm(replacementTerm);
			if (!replacementTerm.equals(d.getTerm())) {
				boolean doReplacement = true;
				if (termAlreadyExists(concept, replacementTerm)) {
					report(task, concept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Replacement term already exists: '" + replacementTerm + "' inactivating abnormal term only.");
					doReplacement = false;
				}
				
				String msg;
				//Has our description been published?  Remove entirely if not
				if (d.isReleased()) {
					d.setActive(false);
					d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					msg = "Inactivated desc ";
				} else {
					concept.getDescriptions().remove(d);
					msg = "Deleted desc ";
				}
				msg +=  d.getDescriptionId() + " - '" + d.getTerm().toString();
				if (doReplacement) {
					msg += "' in favour of '" + replacementTerm + "'";
					concept.addDescription(replacement);
				}
				changesMade++;
				report(task, concept, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
			}
			
			//If this is the FSN, then we should have another description without the semantic tag as an acceptable term
			if (d.getType().equals(DescriptionType.FSN)) {
				Description fsnCounterpart = replacement.clone(null);
				String counterpartTerm = SnomedUtils.deconstructFSN(fsnCounterpart.getTerm())[0];
				
				if (!termAlreadyExists(concept, counterpartTerm)) {
					fsnCounterpart.setTerm(counterpartTerm);
					report(task, concept, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, "FSN Counterpart added: " + counterpartTerm);
					fsnCounterpart.setType(DescriptionType.SYNONYM);
					fsnCounterpart.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
					concept.addDescription(fsnCounterpart);
				}
			}
		}
		return changesMade;
	}*/

	public int ensureDrugTermsConform(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		//This function will split out the US / GB terms if the ingredients show variance where the product does not
		validateUsGbVarianceInIngredients(t,c);

		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			boolean isGbPT = d.isPreferred(GB_ENG_LANG_REFSET);
			boolean isUsPT = d.isPreferred(US_ENG_LANG_REFSET);
			boolean isPT = (isGbPT || isUsPT);
			boolean hasUsGbVariance = !(isGbPT && isUsPT);
			String langRefset = US_ENG_LANG_REFSET;
			if (isGbPT && hasUsGbVariance) {
				langRefset = GB_ENG_LANG_REFSET;
			}
			
			//If it's not the PT or FSN, skip it.   We'll delete later if it's not the FSN counterpart
			if (!isPT && !isFSN) {
				continue;
			}
			
			//Skip FSNs that contain the word vitamin
			if (isFSN && d.getTerm().toLowerCase().contains("vitamin")) {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Skipped vitamin FSN");
				continue;
			}
			
			//Check the existing term has correct capitalization
			ensureCaptialization(d);
			String replacementTerm = calculateTermFromIngredients(c, isFSN, isPT, langRefset);
			replacementTerm = checkForVitamins(replacementTerm, d.getTerm());
			Description replacement = d.clone(null);
			replacement.setTerm(replacementTerm);
			
			//Does the case significance of the ingredients suggest a need to modify the term?
			if (replacement.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) && SnomedUtils.isCaseSensitive(replacementTerm)) {
				replacement.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
			}
			
			//Have we made any changes?  Create a new description if so
			if (!replacementTerm.equals(d.getTerm())) {
				changesMade += replaceTerm(t, c, d, replacement);
			}
			
			//If this is the FSN, then we should have another description without the semantic tag as an acceptable term
			if (isFSN) {
				Description fsnCounterpart = replacement.clone(null);
				String counterpartTerm = SnomedUtils.deconstructFSN(fsnCounterpart.getTerm())[0];
				
				if (!SnomedUtils.termAlreadyExists(c, counterpartTerm)) {
					fsnCounterpart.setTerm(counterpartTerm);
					report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, "FSN Counterpart added: " + counterpartTerm);
					fsnCounterpart.setType(DescriptionType.SYNONYM);
					fsnCounterpart.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
					c.addDescription(fsnCounterpart);
				}
			}
		}
		//Now that the FSN is resolved, remove any redundant terms
		changesMade += removeRedundantTerms(t,c);
		return changesMade;
	}
	
	public String calculateTermFromIngredients(Concept c, boolean isFSN, boolean isPT, String langRefset) throws TermServerScriptException {
		String proposedTerm = "";
		//Get all the ingredients in order
		Set<String> ingredients = getIngredientsWithStrengths(c, isFSN, langRefset);
		
		//What prefixes and suffixes do we need?
		String prefix = "";
		String suffix = "";
		if (isFSN) {
			prefix = "Product containing ";
			switch (c.getConceptType()) {
				case MEDICINAL_PRODUCT_FORM : suffix =  " in " + DrugUtils.getDosageForm(c);
										break;
				case CLINICAL_DRUG : 	prefix = "Product containing only ";
										//TODO Check that presentation is solid before adding 1 each
										suffix =  "/1 each "  + DrugUtils.getDosageForm(c);
										break;
				default:
			}
		} else if (isPT) {
			switch (c.getConceptType()) {
				case MEDICINAL_PRODUCT : suffix = " product";
										break;
				case MEDICINAL_PRODUCT_FORM : suffix =  " in " + DrugUtils.getDosageForm(c);
										break;
				case CLINICAL_DRUG : 	suffix =  " " + DrugUtils.getDosageForm(c);
										break;
				default:
			}
		}
		
		//Form the term from the ingredients with prefixes and suffixes as required.
		proposedTerm = prefix + StringUtils.join(ingredients, " and ") + suffix;
		if (isFSN) {
			proposedTerm += " " + SnomedUtils.deconstructFSN(c.getFsn())[1];
		}
		proposedTerm = SnomedUtils.capitalize(proposedTerm);
		return proposedTerm;
	}

	private int removeRedundantTerms(Task t, Concept c) {
		int changesMade = 0;
		List<Description> allTerms = c.getDescriptions(ActiveState.ACTIVE);
		for (Description d : allTerms) {
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			if (!isFSN && !d.isPreferred()) {
				//Is this term the FSN counterpart?  Remove if not
				String fsnCounterpart = SnomedUtils.deconstructFSN(c.getFsn())[0];
				if (!d.getTerm().equals(fsnCounterpart)) {
					boolean isInactivated = removeDescription(c,d);
					String msg = (isInactivated?"Inactivated redundant desc ":"Deleted redundant desc ") +  d;
					report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_REMOVED, msg);
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private boolean removeDescription(Concept c, Description d) {
		if (d.isReleased()) {
			d.setActive(false);
			d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			return true;
		} else {
			c.getDescriptions().remove(d);
			return false;
		}
	}
	
	private void validateUsGbVarianceInIngredients(Task t, Concept c) throws TermServerScriptException {
		//If the ingredient names have US/GB variance, the Drug should too.
		//Do we have two preferred terms?
		boolean drugVariance = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1;
		boolean ingredientVariance = false;
		//Now check the ingredients
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
			Concept ingredient = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			//Does the ingredient have more than one PT?
			if (ingredient.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1 ) {
				ingredientVariance = true;
				break;
			}
		}
		if (drugVariance != ingredientVariance) {
			String msg = "Drug vs Ingredient US/GB term variance mismatch : Drug=" + drugVariance + " Ingredients=" + ingredientVariance;
			report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			if (!drugVariance && ingredientVariance) {
				splitPreferredTerm(t, c);
			}
		}
	}

	/**
	 * Take the preferred term and create US and GB specific copies
	 * @param t
	 * @param c
	 * @throws TermServerScriptException 
	 */
	private void splitPreferredTerm(Task t, Concept c) throws TermServerScriptException {
		List<Description> preferredTerms = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (preferredTerms.size() != 1) {
			report (t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Unexpected number of preferred terms: " + preferredTerms.size());
		} else {
			Description usPT = preferredTerms.get(0);
			usPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET));
			Description gbPT = usPT.clone(null);
			gbPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET));
			report (t, c, Severity.HIGH, ReportActionType.DESCRIPTION_ADDED, "Split PT into US/GB variants: " + usPT + "/" + gbPT);
			c.addDescription(gbPT);
		}
	}

	private int replaceTerm(Task t, Concept c, Description removing, Description replacement) {
		int changesMade = 0;
		boolean doReplacement = true;
		if (SnomedUtils.termAlreadyExists(c, replacement.getTerm())) {
			//But does it exist inactive?
			if (SnomedUtils.termAlreadyExists(c, replacement.getTerm(), ActiveState.INACTIVE)) {
				reactivateMatchingTerm(t, c, replacement);
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Replacement term already exists: '" + replacement.getTerm() + "' inactivating unwanted term only.");
			}
			//If we're removing a PT, merge the acceptability into the existing term, also any from the replacement
			if (removing.isPreferred()) {
				mergeAcceptability(t, c, removing, replacement);
			}
			doReplacement = false;
		}
		
		//Has our description been published?  Remove entirely if not
		boolean isInactivated = removeDescription(c,removing);
		String msg = (isInactivated?"Inactivated desc ":"Deleted desc ") +  removing;
		if (doReplacement) {
			msg += " in favour of " + replacement;
			c.addDescription(replacement);
		}
		changesMade++;
		Severity severity = removing.getType().equals(DescriptionType.FSN)?Severity.MEDIUM:Severity.LOW;
		report(t, c, severity, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
		return changesMade;
	}

	private void mergeAcceptability(Task t, Concept c, Description removing, Description replacement) {
		//Find the matching term that is not removing and merge that with the acceptability of removing
		boolean merged = false;
		for (Description match : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!removing.equals(match) && match.getTerm().equals(replacement.getTerm())) {
				Map<String,Acceptability> mergedMap = SnomedUtils.mergeAcceptabilityMap(removing.getAcceptabilityMap(), match.getAcceptabilityMap());
				match.setAcceptabilityMap(mergedMap);
				//Now add in any improved acceptability that was coming from the replacement 
				mergedMap = SnomedUtils.mergeAcceptabilityMap(match.getAcceptabilityMap(), replacement.getAcceptabilityMap());
				match.setAcceptabilityMap(mergedMap);
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Merged acceptability from description being replaced and replacement into term that already exists: " + match);
				merged = true;
			}
		}
		if (!merged) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to find existing term to receive accepabilty merge with " + removing);
		}
	}

	public String checkForVitamins(String term, String origTerm) {
		//See if we've accidentally made vitamin letters lower case and switch back
		for (String vitamin : vitamins) {
			if (origTerm.contains(vitamin)) {
				term = term.replace(vitamin.toLowerCase(), vitamin);
			} else if (origTerm.endsWith(vitamin.substring(0, 2))) {
				//Is it still at the end?
				if (term.endsWith(vitamin.toLowerCase().substring(0, 2))) {
					term = term.replace(vitamin.toLowerCase().substring(0, 2), vitamin.substring(0, 2));
				} else {
					//It should now have a space around it
					term = term.replace(vitamin.toLowerCase(), vitamin);
				}
			}
		}
		return term;
	}
	
	private static Set<String> getIngredientsWithStrengths(Concept c, boolean isFSN, String langRefset) throws TermServerScriptException {
		List<Relationship> ingredientRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		Set<String> ingredients = new TreeSet<String>();  //Will naturally sort in alphabetical order
		for (Relationship r : ingredientRels) {
			//Need to recover the full concept to have all descriptions, not the partial one stored as the target.
			Concept ingredient = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			
			//Do we have a BoSS in the same group?
			Concept boSS = getTarget(c, HAS_BOSS, r.getGroupId());
			
			//Are we adding the strength?
			Concept strength = getTarget (c, HAS_STRENGTH_VALUE, r.getGroupId());
			
			//And the unit
			Concept unit = getTarget(c, HAS_STRENGTH_UNIT, r.getGroupId());
			
			String ingredientWithStrengthTerm = formIngredientWithStrengthTerm (ingredient, boSS, strength, unit, isFSN, langRefset);
			ingredients.add(ingredientWithStrengthTerm);
		}
		return ingredients;
	}

	private static String formIngredientWithStrengthTerm(Concept ingredient, Concept boSS, Concept strength, Concept unit, boolean isFSN, String langRefset) throws TermServerScriptException {
		boolean separateBoSS = (boSS!= null && !boSS.equals(ingredient));
		String ingredientTerm="";
		
		//First the ingredient, with the BoSS first if different
		if (separateBoSS) {
			ingredientTerm = getTermForConcat(boSS, isFSN, langRefset);
			ingredientTerm += " as (";
		}
		
		ingredientTerm += getTermForConcat(ingredient, isFSN, langRefset);
		
		if (separateBoSS) {
			ingredientTerm += ")";
		}

		
		//Now add the Strength
		if (strength != null) {
			ingredientTerm += " " + SnomedUtils.deconstructFSN(strength.getFsn())[0];
		}
		
		if (unit != null) {
			ingredientTerm += " " + getTermForConcat(ingredient, isFSN || unit.equals(MILLIGRAM), langRefset);
		}
		
		return ingredientTerm;
	}

	private static String getTermForConcat(Concept c, boolean useFSN, String langRefset) throws TermServerScriptException {
		Description desc;
		String term;
		if (useFSN) {
			desc = c.getFSNDescription();
			term = SnomedUtils.deconstructFSN(desc.getTerm())[0];
		} else {
			desc = c.getPreferredSynonym(langRefset);
			term = desc.getTerm();
		}
		if (!desc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
			term = SnomedUtils.deCapitalize(term);
		}
		return term;
	}

	private static Concept getTarget(Concept c, Concept type, int groupId) {
		List<Relationship> rels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, type, groupId);
		if (rels.size() > 1) {
			TermServerScript.warn(c + " has multiple " + type + " in group " + groupId);
		} else if (rels.size() == 1) {
			return rels.get(0).getTarget();
		}
		return null;
	}

	private void reactivateMatchingTerm(Task t, Concept c, Description replacement) {
		//Loop through the inactive terms and reactivate the one that matches the replacement
		for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
			if (d.getTerm().equals(replacement.getTerm())) {
				d.setActive(true);
				d.setCaseSignificance(replacement.getCaseSignificance());
				d.setAcceptabilityMap(replacement.getAcceptabilityMap());
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, "Re-activated inactive term " + d);
				return;
			}
		}
	}
	
	protected void report(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) {
		if (!quiet) {
			parent.report(task, component, severity, actionType, details);
		}
	}
	
	public void ensureCaptialization(Description d) {
		String term = d.getTerm();
		for (String checkStr : forceCS) {
			if (term.startsWith(checkStr)) {
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			}
		}
	}
	
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}
	
}
