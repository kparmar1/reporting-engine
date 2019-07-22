package org.ihtsdo.termserver.scripting.batchimport;

import java.util.*;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;

public class BatchImportFormat implements RF2Constants {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(BatchImportFormat.class);

	public enum FORMAT { SIRS, ICD11, LOINC }

	public enum FIELD { SCTID, PARENT_1, PARENT_2, FSN, CAPS, FSN_ROOT, CAPSFSN, PREF_TERM, NOTES, SEMANTIC_TAG, EXPRESSION, ORIG_REF}

	public static int FIELD_NOT_FOUND = -1;
	public static String RANGE_SEPARATOR = "-";
	public static int FIRST_NOTE = 0;
	public static int LAST_NOTE = 1;
	public static final String NEW_LINE = "\n";
	public static final String SYNONYM = "Synonym";
	public static final String NOTE = "Note";
	public static final String NOTES = "Notes";
	public static final String EXPRESSION = "Expression";
	private static final String NEW_SCTID = "NEW_SCTID";  //Indicates we'll pass blank to TS
	private static final String NULL_STR = "NULL";
	
	private FORMAT format;
	private Map<FIELD, String> fieldMap;
	private int[] documentationFields = new int[0];
	private int[] synonymFields = new int[0];
	private int[] notesFields = new int[0];
	private boolean definesByExpression = false;
	private boolean constructsFSN = false;
	private boolean multipleTerms = false;
	
	//There are variable numbers of Synonym and Notes fields, so they're optional and we'll work them out at runtime
	public static String[] SIRS_HEADERS = {"Request Id","Topic","Local Code","Local Term","Fully Specified Name","Semantic Tag",
			"Preferred Term","Terminology(1)","Parent Concept Id(1)","UMLS CUI","Definition","Proposed Use","Justification"};
	public static String[] ICD11_HEADERS = {"icd11","sctid","fsn","TERM1","US1","GB1","TERM2","US2","GB2","TERM3","US3","GB3","TERM4","US4","GB4","expression"};  //Also note and synonym, but we'll detect those dynamically as there can be more than 1.
	public static String[] LOINC_HEADERS = {"SCTID","Parent_1","Parent_2","FSN","CAPSFSN","TERM1","US1","GB1","CAPS1","TERM2","US2","GB2","CAPS2","TERM3","US3","GB3","CAPS3","TERM4","US4","GB4","CAPS4","TERM5","US5","GB5","CAPS5","Associated LOINC Part(s)","Reference link(s)","Notes"};

	public static String ADDITIONAL_RESULTS_HEADER = "OrigRow,Loaded,Import Result,SCTID Created";
	
	public static Map<FORMAT, String[]> HEADERS_MAP = new HashMap<>();
	static {
		HEADERS_MAP.put(FORMAT.SIRS, SIRS_HEADERS);
		HEADERS_MAP.put(FORMAT.ICD11, ICD11_HEADERS);
		HEADERS_MAP.put(FORMAT.LOINC, LOINC_HEADERS);
	}
	public static Map<FIELD, String>SIRS_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		SIRS_MAP.put(FIELD.ORIG_REF, "0");
		SIRS_MAP.put(FIELD.SCTID, "2");
		SIRS_MAP.put(FIELD.PARENT_1, "8");
		SIRS_MAP.put(FIELD.FSN_ROOT, "4");
		SIRS_MAP.put(FIELD.SEMANTIC_TAG, "5");
	}
	
	public static Map<FIELD, String>ICD11_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		ICD11_MAP.put(FIELD.ORIG_REF, "0");
		ICD11_MAP.put(FIELD.SCTID, "1");
		ICD11_MAP.put(FIELD.FSN, "2");
		ICD11_MAP.put(FIELD.EXPRESSION, "15");
	}
	
	public static Map<FIELD, String>LOINC_MAP = new HashMap<>();
	static {
		//Note that these are 0-based indexes
		LOINC_MAP.put(FIELD.SCTID, "0");
		LOINC_MAP.put(FIELD.PARENT_1, "1");
		LOINC_MAP.put(FIELD.PARENT_2, "2");
		LOINC_MAP.put(FIELD.FSN, "3");
		LOINC_MAP.put(FIELD.CAPSFSN, "4");
	}
	
	public static int[] LOINC_Documentation = new int[] {25,26,27};

	private static BatchImportFormat create(FORMAT format) throws TermServerScriptException {
		//Booleans are:  defines by expression, constructs FSN, multipleTerms
		if (format == FORMAT.SIRS) {
			return new BatchImportFormat(FORMAT.SIRS, SIRS_MAP, null, false, true, false);
		} else if (format == FORMAT.ICD11) {
			return new BatchImportFormat(FORMAT.ICD11, ICD11_MAP, null, true, false, true);
		} else if (format == FORMAT.LOINC) {
			
			return new BatchImportFormat(FORMAT.LOINC, LOINC_MAP, LOINC_Documentation, false, false, true);
		} else {
			throw new TermServerScriptException("Unsupported format: " + format);
		}
	}
	
	private BatchImportFormat(FORMAT format, Map<FIELD, String> fieldMap, int[] documentationFields, boolean definesByExpression, boolean constructsFSN, boolean multipleTerms) {
		this.format = format;
		this.fieldMap = fieldMap;
		this.definesByExpression = definesByExpression;
		this.constructsFSN = constructsFSN;
		this.multipleTerms  = multipleTerms;
		if (documentationFields != null) {
			this.documentationFields = documentationFields;
		}
	}
	
	public int getIndex(FIELD field) throws TermServerScriptException {
		if (fieldMap.containsKey(field)) {
			return Integer.parseInt(fieldMap.get(field));
		}
		return FIELD_NOT_FOUND;
	}
	
	public Concept createConcept(CSVRecord row) throws TermServerScriptException {
		Concept concept;
		String sctid = row.get(getIndex(FIELD.SCTID)).trim();
		if (sctid.isEmpty() || sctid.equals(NEW_SCTID) ) {
			sctid = null;
		}
		
		if (definesByExpression) {
			String expressionStr = row.get(getIndex(FIELD.EXPRESSION)).trim();
			concept = createConcept(sctid, row, expressionStr);
		} else {
			ArrayList<String> parents = new ArrayList<>();
			parents.add(row.get(getIndex(FIELD.PARENT_1)));
			int parent2Idx = getIndex(FIELD.PARENT_2);
			if (parent2Idx != FIELD_NOT_FOUND && !row.get(parent2Idx).isEmpty()) {
				parents.add(row.get(parent2Idx));
			}
			concept = createConcept(sctid, parents, row);
		}
		
		if (multipleTerms) {
			String[] headers = getHeaders();
			for (int i = 0; i < headers.length; i++) {
				if (headers[i].toLowerCase().startsWith("term")) {
					String termStr = row.get(i);
					if (!termStr.isEmpty() && !termStr.toUpperCase().equals(NULL_STR)) {
						char usAccept = row.get(i+1).isEmpty()? null : row.get(i+1).charAt(0);
						char gbAccept =  row.get(i+2).isEmpty()? null : row.get(i+2).charAt(0);
						Description term = createDescription(termStr, false, usAccept, gbAccept);
						i += 3;
						//Do we have a CAPS indicator here?
						if (headers[i].toLowerCase().startsWith("caps")) {
							throw new IllegalStateException("Unexpected caps indicator");
							//term.setCaseSensitivity(row.get(i));
						} else {
							i--;  //Move back one because loop will take us on to the next term.
						}
						concept.addDescription(term);
					}
				}
			}
		}
		
		return concept;
	}

	private Concept createConcept(String sctid, ArrayList<String> parents, CSVRecord row) {
		throw new IllegalStateException ("TODO - Code for non-expression concepts");
	}

	private Concept createConcept(String sctid, CSVRecord row, String expressionStr) throws TermServerScriptException {
		Concept c = new Concept(sctid);
		c.setModuleId(SCTID_CORE_MODULE);
		c.addAllIssues(getAllNotes(c, row));
		String fsnStr = row.get(getIndex(FIELD.FSN));
		Description fsn = Description.withDefaults(fsnStr, DescriptionType.FSN, Acceptability.PREFERRED);
		c.addDescription(fsn);
		
		GraphLoader gl = GraphLoader.getGraphLoader();
		BatchImportExpression expression = BatchImportExpression.parse(expressionStr, SCTID_MODEL_MODULE);
		c.setDefinitionStatus(expression.getDefinitionStatus());
		for (String parentStr : expression.getFocusConcepts()) {
			Relationship parentRel = new Relationship (c, IS_A, gl.getConcept(parentStr), UNGROUPED);
			c.addRelationship(parentRel);
		}
		
		for (RelationshipGroup group : expression.getAttributeGroups()) {
			c.addRelationshipGroup(group, null);
		}
		return c;
	}
	
	private Description createDescription(String termStr, boolean b, char usAcceptStr, char gbAcceptStr) {
		Description d = Description.withDefaults(termStr,  DescriptionType.SYNONYM);
		Acceptability usAccept = SnomedUtils.translateAcceptability(usAcceptStr);
		Acceptability gbAccept = SnomedUtils.translateAcceptability(gbAcceptStr);
		
		if (!usAccept.equals(Acceptability.NONE)) {
			d.getAcceptabilityMap().put(US_ENG_LANG_REFSET, usAccept);
		}
		
		if (!gbAccept.equals(Acceptability.NONE)) {
			d.getAcceptabilityMap().put(GB_ENG_LANG_REFSET, gbAccept);
		}
		return d;
	}

	public List<String> getAllNotes(Concept c, CSVRecord row) throws TermServerScriptException {
		List<String> notes = new ArrayList<>();
		for (int notesField : notesFields) {
			try {
				String thisNote = row.get(notesField);
				if (thisNote != null && thisNote.trim().length() > 0) {
					notes.add(thisNote);
				}
			} catch (Exception e) {
				LOGGER.error("Failed to recover note at field {} for concept {}", notesField, c, e);
			}
		}
		return notes;
	}
/*	
	public List<String> getAllSynonyms(Concept thisConcept) throws TermServerScriptException {
		List<String> synList = new ArrayList<>();
		for (int synonymField : synonymFields) {
			String thisSyn = thisConcept.getRow().get(synonymField);
			if (thisSyn != null && thisSyn.trim().length() > 0) {
				synList.add(thisSyn);
			}
		}
		return synList;
	}*/

	public static BatchImportFormat determineFormat(CSVRecord header) throws TermServerScriptException {
		BatchImportFormat thisFormat = null;
		for (Map.Entry<FORMAT, String[]> thisFormatHeaders : HEADERS_MAP.entrySet()) {
			FORMAT checkFormat = thisFormatHeaders.getKey();
			String[] checkHeaders = thisFormatHeaders.getValue();
			boolean mismatchDetected = false;
			List<Integer> notesIndexList = new ArrayList<>();
			List<Integer> synonymIndexList = new ArrayList<>();
			for (int colIdx=0; colIdx < header.size() && !mismatchDetected ;colIdx++) {
				//The first column might have a BOM - byte order mark
				String headerColumn = header.get(colIdx).replace("\uFEFF", "");
				
				if (colIdx < checkHeaders.length && !headerColumn.equalsIgnoreCase(checkHeaders[colIdx])) {
					LOGGER.info("File is not {} format because header col {}: '{}' is not '{}' as required by that format.", checkFormat, colIdx, header.get(colIdx), checkHeaders[colIdx]);
					mismatchDetected = true;
				}
				
				if (headerColumn.equalsIgnoreCase(NOTE)) {
					notesIndexList.add(colIdx);
				}
				
				if (headerColumn.equalsIgnoreCase(SYNONYM)) {
					synonymIndexList.add(colIdx);
				}
			}
			if (!mismatchDetected) {
				LOGGER.info("File Batch Import file format determined to be {}.", checkFormat);
				thisFormat = create(checkFormat);
				thisFormat.notesFields = Ints.toArray(notesIndexList);
				thisFormat.synonymFields = Ints.toArray(synonymIndexList);
				break;
			}
		}
		if (thisFormat == null) {
			throw new TermServerScriptException("File format could not be determined");
		}
		return thisFormat;
	
	}

	public boolean definesByExpression() {
		return definesByExpression;
	}
	
	public boolean constructsFSN() {
		return constructsFSN;
	}

	public String[] getHeaders() throws TermServerScriptException {
		switch (format) {
			case SIRS : return SIRS_HEADERS;
			case ICD11 : return ICD11_HEADERS;
			case LOINC : return LOINC_HEADERS;
		}
		throw new TermServerScriptException("Unrecognised format: " + format);
	}
	
	public int[] getDocumentationFields() {
		return documentationFields;
	}

	public boolean hasMultipleTerms() {
		return multipleTerms;
	}
}
