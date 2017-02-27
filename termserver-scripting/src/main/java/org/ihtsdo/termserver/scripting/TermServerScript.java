package org.ihtsdo.termserver.scripting;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.client.SCAClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipSerializer;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import us.monoid.json.JSONException;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

public abstract class TermServerScript implements RF2Constants {
	
	protected static boolean debug = true;
	protected static boolean dryRun = true;
	protected static int dryRunCounter = 0;
	protected static int taskThrottle = 30;
	protected static int conceptThrottle = 20;
	protected String env;
	protected String url = environments[0];
	protected boolean useAuthenticatedCookie = false;
	protected SnowOwlClient tsClient;
	protected SCAClient scaClient;
	protected String authenticatedCookie;
	protected Resty resty = new Resty();
	protected String project;
	protected String projectPath;
	public static final int maxFailures = 5;
	protected int restartPosition = NOT_SET;
	private static Date startTime;
	private static Map<String, Object> summaryDetails = new HashMap<String, Object>();
	private static String summaryText = "";
	protected File inputFile;
	protected File reportFile;
	protected File outputDir;
	
	protected Scanner STDIN = new Scanner(System.in);
	
	public static String CONCEPTS_IN_FILE = "Concepts in file";
	public static String CONCEPTS_PROCESSED = "Concepts processed";
	public static String REPORTED_NOT_PROCESSED = "Reported not processed";
	public static String CRITICAL_ISSUE = "CRITICAL ISSUE";
	protected String inputFileDelimiter = CSV_FIELD_DELIMITER;
	protected String tsRoot = "MAIN/"; //"MAIN/2016-01-31/SNOMEDCT-DK/";
	
	protected static Gson gson;
	static {
		GsonBuilder gsonBuilder = new GsonBuilder();
		//gsonBuilder.registerTypeAdapter(Concept.class, new ConceptDeserializer());
		gsonBuilder.registerTypeAdapter(Relationship.class, new RelationshipSerializer());
		gsonBuilder.setPrettyPrinting();
		gsonBuilder.excludeFieldsWithoutExposeAnnotation();
		gson = gsonBuilder.create();
	}
	
	protected static GraphLoader graph = GraphLoader.getGraphLoader();

	public enum REPORT_ACTION_TYPE { API_ERROR, CONCEPT_CHANGE_MADE, INFO, UNEXPECTED_CONDITION,
									 RELATIONSHIP_ADDED, RELATIONSHIP_REMOVED, DESCRIPTION_CHANGE_MADE, 
									 NO_CHANGE, VALIDATION_ERROR};
									 
	public enum SEVERITY { NONE, LOW, MEDIUM, HIGH, CRITICAL }; 

	public String getScriptName() {
		return this.getClass().getSimpleName();
	}
	
	public String getAuthenticatedCookie() {
		return authenticatedCookie;
	}
	
	public static int getNextDryRunNum() {
		return ++dryRunCounter;
	}

	public void setAuthenticatedCookie(String authenticatedCookie) {
		this.authenticatedCookie = authenticatedCookie;
	}
	
	private static String[] envKeys = new String[] {"local","dev","uat","flat-uat","prod","dev","uat","prod"};

	private static String[] environments = new String[] {	"http://localhost:8080/",
															"https://dev-term.ihtsdotools.org/",
															"https://uat-term.ihtsdotools.org/",
															"https://uat-flat-termserver.ihtsdotools.org/",
															"https://authoring.ihtsdotools.org/",
															"https://dev-ms-authoring.ihtsdotools.org/",
															"https://uat-ms-authoring.ihtsdotools.org/",
															"https://prod-ms-authoring.ihtsdotools.org/",
	};
	
	public static void println (String msg) {
		System.out.println (msg);
	}
	
	public static void print (String msg) {
		System.out.print (msg);
	}
	
	public static void debug (String msg) {
		System.out.println (msg);
	}
	
	public static void warn (String msg) {
		System.out.println ("*** " + msg);
	}
	
	public static String getMessage (Exception e) {
		String msg = e.getMessage();
		Throwable cause = e.getCause();
		if (cause != null) {
			msg += " caused by " + cause.getMessage();
		}
		return msg;
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		
		if (args.length < 3) {
			println("Usage: java <TSScriptClass> [-a author] [-b <batchSize>] [-r <restart lineNum>] [-c <authenticatedCookie>] [-d <Y/N>] [-p <projectName>] <batch file Location>");
			println(" d - dry run");
			System.exit(-1);
		}
		boolean isProjectName = false;
		boolean isCookie = false;
		boolean isDryRun = false;
		boolean isRestart = false;
		boolean isTaskThrottle = false;
		boolean isConceptThrottle = false;
		boolean isOutputDir = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-p")) {
				isProjectName = true;
			} else if (thisArg.equals("-c")) {
				isCookie = true;
			} else if (thisArg.equals("-d")) {
				isDryRun = true;
			} else if (thisArg.equals("-o")) {
				isOutputDir = true;
			} else if (thisArg.equals("-r")) {
				isRestart = true;
			} else if (thisArg.equals("-t") || thisArg.equals("-t1")) {
				isTaskThrottle = true;
			} else if (thisArg.equals("-t2")) {
				isConceptThrottle = true;
			} else if (isProjectName) {
				project = thisArg;
				isProjectName = false;
			} else if (isDryRun) {
				dryRun = thisArg.toUpperCase().equals("Y");
				isDryRun = false;
			} else if (isRestart) {
				restartPosition = Integer.parseInt(thisArg);
				isRestart = false;
			} else if (isTaskThrottle) {
				taskThrottle = Integer.parseInt(thisArg);
				isTaskThrottle = false;
			} else if (isConceptThrottle) {
				conceptThrottle = Integer.parseInt(thisArg);
				isConceptThrottle = false;
			} else if (isCookie) {
				authenticatedCookie = thisArg;
				isCookie = false;
			} else if (isOutputDir) {
				File possibleDir = new File(thisArg);
				if (possibleDir.exists() && possibleDir.isDirectory() && possibleDir.canRead()) {
					outputDir = possibleDir;
				} else {
					println ("Unable to use directory " + possibleDir.getAbsolutePath() + " for output.");
				}
				isOutputDir = false;
			} else {
				File possibleFile = new File(thisArg);
				if (possibleFile.exists() && !possibleFile.isDirectory() && possibleFile.canRead()) {
					inputFile = possibleFile;
				} else {
					println ("Warning, unable to read possible input file " + possibleFile.getAbsolutePath());
				}
			}
		}
		
		println ("Select an environment ");
		for (int i=0; i < environments.length; i++) {
			println ("  " + i + ": " + environments[i]);
		}
		
		print ("Choice: ");
		String choice = STDIN.nextLine().trim();
		int envChoice = Integer.parseInt(choice);
		url = environments[envChoice];
		env = envKeys[envChoice];
	
		if (authenticatedCookie == null) {
			print ("Please enter your authenticated cookie for connection to " + url + " : ");
			authenticatedCookie = STDIN.nextLine().trim();
		}
		//TODO Make calls through client objects rather than resty direct and remove this member 
		resty.withHeader("Cookie", authenticatedCookie);  
		scaClient = new SCAClient(url, authenticatedCookie);
		initialiseSnowOwlClient();
		
		print ("Specify Project " + (project==null?": ":"[" + project + "]: "));
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			project = response;
			projectPath = tsRoot + project;
		}
		
		if (restartPosition != NOT_SET) {
			print ("Restarting from line [" +restartPosition + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				restartPosition = Integer.parseInt(response);
			}
		}
		
		if (taskThrottle > 0) {
			print ("Time delay between tasks (throttle) seconds [" +taskThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				taskThrottle = Integer.parseInt(response);
			}
		}
		
		if (conceptThrottle > 0) {
			print ("Time delay between concepts (throttle) seconds [" +conceptThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				conceptThrottle = Integer.parseInt(response);
			}
		}

		if (restartPosition == 0) {
			println ("Restart position given as 0 but line numbering starts from 1.  Starting at line 1.");
			restartPosition = 1;
		}
	}
	
	protected void initialiseSnowOwlClient() {
		if (useAuthenticatedCookie) {
			tsClient = new SnowOwlClient(url + "snowowl/snomed-ct/v2", authenticatedCookie);
		} else {
			tsClient = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");
		}
	}
	
	
	protected void loadProjectSnapshot(boolean fsnOnly) throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		File snapShotArchive = new File (project + "_" + env + ".zip");
		//Do we already have a copy of the project locally?  If not, recover it.
		if (!snapShotArchive.exists()) {
			println ("Recovering current state of " + project + " from TS (" + env + ")");
			tsClient.export(tsRoot + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, snapShotArchive);
		}
		GraphLoader gl = GraphLoader.getGraphLoader();
		println ("Loading archive contents into memory...");
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(snapShotArchive));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						if (fileName.contains("sct2_Relationship_Snapshot")) {
							println("Loading Relationship File.");
							gl.loadRelationships(CharacteristicType.INFERRED_RELATIONSHIP, zis, true);
						} else if (fileName.contains("sct2_Description_Snapshot")) {
							println("Loading Description File.");
							gl.loadDescriptionFile(zis, fsnOnly);
						}
						//If we're loading all terms, load the language refset as well
						if (!fsnOnly && (fileName.contains("EnglishSnapshot") || fileName.contains("LanguageSnapshot-en"))) {
							println("Loading Language Reference Set File - " + fileName);
							gl.loadLanguageFile(zis);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				try{
					zis.closeEntry();
					zis.close();
				} catch (Exception e){} //Well, we tried.
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to extract project state from archive " + snapShotArchive.getName(), e);
		}
	}
	
	protected Concept loadConcept(Concept concept, String branchPath) throws TermServerScriptException {
		debug ("Loading: " + concept + " from TS.");
		try {
			//In a dry run situation, the task branch is not created so use the Project instead
			if (dryRun) {
				branchPath = branchPath.substring(0, branchPath.lastIndexOf("/"));
			}
			JSONResource response = tsClient.getConcept(concept.getConceptId(), branchPath);
			String json = response.toObject().toString();
			concept = gson.fromJson(json, Concept.class);
			concept.setLoaded(true);
		} catch (SnowOwlClientException | JSONException | IOException e) {
			throw new TermServerScriptException("Failed to recover " + concept + " from TS",e);
		}
		return concept;
	}
	
	protected List<Concept> processFile() throws TermServerScriptException {
		return processFile(inputFile);
	}
	
	protected List<Concept> processFile(File file) throws TermServerScriptException {
		List<Concept> allConcepts = new ArrayList<Concept>();
		debug ("Loading input file " + file.getAbsolutePath());
		try {
			List<String> lines = Files.readLines(file, Charsets.UTF_8);
			lines = SnomedUtils.removeBlankLines(lines);
			
			//Are we restarting the file from some line number
			int startPos = (restartPosition == NOT_SET)?0:restartPosition - 1;
			Concept c;
			for (int lineNum = startPos; lineNum < lines.size(); lineNum++) {
				if (lineNum == 0) {
					//continue; //skip header row  //Current file format has no header
				}
				
				//File format Concept Type, SCTID, FSN with string fields quoted.  Strip quotes also.
				String[] lineItems = lines.get(lineNum).replace("\"", "").split(inputFileDelimiter);
				if (lineItems.length >= 1) {
					try{
						c = loadLine(lineItems);
					} catch (Exception e) {
						throw new TermServerScriptException("Failed to load line " + lineNum,e);
					}
					if (c != null) {
						allConcepts.add(c);
					} else {
						debug ("Malformed line " + lineNum + ": " + lines.get(lineNum));
					}
				} else {
					debug ("Skipping blank line " + lineNum);
				}
			}
			addSummaryInformation(CONCEPTS_IN_FILE, allConcepts);

		} catch (FileNotFoundException e) {
			throw new TermServerScriptException("Unable to open input file " + file.getAbsolutePath(), e);
		} catch (IOException e) {
			throw new TermServerScriptException("Error while reading input file " + file.getAbsolutePath(), e);
		}
		return allConcepts;
	}
	
	protected abstract Concept loadLine(String[] lineItems) throws TermServerScriptException;

	public String getProject() {
		return project;
	}

	public void setProject(String project) {
		this.project = project;
	}
	
	public void startTimer() {
		startTime = new Date();
	}
	
	public void addSummaryInformation(String item, Object detail) {
		summaryDetails.put(item, detail);
	}
	
	public void incrementSummaryInformation(String key, int incrementAmount) {
		if (!summaryDetails.containsKey(key)) {
			summaryDetails.put (key, new Integer(0));
		}
		int newValue = ((Integer)summaryDetails.get(key)).intValue() + incrementAmount;
		summaryDetails.put(key, newValue);
	}
	
	public void storeRemainder(String start, String remove1, String remove2, String storeAs) {
		Collection<?> differences = new ArrayList((Collection<?>)summaryDetails.get(start));
		Collection<?> removeList = (Collection<?>)summaryDetails.get(remove1);
		differences.removeAll(removeList);
		if (remove2 != null && !remove2.isEmpty()) {
			removeList = (Collection<?>)summaryDetails.get(remove2);
			differences.removeAll(removeList);
		}
		summaryDetails.put(storeAs, differences.toString());
	}
	
	public void finish() {
		println ("===========================================");
		Date endTime = new Date();
		SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
		//I've not had to adjust for timezones when creating a date before?
		if (startTime != null) {
			Date diff = new Date(endTime.getTime() - startTime.getTime() + (endTime.getTimezoneOffset() * 60 * 1000));
			recordSummaryText ("Completed processing in " + sdf.format(diff));
			recordSummaryText ("Started at: " + startTime);
		}
		recordSummaryText ("Finished at: " + endTime);
		List<String> criticalIssues = new ArrayList<String>();
		
		for (Map.Entry<String, Object> summaryDetail : summaryDetails.entrySet()) {
			String key = summaryDetail.getKey();
			Object value = summaryDetail.getValue();
			String display = "";
			if (value instanceof Collection) {
				display += ((Collection<?>)value).size();
			} else if (key.startsWith(CRITICAL_ISSUE)) {
				criticalIssues.add(key + ": " + value.toString());
				continue;
			} else {
				display = value.toString();
			}
			recordSummaryText (key + ": " + display);
		}
		if (summaryDetails.containsKey("Tasks created") && summaryDetails.containsKey(CONCEPTS_PROCESSED) ) {
			double c = (double)((Collection<?>)summaryDetails.get("Concepts processed")).size();
			double t = (double)((Integer)summaryDetails.get("Tasks created")).intValue();
			double avg = Math.round((c/t) * 10) / 10.0;
			recordSummaryText ("Concepts per task: " + avg);
		}
		
		if (criticalIssues.size() > 0) {
			recordSummaryText ("\nCritical Issues Encountered\n========================");
			for (String thisCriticalIssue : criticalIssues) {
				recordSummaryText(thisCriticalIssue);
			}
		}
		
	}
	
	private synchronized void recordSummaryText(String msg) {
		println (msg);
		msg = msg.replace("\n", "\n</br>");
		summaryText += msg + "\n<br/>";
	}
	
	public static String getSummaryText() {
		return summaryText;
	}
	
	protected void writeToFile(String line) {
		try(	OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(reportFile, true), StandardCharsets.UTF_8);
				BufferedWriter bw = new BufferedWriter(osw);
				PrintWriter out = new PrintWriter(bw))
		{
			out.println(line);
		} catch (Exception e) {
			println ("Unable to output report line: " + line + " due to " + e.getMessage());
		}
	}
	
	protected void initialiseReportFile(String columnHeaders) throws IOException {
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "results_" + SnomedUtils.deconstructFilename(inputFile)[1] + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile (columnHeaders);
	}

	protected File ensureFileExists(String fileName) throws TermServerScriptException {
		File file = new File(fileName);
		try {
			if (!file.exists()) {
				file.getParentFile().mkdirs();
				file.createNewFile();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to create file " + fileName,e);
		}
		return file;
	}

}