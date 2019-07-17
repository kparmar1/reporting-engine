package org.ihtsdo.termserver.scripting.domain;

import java.util.UUID;

//id	effectiveTime	active	moduleId	refsetId	referencedComponentId	inactivationReasonId
public class InactivationIndicatorEntry extends RefsetEntry {

	private boolean dirty = false;
	private boolean isDeleted = false;
	private String deletionEffectiveTime;
	
	public InactivationIndicatorEntry clone(String newComponentSctId) {
		InactivationIndicatorEntry clone = new InactivationIndicatorEntry();
		clone.id = UUID.randomUUID().toString();
		clone.effectiveTime = null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.setReferencedComponentId(newComponentSctId);
		clone.valueId = this.valueId;
		clone.dirty = true; //New components need to be written to any delta
		return clone;
	}

	private static InactivationIndicatorEntry withDefaults() {
		InactivationIndicatorEntry i = new InactivationIndicatorEntry();
		i.setId(UUID.randomUUID().toString());
		i.setModuleId(SCTID_CORE_MODULE);
		i.setActive(true);
		i.setDirty();
		return i;
	}
	
	public static InactivationIndicatorEntry withDefaults (Concept c) {
		InactivationIndicatorEntry i = withDefaults();
		i.setRefsetId(SCTID_CON_INACT_IND_REFSET);
		i.setReferencedComponentId(c.getId());
		return i;
	}
	
	public static InactivationIndicatorEntry withDefaults (Description d) {
		InactivationIndicatorEntry i = withDefaults();
		i.setRefsetId(SCTID_DESC_INACT_IND_REFSET);
		i.setReferencedComponentId(d.getId());
		i.setDirty();
		return i;
	}
	
	public String toString() {
		String activeIndicator = isActive()?"":"*";
		return "[" + activeIndicator + "IA]:" + id + " - " + refsetId + " : " + referencedComponent + "->" + getInactivationReasonId();
	}
	
	public String[] toRF2() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				(active?"1":"0"),
				moduleId, refsetId,
				referencedComponent.toString(),
				getInactivationReasonId()
		};
	}
	
	public String[] toRF2Deletion() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				deletionEffectiveTime,
				(active?"1":"0"),
				"1",
				moduleId, refsetId,
				referencedComponent.toString(),
				getInactivationReasonId()
		};
	}

	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getEffectiveTime() {
		return effectiveTime;
	}
	public void setEffectiveTime(String effectiveTime) {
		if (this.effectiveTime != null && !this.effectiveTime.isEmpty() && effectiveTime == null) {
			//Are we resetting this component to mark a change?
			dirty = true;
		}
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
		if (this.active != null && this.active != newActiveState) {
			setDirty();
			setEffectiveTime(null);
		}
		this.active = newActiveState;
	}
	public String getRefsetId() {
		return refsetId;
	}
	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}
	public String getInactivationReasonId() {
		return valueId;
	}
	public void setInactivationReasonId(String inactivationReasonId) {
		if (getInactivationReasonId() != null && !this.getInactivationReasonId().equals(inactivationReasonId)) {
			dirty = true;
		}
		setValueId(inactivationReasonId);
	}

	public boolean isDirty() {
		return dirty;
	}
	
	public void setDirty() {
		dirty = true;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void delete(String deletionEffectiveTime) {
		this.isDeleted = true;
		this.deletionEffectiveTime = deletionEffectiveTime;
	}
	
	public static InactivationIndicatorEntry fromRf2(String[] lineItems) {
		InactivationIndicatorEntry i = new InactivationIndicatorEntry();
		i.setId(lineItems[INACT_IDX_ID]);
		i.setEffectiveTime(lineItems[INACT_IDX_EFFECTIVETIME]);
		i.setActive(lineItems[INACT_IDX_ACTIVE].equals("1"));
		i.setModuleId(lineItems[INACT_IDX_MODULID]);
		i.setRefsetId(lineItems[INACT_IDX_REFSETID]);
		i.setReferencedComponentId(lineItems[INACT_IDX_REFCOMPID]);
		i.setInactivationReasonId(lineItems[INACT_IDX_REASON_ID]);
		return i;
	}

	@Override
	public ComponentType getComponentType() {
		return ComponentType.ATTRIBUTE_VALUE;
	}

	@Override
	public String getReportedName() {
		return getId();
	}

	@Override
	public String getReportedType() {
		return getComponentType().toString();
	}
	
	@Override 
	public boolean equals(Object o) {
		if (o instanceof InactivationIndicatorEntry) {
			return this.getId().equals(((InactivationIndicatorEntry)o).getId());
		}
		return false;
	}
}
