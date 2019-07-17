
package org.ihtsdo.termserver.scripting.domain;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.termserver.scripting.TermServerScriptException;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class RefsetEntry extends Component implements RF2Constants {

    @SerializedName("id")
    @Expose
	protected String id;
    @SerializedName("effectiveTime")
    @Expose
    protected String effectiveTime;
    @SerializedName("released")
    @Expose
    protected Boolean released;
    @SerializedName("active")
    @Expose
    protected Boolean active;
    @SerializedName("moduleId")
    @Expose
    protected String moduleId;
    @SerializedName("referencedComponent")
    @Expose
    protected ReferencedComponent referencedComponent;
    @SerializedName("referenceSetId")
    @Expose
    protected String refsetId;
    @SerializedName("valueId")
    @Expose
    protected String valueId;
    @SerializedName("commitComment")
    @Expose
    protected String commitComment = "TermserverScript update";

    /**
     * No args constructor for use in serialization
     * 
     */
    public RefsetEntry() {
    }

    /**
     * 
     * @param released
     * @param id
     * @param referenceSetId
     * @param moduleId
     * @param valueId
     * @param active
     * @param referencedComponent
     */
    public RefsetEntry(String id, Boolean released, Boolean active, String moduleId, ReferencedComponent referencedComponent, String referenceSetId, String valueId) {
        super();
        this.id = id;
        this.released = released;
        this.active = active;
        this.moduleId = moduleId;
        this.referencedComponent = referencedComponent;
        this.refsetId = referenceSetId;
        this.valueId = valueId;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Boolean getReleased() {
        return released;
    }

    public void setReleased(Boolean released) {
        this.released = released;
    }

    public Boolean getActive() {
        return active;
    }
    
    public boolean isActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public ReferencedComponent getReferencedComponent() {
        return referencedComponent;
    }

    public void setReferencedComponent(ReferencedComponent referencedComponent) {
        this.referencedComponent = referencedComponent;
    }
    
	public void setReferencedComponentId(String id) {
		setReferencedComponent(new ReferencedComponent(id));
	}
	
	public String getReferencedComponentId() {
		return referencedComponent.toString();
	}

    public String getReferenceSetId() {
        return refsetId;
    }

    public void setReferenceSetId(String referenceSetId) {
        this.refsetId = referenceSetId;
    }

    public String getValueId() {
        return valueId;
    }

    public void setValueId(String valueId) {
        this.valueId = valueId;
    }

	@Override
	public String getReportedName() {
		return refsetId;
	}

	@Override
	public String getReportedType() {
		return "RefsetEntry";
	}
	
	public String toString() {
		return id;
	}

	public String getCommitComment() {
		return commitComment;
	}
	
	public void setCommitComment(String comment) {
		commitComment = comment;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	@Override
	public String[] toRF2() throws TermServerScriptException {
		throw new NotImplementedException();
	}

	@Override
	public ComponentType getComponentType() {
		// TODO Auto-generated method stub
		return null;
	}

}
