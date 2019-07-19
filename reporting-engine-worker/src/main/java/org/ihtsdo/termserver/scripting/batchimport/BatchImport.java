package org.ihtsdo.termserver.scripting.batchimport;

import java.io.IOException;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public class BatchImport extends BatchFix implements RF2Constants{
	
	BatchImportFormat format;
	
	protected BatchImport(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		BatchImport fix = new BatchImport(null);
		try {
			fix.useExcel = true;
			fix.inputFileHasHeaderRow = true;
			ReportSheetManager.targetFolderId = "1bO3v1PApVCEc3BWWrKwc525vla7ZMPoE"; //Batch Import
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		Concept newConcept = createConcept(t, c, info);
		//Replace the concept in the task so we have the new SCTID to display
		t.replace(c, newConcept);
		return CHANGE_MADE;
	}

}
