package org.ihtsdo.termserver.scripting.batchimport;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

public class BatchImport extends BatchFix implements RF2Constants{
	
	BatchImportFormat format;
	
	protected BatchImport(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		BatchImport fix = new BatchImport(null);
		try {
			fix.init(args);
			fix.inputFileHasHeaderRow = true;
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		createConcept(t, c, info);
		return CHANGE_MADE;
	}

	@Override
	protected List<Component> loadLine(String[] items) throws TermServerScriptException {

		return Collections.singletonList(concept);
	}

}
