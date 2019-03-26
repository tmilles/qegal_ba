package org.softlang.qegal.process.regex;

import java.io.Serializable;

public class Dependency implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = -9099916498970867854L;
	private String artifactId;
	private String groupId;
	private String version;
	public String getArtifactId() {
		return artifactId;
	}
	public void setArtifactId(String artifactId) {
		this.artifactId = artifactId;
	}
	public String getGroupId() {
		return groupId;
	}
	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
	
	@Override
	public boolean equals(Object o) {
		if(o == null) return false;
		if(this == o) return true;
		Dependency other = (Dependency) o;
		return other.getArtifactId() == artifactId && other.getGroupId() == groupId && other.getVersion() == version;
	}
	
	
}
