
package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Generated;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Description implements RF2Constants{
	
	public static boolean padTerm = false; //Pads terms front and back with spaces to assist whole word matching.

	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private Boolean active;
	@SerializedName("descriptionId")
	@Expose
	private String descriptionId;
	@SerializedName("conceptId")
	@Expose
	private String conceptId;
	@SerializedName("type")
	@Expose
	private DescriptionType type;
	@SerializedName("lang")
	@Expose
	private String lang;
	@SerializedName("term")
	@Expose
	private String term;
	@SerializedName("caseSignificance")
	@Expose
	private String caseSignificance;
	@SerializedName("acceptabilityMap")
	@Expose
	private Map<String, Acceptability> acceptabilityMap = null;
	@SerializedName("inactivationIndicator")
	@Expose
	private InactivationIndicator inactivationIndicator;
	List<LangRefsetEntry> langRefsetEntries;
	private boolean dirty = false;
	/**
	 * No args constructor for use in serialization
	 * 
	 */
	public Description() {
	}

	/**
	 * 
	 * @param moduleId
	 * @param term
	 * @param conceptId
	 * @param active
	 * @param effectiveTime
	 * @param type
	 * @param descriptionId
	 * @param caseSignificance
	 * @param lang
	 * @param AcceptabilityMap
	 */
	public Description(String effectiveTime, String moduleId, boolean active, String descriptionId, String conceptId, DescriptionType type, String lang, String term, String caseSignificance, Map<String, Acceptability> AcceptabilityMap) {
		this.effectiveTime = effectiveTime;
		this.moduleId = moduleId;
		this.active = active;
		this.descriptionId = descriptionId;
		this.conceptId = conceptId;
		this.type = type;
		this.lang = lang;
		if (padTerm) {
			this.term = " " + term + " ";
		} else {
			this.term = term;
		}
		this.caseSignificance = caseSignificance;
		this.acceptabilityMap = AcceptabilityMap;
	}

	public Description(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean newActiveState) {
		if (this.active != null && !this.active == newActiveState) {
			setDirty();
		}
		this.active = newActiveState;
		this.effectiveTime = null;
		//If we inactivate a description, inactivate all of its LangRefsetEntriesAlso
		if (active == false && this.langRefsetEntries != null) {
			//If we're working with RF2, modify the lang ref set
			for (LangRefsetEntry thisDialect : getLangRefsetEntries()) {
				thisDialect.setEffectiveTime(null);
				thisDialect.setActive(false);
			}
			//If we're working with TS Concepts, remove the acceptability Map
			acceptabilityMap = null;
		}
	}

	public String getDescriptionId() {
		return descriptionId;
	}

	public void setDescriptionId(String descriptionId) {
		this.descriptionId = descriptionId;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public DescriptionType getType() {
		return type;
	}

	public void setType(DescriptionType type) {
		this.type = type;
	}

	public String getLang() {
		return lang;
	}

	public void setLang(String lang) {
		this.lang = lang;
	}

	public String getTerm() {
		return term;
	}

	public void setTerm(String term) {
		//Are we changing the term?
		if (this.term != null && !this.term.equalsIgnoreCase(term)) {
			dirty = true;
		}
		
		if (padTerm) {
			this.term = " " + term + " ";
		} else {
			this.term = term;
		}
	}

	public String getCaseSignificance() {
		return caseSignificance;
	}

	public void setCaseSignificance(String caseSignificance) {
		this.caseSignificance = caseSignificance;
	}

	public Map<String, Acceptability> getAcceptabilityMap() {
		return acceptabilityMap;
	}

	/**
	 * 
	 * @param AcceptabilityMap
	 *	 The AcceptabilityMap
	 */
	public void setAcceptabilityMap(Map<String, Acceptability> AcceptabilityMap) {
		this.acceptabilityMap = AcceptabilityMap;
	}

	@Override
	public String toString() {
		return (descriptionId==null?"NEW":descriptionId) + "[" + conceptId + "]: " + term;
	}

	@Override
	public int hashCode() {
		return term.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if ((other instanceof Description) == false) {
			return false;
		}
		Description rhs = ((Description) other);
		return this.hashCode() == rhs.hashCode();
	}
	
	public Description clone(String newSCTID) {
		Description clone = new Description();
		clone.effectiveTime = null; //New description is unpublished.
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.descriptionId = newSCTID;
		clone.conceptId = this.conceptId;
		clone.type = this.type;
		clone.lang = this.lang;
		clone.term = this.term;
		clone.caseSignificance = this.caseSignificance;
		clone.acceptabilityMap = new HashMap<String, Acceptability>();
		if (this.acceptabilityMap != null) { 
			clone.acceptabilityMap.putAll(this.acceptabilityMap);
		}
		if (langRefsetEntries != null) {
			for (LangRefsetEntry thisDialect : this.getLangRefsetEntries()) {
				//The lang refset entres for the cloned description should also point to it
				LangRefsetEntry thisDialectClone = thisDialect.clone(clone.descriptionId); //will create a new UUID and remove EffectiveTime
				clone.getLangRefsetEntries().add(thisDialectClone);
				thisDialectClone.setActive(true);
			}
		}
		return clone;
	}

	public InactivationIndicator getInactivationIndicator() {
		return inactivationIndicator;
	}

	public void setInactivationIndicator(InactivationIndicator inactivationIndicator) {
		this.inactivationIndicator = inactivationIndicator;
	}

	public void setAcceptablity(String refsetId, Acceptability Acceptability) {
		if (acceptabilityMap == null) {
			acceptabilityMap = new HashMap<String, Acceptability> ();
		}
		acceptabilityMap.put(refsetId, Acceptability);
	}

	public String[] toRF2() throws TermServerScriptException {
		//"id","effectiveTime","active","moduleId","conceptId","languageCode","typeId","term","caseSignificanceId"
		return new String[] {descriptionId, effectiveTime, (active?"1":"0"), moduleId, conceptId, lang,
				SnomedUtils.translateDescType(type), term, caseSignificance};
	}

	public List<LangRefsetEntry> getLangRefsetEntries() {
		if (langRefsetEntries == null) {
			langRefsetEntries = new ArrayList<LangRefsetEntry>();
		}
		return langRefsetEntries;
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState) {
		if (activeState.equals(ActiveState.BOTH)) {
			return getLangRefsetEntries();
		}
		List<LangRefsetEntry> result = new ArrayList<LangRefsetEntry>();
		for (LangRefsetEntry l : getLangRefsetEntries()) {
			if ((activeState.equals(ActiveState.ACTIVE) && l.isActive()) ||
				(activeState.equals(ActiveState.INACTIVE) && !l.isActive()) ) {
				result.add(l);
			}
		}
		return result;
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState, String langRefsetId) {
		return getLangRefsetEntries (activeState, langRefsetId, null); // Return all modules
	}
	
	public List<LangRefsetEntry> getLangRefsetEntries(ActiveState activeState, String langRefsetId, String moduleId) {
		List<LangRefsetEntry> result = new ArrayList<LangRefsetEntry>();
		for (LangRefsetEntry thisLangRefSetEntry : getLangRefsetEntries(activeState)) {
			if (thisLangRefSetEntry.getRefsetId().equals(langRefsetId)) {
				if (moduleId == null || thisLangRefSetEntry.getModuleId().equals(moduleId)) {
					result.add(thisLangRefSetEntry);
				}
			}
		}
		return result;
	}

	public boolean isDirty() {
		return dirty;
	}
	
	/**
	 * @return true if this description is preferred in any dialect.
	 */
	public boolean isPreferred() {
		for (Map.Entry<String, Acceptability> entry: acceptabilityMap.entrySet()) {
			if (entry.getValue().equals(Acceptability.PREFERRED)) {
				return true;
			}
		}
		return false;
	}
	
	public void setDirty() {
		dirty = true;
	}
	
	public void inactivateDescription(InactivationIndicator indicator) {
		this.setActive(false);
		this.setInactivationIndicator(indicator);
	}

}
