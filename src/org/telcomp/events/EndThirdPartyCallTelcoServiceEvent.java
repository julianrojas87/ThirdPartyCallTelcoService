package org.telcomp.events;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Random;

public final class EndThirdPartyCallTelcoServiceEvent implements Serializable {
	
	private static final long serialVersionUID = 1L;
	private final long id;
	private String caller;
	private String callee;
	private String result;

	public EndThirdPartyCallTelcoServiceEvent(HashMap<String, ?> hashMap) {
		id = new Random().nextLong() ^ System.currentTimeMillis();
		this.caller = (String) hashMap.get("caller");
		this.callee = (String) hashMap.get("callee");
		this.result = (String) hashMap.get("result");
	}

	public boolean equals(Object o) {
		if (o == this) return true;
		if (o == null) return false;
		return (o instanceof EndThirdPartyCallTelcoServiceEvent) && ((EndThirdPartyCallTelcoServiceEvent)o).id == id;
	}
	public String getCaller(){
		return this.caller;
	}
	
	public String getCallee(){
		return this.callee;
	}
	
	public String getResult(){
		return this.result;
	}
	
	public int hashCode() {
		return (int) id;
	}
	
	public String toString() {
		return "endThirdPartyCallEvent[" + hashCode() + "]";
	}
}
