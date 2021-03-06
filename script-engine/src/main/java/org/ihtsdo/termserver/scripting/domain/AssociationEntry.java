package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

//id	effectiveTime	active	moduleId 	refsetId 	referencedComponentId	targetComponentId
public class AssociationEntry extends RefsetMember implements RF2Constants {

	private String targetComponentId;
	
	public AssociationEntry clone(String newComponentSctId) {
		AssociationEntry clone = new AssociationEntry();
		clone.id = UUID.randomUUID().toString();
		clone.effectiveTime = null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.referencedComponentId = newComponentSctId;
		clone.targetComponentId = this.targetComponentId;
		clone.isDirty = true; //New components need to be written to any delta
		return clone;
	}
	
	public AssociationEntry clone() {
		return clone(this.referencedComponentId);
	}
	
	public String toString() {
		String activeIndicator = isActive()?"":"*";
		return "[" + activeIndicator + "HA]:" + id + " - " + refsetId + " : " + referencedComponentId + "->" + targetComponentId;
	}
	
	public String toVerboseString(){
		try {
			String source = GraphLoader.getGraphLoader().getConcept(referencedComponentId).toString();
			String refset = SnomedUtils.getPT(refsetId).replace("association reference set","");
			String target = GraphLoader.getGraphLoader().getConcept(targetComponentId).toString();
			return source + " " + refset + " " + target;
		} catch (Exception e) {
			throw new IllegalArgumentException("Unable to form association string",e);
		}
	}
	
	public static AssociationEntry fromRf2(String[] lineItems) {
		AssociationEntry h = new AssociationEntry();
		h.setId(lineItems[ASSOC_IDX_ID]);
		h.setEffectiveTime(lineItems[ASSOC_IDX_EFFECTIVETIME]);
		h.setActive(lineItems[ASSOC_IDX_ACTIVE].equals("1"));
		h.setModuleId(lineItems[ASSOC_IDX_MODULID]);
		h.setRefsetId(lineItems[ASSOC_IDX_REFSETID]);
		h.setReferencedComponentId(lineItems[ASSOC_IDX_REFCOMPID]);
		h.setTargetComponentId(lineItems[ASSOC_IDX_TARGET]);
		return h;
	}
	
	public String[] toRF2() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				(active?"1":"0"),
				moduleId, refsetId,
				referencedComponentId,
				targetComponentId
		};
	}
	
	public String[] toRF2Deletion() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				deletionEffectiveTime,
				(active?"1":"0"),
				"1",
				moduleId, refsetId,
				referencedComponentId,
				targetComponentId
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
			isDirty = true;
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
	public String getReferencedComponentId() {
		return referencedComponentId;
	}
	public void setReferencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
	}
	public String getTargetComponentId() {
		return targetComponentId;
	}
	public void setTargetComponentId(String targetComponentId) {
		if (this.targetComponentId != null && !this.targetComponentId.equals(targetComponentId)) {
			isDirty = true;
		}
		this.targetComponentId = targetComponentId;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void delete(String deletionEffectiveTime) {
		this.isDeleted = true;
		this.deletionEffectiveTime = deletionEffectiveTime;
	}

	@Override
	public String getReportedName() {
		return this.toString();
	}

	@Override
	public String getReportedType() {
		return getComponentType().toString();
	}

	@Override
	public ComponentType getComponentType() {
		return ComponentType.HISTORICAL_ASSOCIATION;
	}
	
	@Override 
	public boolean equals(Object o) {
		if (o instanceof AssociationEntry) {
			return this.getId().equals(((AssociationEntry)o).getId());
		}
		return false;
	}

	@Override
	public List<String> fieldComparison(Component other) {
		AssociationEntry otherA = (AssociationEntry)other;
		List<String> differences = new ArrayList<>();
		String name = this.getClass().getSimpleName(); 
		commonFieldComparison(otherA, differences);
		
		if (!this.getTargetComponentId().equals(otherA.getTargetComponentId())) {
			differences.add("TargetComponentId is different in " + name + ": " + this.getTargetComponentId() + " vs " + otherA.getTargetComponentId());
		}
		return differences;
	}
}
