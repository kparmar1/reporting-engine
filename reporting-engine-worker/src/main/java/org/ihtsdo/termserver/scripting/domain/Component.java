package org.ihtsdo.termserver.scripting.domain;

import java.util.Collection;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ComponentType;

public abstract class Component {
	
	//Generic debug string to say if concept should be highlighted for some reason, eg cause a template match to fail
	private transient String issues = "";

	public abstract String getId();
	
	public abstract String getEffectiveTime();
	
	public abstract boolean isActive();
	
	public abstract String getReportedName();
	
	public abstract String getReportedType();
	
	public abstract ComponentType getComponentType();
	
	public abstract String[] toRF2() throws TermServerScriptException;
	
	public void addIssue(String issue) {
		if (this.issues == null) {
			this.issues = issue;
		} else {
			if (!this.issues.isEmpty()) {
				this.issues += ", ";
			}
			this.issues += issue;
		}
	}
	
	public void addAllIssues(Collection<? extends Object> issues) {
		for (Object issue : issues) {
			addIssue(issue.toString());
		}
	}
	
	public String getIssues() {
		return issues;
	}

	public void setIssue(String issue) {
		issues = issue;
	}

	@Override
	public boolean equals(Object other) {
		if (other instanceof Component) {
			return this.getId().equals(((Component)other).getId());
		}
		return false;
	}
}
