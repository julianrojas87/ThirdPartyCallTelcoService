package org.telcomp.events;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Random;

public final class StartThirdPartyCallTelcoServiceEvent implements Serializable {

	private static final long serialVersionUID = 1L;
	private final long id;
	private String caller;
	private String callee;

	public StartThirdPartyCallTelcoServiceEvent(HashMap<String, ?> hashMap) {
		id = new Random().nextLong() ^ System.currentTimeMillis();
		this.caller = (String) hashMap.get("caller");
		this.callee = (String) hashMap.get("callee");
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (o == null) return false;
		return (o instanceof StartThirdPartyCallTelcoServiceEvent) && ((StartThirdPartyCallTelcoServiceEvent)o).id == id;
	}
	
	public String getCaller(){
		return this.caller;
	}
	
	public String getCallee(){
		return this.callee;
	}
	
	public int hashCode() {
		return (int) id;
	}
	
	public String toString() {
		return "startThirdPartyCallEvent[" + hashCode() + "]";
	}
}
