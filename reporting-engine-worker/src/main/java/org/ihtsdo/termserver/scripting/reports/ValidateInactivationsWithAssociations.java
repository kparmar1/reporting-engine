package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.util.StringUtils;

/**
 * INFRA-2454
 * MAINT-489 Ensure that inactivation indicators are appropriate to historical associations
 * No active historical associations if the concept does not have an inactivation indicator
 * Inactivated as "Non conformance to Ed Policy" should have no historical associations
 * Inactivated as "Ambiguous" should have [1..*] Possibly Equivalent to associations
 * Inactivated as "Component move elsewhere" should have [1...1] Moved To associations
 * Inactivated as "Duplicate" should have [1...1] Same As associations
 * Inactivated as "Erroneous" or "Outdated" should have [1...1] Replaced By associations
 * Inactivated as "Limited Component" should have [1..*] Was A associations
 */
public class ValidateInactivationsWithAssociations extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermServerReport.run(ValidateInactivationsWithAssociations.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		additionalReportColumns="FSN, Concept EffectiveTime, Issue, isLegacy (C/D), Data";
		super.init(run);
	}

	@Override
	public Job getJob() {
		String[] parameterNames = new String[] { "SubHierarchy" };
		return new Job( new JobCategory(JobCategory.RELEASE_VALIDATION),
						"Validate Inactivations with Associations",
						"Ensures that inactivation indicators are appropriate to historical associations",
						parameterNames);
	}

	public void runJob() throws TermServerScriptException {
		
		for (Concept c : gl.getAllConcepts()) {
			String isLegacy = isLegacy(c);
			if (!c.isActive()) {
				if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() == 0) {
					incrementSummaryInformation("Inactive concept missing inactivation indicator");
					//report (c, c.getEffectiveTime(), "Inactive concept missing inactivation indicator", isLegacy);
				} else if (c.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
							.map(i->i.toString())
							.collect(Collectors.joining(",\n"));
					report (c, c.getEffectiveTime(), null, "Concept has multiple inactivation indicators", isLegacy, data);
				} else {
					InactivationIndicatorEntry i = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).get(0); 
					switch (i.getInactivationReasonId()) {
						case SCTID_INACT_AMBIGUOUS : 
							validate(c, i, "1..*", SCTID_HIST_POSS_EQUIV_REFSETID, isLegacy);
							break;
						case SCTID_INACT_NON_CONFORMANCE :
							validate(c, i, "0..0", null, isLegacy);
							break;
						case SCTID_INACT_MOVED_ELSEWHERE :
							validate(c, i, "1..1", SCTID_HIST_MOVED_TO_REFSETID, isLegacy);
							break;
						case SCTID_INACT_DUPLICATE :
							validate(c, i, "1..1", SCTID_HIST_SAME_AS_REFSETID, isLegacy);
							break;
						case SCTID_INACT_LIMITED : 
							validate(c, i, "1..1", SCTID_HIST_WAS_A_REFSETID, isLegacy);
							break;
						case SCTID_INACT_ERRONEOUS :
						case SCTID_INACT_OUTDATED :
							validate(c, i, "1..1", SCTID_HIST_REPLACED_BY_REFSETID, isLegacy);
							break;
						default :
							report (c, c.getEffectiveTime(), "Unrecognised concept inactivation indicator", isLegacy, i);
					
					}
				}
			}
			
			//In all cases, run a check on the descriptions as well
			//Inactivation indicators should have an inactivation indicator
			//Active descriptions might have one if the concept is inactive
			
			nextDescription:
			for (Description d : c.getDescriptions()) {
				String cdLegacy = isLegacy + "/" + isLegacy(d); 
				//What are we looking at here?
				String data = c.getInactivationIndicatorEntries(ActiveState.ACTIVE).stream()
						.map(i->i.toString())
						.collect(Collectors.joining(",\n"));
				
				//First check that any indicators are only in the appropriate refset
				for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					if (!i.getRefsetId().equals(SCTID_DESC_INACT_IND_REFSET)) {
						report (c, c.getEffectiveTime(), d, "Description has something other than a description inactivation indicator", cdLegacy, d, i);
					}
					continue nextDescription;
				}
				
				// Now check for duplicates, followed by acceptable entries
				if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					report (c, c.getEffectiveTime(), "Description has multiple inactivation indicators", cdLegacy, d, data);
				} else if (d.isActive() && !c.isActive()) {
					//Expect a single "Concept not current" indicator
					if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() == 0) {
						report (c, c.getEffectiveTime(), "Active description of inactive concept is missing 'Concept non-current' indicator", cdLegacy, d);
					} else {
						InactivationIndicatorEntry i = d.getInactivationIndicatorEntries(ActiveState.ACTIVE).get(0); 
						if (!i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
							report (c, c.getEffectiveTime(), "Active description of inactive concept has something other than a 'Concept non-current' indicator", cdLegacy, d, i);
						}
					}
				} else if (d.isActive() && c.isActive()) {
					//Expect NO inactivation indicator here
					if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 0) {
						report (c, c.getEffectiveTime(), d, "Active description of active concept should not have an inactivation indicator", cdLegacy, d, data);
					}
				} else if (!d.isActive() && !c.isActive()) {
					//Expect inactivation indicator here, but not Concept-non-current
					if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() != 1) {
						//report (c, c.getEffectiveTime(), d, "Inactive description of active concept should have an inactivation indicator", cdLegacy, d);
						incrementSummaryInformation("Inactive description of active concept should have an inactivation indicator");
					} else {
						InactivationIndicatorEntry i = d.getInactivationIndicatorEntries(ActiveState.ACTIVE).get(0); 
						if (i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
							report (c, c.getEffectiveTime(), d, "Inactive description of an active concept should not have a 'Concept non-current' indicator", cdLegacy, d, i);
						}
					}
				}
			}
		}
	}

	private void validate(Concept c, InactivationIndicatorEntry i, String cardinality, String requiredAssociation, String legacy) throws TermServerScriptException {
		int minAssocs = Character.getNumericValue(cardinality.charAt(0));
		char maxStr = cardinality.charAt(cardinality.length() - 1);
		int maxAssocs = maxStr == '*' ? Integer.MAX_VALUE : Character.getNumericValue(maxStr);
		
		String data = c.getHistorialAssociations(ActiveState.ACTIVE).stream()
				.map(h->h.toString())
				.collect(Collectors.joining(",\n"));
		String inactStr = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()).toString();
		String reqAssocStr = requiredAssociation == null? "None" : SnomedUtils.translateHistoricalAssociation(requiredAssociation).toString();
		
		//Special case, we're getting limited inactivations with both SameAs and Was A attributes.  Record stats, but skip
		if (i.getInactivationReasonId().equals(SCTID_INACT_LIMITED) || i.getInactivationReasonId().equals(SCTID_INACT_MOVED_ELSEWHERE)) {
			String typesOfAssoc = c.getHistorialAssociations(ActiveState.ACTIVE).stream()
					.map(h->SnomedUtils.translateHistoricalAssociation(h.getRefsetId()).toString())
					.collect(Collectors.joining(", "));
			typesOfAssoc = typesOfAssoc.isEmpty()? "No associations" : typesOfAssoc;
			incrementSummaryInformation(inactStr +" inactivation with " + typesOfAssoc);
			return;
		}
		
		//First check cardinality
		int assocCount = c.getHistorialAssociations(ActiveState.ACTIVE).size();
		if (assocCount > maxAssocs) {
			report (c, c.getEffectiveTime(), inactStr + " inactivation must have no more than " + maxStr + " historical associations.", legacy,  data);
		} else if  (assocCount < minAssocs) {
			report (c, c.getEffectiveTime(), inactStr + " inactivation must have at least " + minAssocs + " historical associations.", legacy, data);
		} else {
			//Now check association is appropriate for the inactivation indicator used
			for (HistoricalAssociationEntry h : c.getHistorialAssociations(ActiveState.ACTIVE)) {
				if (!h.getRefsetId().equals(requiredAssociation)) {
					String assocStr = SnomedUtils.translateHistoricalAssociation(h.getRefsetId()).toString();
					//If we found a "WAS_A" then just record the stat for that
					String msg = inactStr + " inactivation requires " + reqAssocStr + " historical association.  Found: " + assocStr;
					if (assocStr.equals(HistoricalAssociation.WAS_A.toString())) {
						incrementSummaryInformation(msg);
					} else {
						report (c, c.getEffectiveTime(), msg, legacy, data);
					}
				}
			}
		}
		
	}
	
	private String isLegacy(Component c) {
		return StringUtils.isEmpty(c.getEffectiveTime()) ? "N" : "Y";
	}

}