package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.termserver.scripting.util.StringUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/**
 * Addresses case significance issues in the target sub-hierarchy
 * 
 * SUBST-288 Remove capital letter from greek letters (unless initial letter)
 * and adjust CS as required. 
 **/
public class CaseSignificanceFix extends BatchFix implements RF2Constants{
	
	boolean unpublishedContentOnly = false;
	Concept subHierarchy = SUBSTANCE;
	List<String> properNouns = new ArrayList<>();
	Map<String, List<String>> properNounPhrases = new HashMap<>();
	List<String> knownLowerCase = new ArrayList<>();
	
	String[] greekLettersUpper = new String[] { "Alpha", "Beta", "Delta", "Gamma", "Epsilon", "Tau" };
	String[] greekLettersLower = new String[] { "alpha", "beta", "delta", "gamma", "epsilon", "tau" };
	
	String[] exceptions = new String[] {"86622001"};
	
	protected CaseSignificanceFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		CaseSignificanceFix fix = new CaseSignificanceFix(null);
		try {
			ReportSheetManager.targetFolderId = "1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d"; //SUBSTANCES
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadCSWords();
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		//int changesMade = fixCaseSignifianceIssues(task, loadedConcept);
		int changesMade = fixGreekLetterIssues(task, loadedConcept);
		if (changesMade > 0) {
			updateConcept(task, concept, info);
		}
		return changesMade;
	}
	
	public void loadCSWords() throws IOException, TermServerScriptException {
		info("Loading " + inputFile);
		if (!inputFile.canRead()) {
			throw new TermServerScriptException("Cannot read: " + inputFile);
		}
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		for (String line : lines) {
			if (line.startsWith("milliunit/")) {
				debug("Check here");
			}
			//Split the line up on tabs
			String[] items = line.split(TAB);
			String phrase = items[0];
			//Does the word contain a capital letter (ie not the same as it's all lower case variant)
			if (!phrase.equals(phrase.toLowerCase())) {
				//Is this a phrase?
				String[] words = phrase.split(" ");
				if (words.length == 1) {
					properNouns.add(phrase);
				} else {
					List<String> phrases = properNounPhrases.get(words[0]);
					if (phrases == null) {
						phrases = new ArrayList<>();
						properNounPhrases.put(words[0], phrases);
					}
					phrases.add(phrase);
				}
			} else {
				knownLowerCase.add(phrase);
			}
		}
	}

	private int fixCaseSignifianceIssues(Task task, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if ( (!unpublishedContentOnly || !d.isReleased()) &&
					d.getTerm().contains("Product containing") && d.getTerm().contains("milliliter")) {
				String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				String firstLetter = d.getTerm().substring(0,1);
				String chopped = d.getTerm().substring(1);
				//Lower case first letters must be entire term case sensitive
				if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
					//Not dealing with this situation right now
					//report (c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
				} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
					if (chopped.equals(chopped.toLowerCase())) {
						report (task, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, d, "-> ci" );
						d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
						changesMade++;
					}
				} else {
					//For case insensitive terms, we're on the look out for capitial letters after the first letter
					if (!chopped.equals(chopped.toLowerCase())) {
						//Not dealing with this situation right now
						//report (c, d, preferred, caseSig, "Case insensitive term has a capital after first letter");
						//incrementSummaryInformation("issues");
					}
				}
			}
		}
		return changesMade;
	}
	
	private int fixGreekLetterIssues(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String matchedGreekUpper = StringUtils.containsAny(d.getTerm(), greekLettersUpper);
			String matchedGreekLower = StringUtils.containsAny(d.getTerm(), greekLettersLower);
			if ( (matchedGreekUpper != null || matchedGreekLower != null) && 
				//d.getTerm().contains("Product containing") && d.getTerm().contains("milliliter")) {
				(!unpublishedContentOnly || !d.isReleased())) {
				Description checkTerm = d;
				
				//If we start with the greek letter captitalised, that's OK to be capital
				if (matchedGreekUpper != null && !d.getTerm().startsWith(matchedGreekUpper)) {
					String replacementTerm = d.getTerm().replaceAll(matchedGreekUpper, matchedGreekUpper.toLowerCase());
					checkTerm = replaceDescription(t, c, d, replacementTerm, InactivationIndicator.ERRONEOUS);
					changesMade++;
				}
				
				//If we START with the greek letter lower, that's needs to be captialised
				if (matchedGreekLower != null && d.getTerm().startsWith(matchedGreekLower)) {
					String replacementTerm = StringUtils.capitalize(d.getTerm());
					//Now we might have an erroneous capital after a dash, or after "< ", say within 5 characters
					for (int idx = matchedGreekLower.length() ; idx + 2 < replacementTerm.length() && idx < matchedGreekLower.length() + 5; idx ++) {
						if (replacementTerm.charAt(idx) == '-' && replacementTerm.charAt(idx+1) == Character.toUpperCase(replacementTerm.charAt(idx+1))) {
							//if the NEXT character is also a dash, then leave this eg alpha-L-fucosidase
							//OR if it's also a capital eg alpha-MT
							if (replacementTerm.charAt(idx+2) != '-' && replacementTerm.charAt(idx+2) != Character.toUpperCase(replacementTerm.charAt(idx+2))) {
								replacementTerm = replacementTerm.substring(0,idx +1)+ Character.toLowerCase(replacementTerm.charAt(idx+1)) +replacementTerm.substring(idx+2);
							}
						}
						
						if (replacementTerm.charAt(idx) == ' ' && replacementTerm.charAt(idx+1) == Character.toUpperCase(replacementTerm.charAt(idx+1))) {
							replacementTerm = replacementTerm.substring(0,idx +1)+ Character.toLowerCase(replacementTerm.charAt(idx+1)) +replacementTerm.substring(idx+2);
						}
						
						if (replacementTerm.charAt(idx) == '<' && replacementTerm.charAt(idx +1) == ' ' && replacementTerm.charAt(idx+2) == Character.toUpperCase(replacementTerm.charAt(idx+2))) {
							replacementTerm = replacementTerm.substring(0,idx +2)+ Character.toLowerCase(replacementTerm.charAt(idx+2)) +replacementTerm.substring(idx+3);
						}
					}
					checkTerm = replaceDescription(t, c, d, replacementTerm, InactivationIndicator.ERRONEOUS);
					changesMade++;
				}
				
				String caseSig = SnomedUtils.translateCaseSignificanceFromEnum(checkTerm.getCaseSignificance());
				String firstLetter = checkTerm.getTerm().substring(0,1);
				String chopped = checkTerm.getTerm().substring(1);
				//Lower case first letters must be entire term case sensitive
				if (!checkTerm.getTerm().startsWith("Hb ") && !checkTerm.getTerm().startsWith("T-")) {
					if (Character.isLetter(firstLetter.charAt(0)) && firstLetter.equals(firstLetter.toLowerCase()) && !caseSig.equals(CS)) {
						//Not dealing with this situation right now
						//report (c, d, preferred, caseSig, "Terms starting with lower case letter must be CS");
					} else if (caseSig.equals(CS) || caseSig.equals(cI)) {
						if (chopped.equals(chopped.toLowerCase())) {
							report (t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, checkTerm, caseSig + "-> ci" );
							d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
							changesMade++;
						}
					} else {
						//For case insensitive terms, we're on the look out for capitial letters after the first letter
						if (!chopped.equals(chopped.toLowerCase())) {
							report (t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, checkTerm, caseSig + "-> cI" );
							d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
							changesMade++;
						}
					}
				}
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		info ("Identifying incorrect case signficance settings");
		this.setQuiet(true);
		for (Concept concept : subHierarchy.getDescendents(NOT_SET)) {
			if (concept.isActive() && !isException(concept.getId())) {
				/*if (fixCaseSignifianceIssues(null, concept.cloneWithIds()) > 0) {
					processMe.add(concept);
				}*/
				if (fixGreekLetterIssues(null, concept.cloneWithIds()) > 0) {
					processMe.add(concept);
				}
			}
		}
		debug ("Identified " + processMe.size() + " concepts to process");
		this.setQuiet(false);
		return processMe;
	}

	private boolean isException(String id) {
		for (String exception : exceptions) {
			if (exception.equals(id)) {
				return true;
			}
		}
		return false;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}
