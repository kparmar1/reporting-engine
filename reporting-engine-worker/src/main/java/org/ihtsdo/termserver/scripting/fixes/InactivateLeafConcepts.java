package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
 * DRUGS-321, DRUGS-479
 * Inactivate concepts (functionality currently broken in production) 
 * and check that the concept is a leaf node, otherwise this is obviously not safe.
 */
public class InactivateLeafConcepts extends BatchFix implements RF2Constants{
	
	protected InactivateLeafConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		InactivateLeafConcepts fix = new InactivateLeafConcepts(null);
		try {
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Just the FSNs
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = inactivateConcept(task, loadedConcept);
		if (changesMade > 0) {
			save(task, loadedConcept, info);
		}
		return changesMade;
	}

	private int inactivateConcept(Task task, Concept concept) throws TermServerScriptException {
		//Check for this concept being the target of any historical associations and unwire them
		checkAndInactivatateIncomingAssociations(task, concept);
		
		//Check for any children and inactivate them to.  Because we can.
		for (Concept child :  gl.getConcept(concept.getConceptId()).getDescendents(IMMEDIATE_CHILD)) {
			task.add(child);
			doFix(task, child, " as descendant of " + concept);
			report(task, child, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Inactivating child of " + concept);
		}
		concept.setActive(false);
		concept.setEffectiveTime(null);
		concept.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept inactivated");
		return 1;
	}

	private void checkAndInactivatateIncomingAssociations(Task task, Concept c) throws TermServerScriptException {
		if (gl.usedAsHistoricalAssociationTarget(c) == null) {
			return;
		}
		for (AssociationEntry assoc : gl.usedAsHistoricalAssociationTarget(c)) {
			inactivateHistoricalAssociation (task, assoc);
		}
	}

	private void inactivateHistoricalAssociation(Task task, AssociationEntry assoc) throws TermServerScriptException {
		//The source concept can no longer have this historical association, and its
		//inactivation reason must also change to NonConformance.
		Concept incomingConcept = loadConcept(assoc.getReferencedComponentId(), task.getBranchPath());
		incomingConcept.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
		incomingConcept.setAssociationTargets(new AssociationTargets());
		report(task, incomingConcept, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Incoming historical association detached.");
		
		try {
			save(task, incomingConcept, null);
		} catch (Exception e) {
			report(task, incomingConcept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		if (!c.getFsn().equals(lineItems[1])) {
			report((Concept)null, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "FSN failed to match expected value " + lineItems[1]);
		} else if (!c.isActive()) {
			report((Concept)null, c, Severity.HIGH, ReportActionType.NO_CHANGE, "Concept is already inactive");
		} else {
			return Collections.singletonList(c);
		}
		return null;
	}
}
