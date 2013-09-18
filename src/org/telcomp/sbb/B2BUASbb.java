package org.telcomp.sbb;

import java.text.ParseException;
import java.util.HashMap;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.ClientTransaction;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.ObjectInUseException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.ChildRelation;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import org.telcomp.events.EndThirdPartyCallTelcoServiceEvent;
import org.telcomp.events.StartThirdPartyCallTelcoServiceEvent;

public abstract class B2BUASbb implements javax.slee.Sbb {

	private SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	private SleeSipProvider sipFactoryProvider;
	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;

	private final String Caller_Not_Available = "Caller Not Available";
	private final String Caller_Not_Existent = "Caller Not Existent";
	private final String Callee_Not_Available = "Callee Not Available";
	private final String Callee_Not_Existent = "Callee Not Existent";
	private final String Call_Rejected_by_Caller = "Call Rejected by Caller";
	private final String Call_Rejected_by_Callee = "Call Rejected by Callee";
	private final String Caller_Occupied = "Caller Occupied";
	private final String Callee_Occupied = "Callee Occupied";
	private final String Call_Established = "Call Established";

	public void onStartThirdPartyCallTelcoServiceEvent(StartThirdPartyCallTelcoServiceEvent event, ActivityContextInterface aci) {
		System.out.println("*******************************************");
		System.out.println("ThirdPartyCallTelcoService Invoked");
		System.out.println("Input Caller = "+event.getCaller());
		System.out.println("Input Callee = "+event.getCallee());
		
		this.setDialogFlag(true);
		this.setErrorFlag(false);
		this.setMainAci(aci);

		String caller = event.getCaller();
		this.setCaller(caller);
		String callee = event.getCallee();
		this.setCallee(callee);	

		// Verify if caller exists in DB and is online to fire its Invite request
		try {
			ChildRelation childRelation = this.getContactSbb();
			ContactSbbLocalObject contactSbb = (ContactSbbLocalObject) childRelation.create();
			String toUri = contactSbb.getContactUri(caller);
			if(toUri == null){
				// Caller don't exists in DB, fire endEvent
				System.out.println("User "+ caller+ " Not Found in DB");
				HashMap<String, Object> operationInputs = new HashMap<String, Object>();
				operationInputs.put("caller", (String) caller);
				operationInputs.put("callee", (String) callee);
				operationInputs.put("result", (String) this.Caller_Not_Existent);
				EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
				this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, aci, null);
				aci.detach(this.sbbContext.getSbbLocalObject());
				System.out.println("Output Caller = "+caller);
				System.out.println("Output Callee = "+callee);
				System.out.println("Output Result = "+this.Caller_Not_Existent);
				System.out.println("*******************************************");
			} else {
				if (contactSbb.getState(caller).equals("offline")) {
					// Caller is offline so Invite request can't be send
					HashMap<String, Object> operationInputs = new HashMap<String, Object>();
					operationInputs.put("caller", (String) caller);
					operationInputs.put("callee", (String) callee);
					operationInputs.put("result", (String) this.Caller_Not_Available);
					EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
					this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, aci, null);
					aci.detach(this.sbbContext.getSbbLocalObject());
					System.out.println("Output Caller = "+caller);
					System.out.println("Output Callee = "+callee);
					System.out.println("Output Result = "+this.Caller_Not_Available);
					System.out.println("*******************************************");
				} else {
					// Fire Initial Invite to Caller's UA
					String[] data = new String[3];
					data[0] = callee;
					data[1] = caller;
					data[2] = toUri;
					ChildRelation childRelation1 = this.getFireInviteSbb();
					FireInviteSbbLocalObject fireInviteSbb = (FireInviteSbbLocalObject) childRelation1.create();
					DialogActivity firstDialog = fireInviteSbb.sendInitialInvite(data);
					// Attaching B2B Local Object to second dialog and saving it in CMP Field.
					ActivityContextInterface firstDialogAci = sipActivityContextInterfaceFactory.getActivityContextInterface(firstDialog);
					firstDialogAci.attach(this.sbbContext.getSbbLocalObject());
					this.setIncomingDialog(firstDialogAci);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		Response response = event.getResponse();
		FromHeader from = (FromHeader) response.getHeader(FromHeader.NAME);
		ToHeader to = (ToHeader) response.getHeader(ToHeader.NAME);
		CSeqHeader Cseq = (CSeqHeader) response.getHeader(CSeqHeader.NAME);

		if(this.getDialogFlag() && !Cseq.getMethod().equals("BYE")){
			this.setDialogFlag(false);
			try {
				String[] users = new String[3];
				users[0] = this.getName(from.getAddress().toString());
				users[1] = this.getName(to.getAddress().toString());
				this.setToHeader(users[1]);
				// Provisional ACK for first user to prevent 200OK retransmissions.
				DialogActivity out = (DialogActivity) aci.getActivity();
				Request ACK = out.createAck(Cseq.getSeqNumber());
				out.sendAck(ACK);

				// Getting second user contact URI.
				ChildRelation contactRelation = this.getContactSbb();
				ContactSbbLocalObject contactSbb = (ContactSbbLocalObject) contactRelation.create();
				users[2] = contactSbb.getContactUri(users[0]);
				
				if(users[2] == null){
					//Callee don't exists in DB, fire endEvent
					System.out.println("User "+ users[0]+ " Not Found in DB");
					this.sendByeRequest(this.getIncomingDialog());
					HashMap<String, Object> operationInputs = new HashMap<String, Object>();
					operationInputs.put("caller", (String) this.getCaller());
					operationInputs.put("callee", (String) this.getCallee());
					operationInputs.put("result", (String) this.Callee_Not_Existent);
					EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
					this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, this.getMainAci(), null);
					this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
					System.out.println("Output Caller = "+this.getCaller());
					System.out.println("Output Callee = "+this.getCallee());
					System.out.println("Output Result = "+this.Callee_Not_Existent);
					System.out.println("*******************************************");
				} else {
					// Checking if user is available
					if (contactSbb.getState(users[0]).equals("offline")) {
						this.sendByeRequest(this.getIncomingDialog());
						HashMap<String, Object> operationInputs = new HashMap<String, Object>();
						operationInputs.put("caller", (String) this.getCaller());
						operationInputs.put("callee", (String) this.getCallee());
						operationInputs.put("result", (String) this.Callee_Not_Available);
						EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
						this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, this.getMainAci(), null);
						this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
						System.out.println("Output Caller = "+this.getCaller());
						System.out.println("Output Callee = "+this.getCallee());
						System.out.println("Output Result = "+this.Callee_Not_Available);
						System.out.println("*******************************************");
					} else {
						aci.detach(contactSbb);
						// Getting first user SDP and firing second INVITE.
						String remoteSdp = new String(response.getRawContent());
						ChildRelation childRelation = this.getFireInviteSbb();
						FireInviteSbbLocalObject fireInviteSbb = (FireInviteSbbLocalObject) childRelation.create();
						DialogActivity second = fireInviteSbb.sendSecondInvite(users, remoteSdp);
						// Attaching B2B Local Object to second dialog and saving it in CMP Field.
						ActivityContextInterface secondDialogAci = sipActivityContextInterfaceFactory.getActivityContextInterface(second);
						secondDialogAci.attach(this.sbbContext.getSbbLocalObject());
						this.setOutgoingDialog(secondDialogAci);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else if(Cseq.getMethod().equals("BYE")){
			//Condition to identify that the BYE Request was generated from the server
			if(Cseq.getSeqNumber() < 3){
				this.setErrorFlag(true);
			}
			processResponse(event, aci);
		} else if(this.getName(to.getAddress().toString()).equals(this.getToHeader()) && !this.getErrorFlag()){
			try{
				DialogActivity out = (DialogActivity) aci.getActivity();
				Request ACK = out.createAck(Cseq.getSeqNumber());
				out.sendAck(ACK);
				//Call successfully established
				HashMap<String, Object> operationInputs = new HashMap<String, Object>();
				operationInputs.put("caller", (String) this.getCaller());
				operationInputs.put("callee", (String) this.getCallee());
				operationInputs.put("result", (String) this.Call_Established);
				EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
				this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, this.getMainAci(), null);
				this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
				System.out.println("Output Caller = "+this.getCaller());
				System.out.println("Output Callee = "+this.getCallee());
				System.out.println("Output Result = "+this.Call_Established);
				System.out.println("*******************************************");
			} catch(Exception e){
				e.printStackTrace();
			}
		} else{
			DialogActivity dg2 = (DialogActivity) aci.getActivity();
			try {
				System.out.println("**************200 OK from second user");
				//Sending ACK to second user.
				Request ACK = dg2.createAck(Cseq.getSeqNumber());
				dg2.sendAck(ACK);
				//Sending Re-INVITE to first user to send the proper SDP
				DialogActivity dg1 = (DialogActivity) this.getIncomingDialog().getActivity();
				Request reInvite = dg1.createRequest(Request.INVITE);
				String sdpData = new String(event.getResponse().getRawContent());
				byte[] contents = sdpData.getBytes();
				ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
				reInvite.setContent(contents, contentTypeHeader);
				dg1.sendRequest(reInvite);
			} catch (InvalidArgumentException e) {
				e.printStackTrace();
			} catch (SipException e) {
				e.printStackTrace();
			} catch (ParseException e) {
				e.printStackTrace();
			}
			
		}
	}
	
	public void on4xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		 if(!this.getDialogFlag()){
			 //Callee occupied
		    this.sendByeRequest(this.getIncomingDialog());
		    HashMap<String, Object> operationInputs = new HashMap<String, Object>();
			operationInputs.put("caller", (String) this.getCaller());
			operationInputs.put("callee", (String) this.getCallee());
			operationInputs.put("result", (String) this.Callee_Occupied);
			EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
			this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, this.getMainAci(), null);
			this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
			System.out.println("Output Caller = "+this.getCaller());
			System.out.println("Output Callee = "+this.getCallee());
			System.out.println("Output Result = "+this.Callee_Occupied);
			System.out.println("*******************************************");
		 } else{
			 //Caller occupied
			 HashMap<String, Object> operationInputs = new HashMap<String, Object>();
				operationInputs.put("caller", (String) this.getCaller());
				operationInputs.put("callee", (String) this.getCallee());
				operationInputs.put("result", (String) this.Caller_Occupied);
				EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
				this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, this.getMainAci(), null);
				this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
				System.out.println("Output Caller = "+this.getCaller());
				System.out.println("Output Callee = "+this.getCallee());
				System.out.println("Output Result = "+this.Caller_Occupied);
				System.out.println("*******************************************");
		 }
	}

	public void on6xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		 if(!this.getDialogFlag()){
			 //Call rejected by callee
		    this.sendByeRequest(this.getIncomingDialog());
		    HashMap<String, Object> operationInputs = new HashMap<String, Object>();
			operationInputs.put("caller", (String) this.getCaller());
			operationInputs.put("callee", (String) this.getCallee());
			operationInputs.put("result", (String) this.Call_Rejected_by_Callee);
			EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
			this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, this.getMainAci(), null);
			this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
			System.out.println("Output Caller = "+this.getCaller());
			System.out.println("Output Callee = "+this.getCallee());
			System.out.println("Output Result = "+this.Call_Rejected_by_Callee);
			System.out.println("*******************************************");
		 } else{
			 //Call rejected by caller
			 HashMap<String, Object> operationInputs = new HashMap<String, Object>();
				operationInputs.put("caller", (String) this.getCaller());
				operationInputs.put("callee", (String) this.getCallee());
				operationInputs.put("result", (String) this.Call_Rejected_by_Caller);
				EndThirdPartyCallTelcoServiceEvent endThirdPartyCallTelcoServiceEvent = new EndThirdPartyCallTelcoServiceEvent(operationInputs);
				this.fireEndThirdPartyCallTelcoServiceEvent(endThirdPartyCallTelcoServiceEvent, this.getMainAci(), null);
				this.getMainAci().detach(this.sbbContext.getSbbLocalObject());
				System.out.println("Output Caller = "+this.getCaller());
				System.out.println("Output Callee = "+this.getCallee());
				System.out.println("Output Result = "+this.Call_Rejected_by_Caller);
				System.out.println("*******************************************");
		 }
	}

	// other responses handled the same way as above Mid-dialog requests
	public void onBye(RequestEvent event, ActivityContextInterface aci) {
		processMidDialogRequest(event, aci);
	}

	// Other mid-dialog requests handled the same way as above Helpers
	private void processMidDialogRequest(RequestEvent event, ActivityContextInterface dialogACI) {
		try {
			// Find the dialog to forward the request on
			ActivityContextInterface peerACI = getPeerDialog(dialogACI);
			forwardRequest(event.getServerTransaction(), (DialogActivity) peerACI.getActivity(), dialogACI, event);
		} catch (SipException e) {
			System.out.println("processMidDialog error because: "+ e.getMessage());
			sendErrorResponse(event.getServerTransaction(), Response.SERVICE_UNAVAILABLE);
		}
	}

	private void processResponse(ResponseEvent event, ActivityContextInterface aci) {
		if (!this.getErrorFlag()) {
			try {
				// Find the dialog to forward the response on
				ActivityContextInterface peerACI = getPeerDialog(aci);
				forwardResponse((DialogActivity) aci.getActivity(), (DialogActivity) peerACI.getActivity(),
						event.getClientTransaction(), event.getResponse());
			} catch (SipException e) {
				System.out.println(e.getMessage());
			}
		} else {
			this.endAllActivities();
		}
	}

	private ActivityContextInterface getPeerDialog(ActivityContextInterface aci)
			throws SipException {
		if (aci.getActivity().equals(getIncomingDialog().getActivity())) {
			System.out.println("incomingDialog --> outgoingDialog");
			return getOutgoingDialog();
		}
		if (aci.getActivity().equals(getOutgoingDialog().getActivity())) {
			System.out.println("outgoingDialog --> incomingDialog");
			return getIncomingDialog();
		}
		throw new SipException("could not find peer dialog");
	}

	private void forwardRequest(ServerTransaction st, DialogActivity out, ActivityContextInterface aci, RequestEvent event)
			throws SipException {
		// Copies the request, setting the appropriate headers for the dialog.
		Request incomingRequest = st.getRequest();
		Request outgoingRequest = out.createRequest(incomingRequest);
		try {
			outgoingRequest.setHeader(getContactHeader());
			ToHeader toHeader = (ToHeader) outgoingRequest.getHeader(ToHeader.NAME);
			String name = this.getName(toHeader.getAddress().toString());
			ChildRelation childRelation = this.getContactSbb();
			ContactSbbLocalObject contactSbb = (ContactSbbLocalObject) childRelation.create();
			String uri = contactSbb.getContactUri(name);
			aci.detach(contactSbb);
			SipURI urit = addressFactory.createSipURI(name, uri);
			outgoingRequest.setRequestURI(urit);
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		ClientTransaction ct = this.sipFactoryProvider.getNewClientTransaction(outgoingRequest);
		out.sendRequest(ct);
		out.associateServerTransaction(ct, st);
	}

	private void forwardResponse(DialogActivity in, DialogActivity out, ClientTransaction ct, Response receivedResponse)
			throws SipException {
		// Find the original server transaction that this response
		// should be forwarded on.
		ServerTransaction st = in.getAssociatedServerTransaction(ct);
		// could be null
		if (st == null) {
			throw new SipException(
					"could not find associated server transaction");
		}
		// Copy the response across, setting the appropriate headers for the
		// dialog
		Response outgoingResponse = out.createResponse(st, receivedResponse);
		// Forward response upstream.
		try {
			outgoingResponse.setHeader(getContactHeader());
			st.sendResponse(outgoingResponse);
		} catch (InvalidArgumentException e) {
			e.printStackTrace();
			throw new SipException("invalid response", e);
		} catch (ParseException e) {
			System.out.println(e.getMessage());
		}
		CSeqHeader cseq = (CSeqHeader) receivedResponse.getHeader(CSeqHeader.NAME);
		if (cseq.getMethod().equals("BYE")) {
			this.endAllActivities();
		}
	}

	private ContactHeader getContactHeader() throws ParseException {
		ContactHeader contactHeader;
		ListeningPoint listeningPoint = sipFactoryProvider.getListeningPoint("udp");
		Address address = addressFactory.createAddress("Mobicents JSLEE AS <sip:"+ listeningPoint.getIPAddress() + ">");
		((SipURI) address.getURI()).setPort(listeningPoint.getPort());
		contactHeader = headerFactory.createContactHeader(address);
		return contactHeader;
	}

	private void sendErrorResponse(ServerTransaction st, int statusCode) {
		try {
			Response response = sipFactoryProvider.getMessageFactory().createResponse(statusCode, st.getRequest());
			st.sendResponse(response);
		} catch (Exception e) {
			System.out.println("Could not send error response because: "+ e.getMessage().toString());
		}
	}
	
	private void sendByeRequest(ActivityContextInterface outAci){
		DialogActivity outDialog = (DialogActivity) outAci.getActivity();
		Request bye = null;
		try {
			bye = outDialog.createRequest(Request.BYE);
			ClientTransaction ct = this.sipFactoryProvider.getNewClientTransaction(bye);
			outDialog.sendRequest(ct);
		} catch (SipException e) {
			e.printStackTrace();
		}
	}

	private void endAllActivities() {
		ActivityContextInterface[] acl = this.sbbContext.getActivities();
		for (int i = 0; i < acl.length; i++) {
			if (acl[i].isAttached(this.sbbContext.getSbbLocalObject())) {
				try {
					DialogActivity dat = (DialogActivity) acl[i].getActivity();
					dat.delete();
				} catch (Exception e) {
					ServerTransaction st1 = (ServerTransaction) acl[i].getActivity();
					try {
						st1.terminate();
					} catch (ObjectInUseException e1) {
						e1.printStackTrace();
					}
				}
			}
		}
	}

	// Get User name from URI
	private String getName(String prevName) {
		return prevName.substring(prevName.indexOf(':') + 1,prevName.indexOf('@'));
	}

	public abstract void setIncomingDialog(ActivityContextInterface aci);
	public abstract ActivityContextInterface getIncomingDialog();
	public abstract void setOutgoingDialog(ActivityContextInterface aci);
	public abstract ActivityContextInterface getOutgoingDialog();
	public abstract void setDialogFlag(boolean flag);
	public abstract boolean getDialogFlag();
	public abstract void setToHeader(String to);
	public abstract String getToHeader();
	public abstract void setErrorFlag(boolean flag);
	public abstract boolean getErrorFlag();
	public abstract void setMainAci(ActivityContextInterface aci);
	public abstract ActivityContextInterface getMainAci();
	public abstract void setCaller(String caller);
	public abstract String getCaller();
	public abstract void setCallee(String callee);
	public abstract String getCallee();

	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");
			sipFactoryProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			addressFactory = sipFactoryProvider.getAddressFactory();
			headerFactory = sipFactoryProvider.getHeaderFactory();
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}

	public void unsetSbbContext() {
		this.sbbContext = null;
	}

	// TODO: Implement the lifecycle methods if required
	public void sbbCreate() throws javax.slee.CreateException {}
	public void sbbPostCreate() throws javax.slee.CreateException {}
	public void sbbActivate() {}
	public void sbbPassivate() {}
	public void sbbRemove() {}
	public void sbbLoad() {}
	public void sbbStore() {}
	public void sbbExceptionThrown(Exception exception, Object event,ActivityContextInterface activity) {}
	public void sbbRolledBack(RolledBackContext context) {}

	public abstract void fireEndThirdPartyCallTelcoServiceEvent(EndThirdPartyCallTelcoServiceEvent event, ActivityContextInterface aci, javax.slee.Address address);

	public abstract ChildRelation getContactSbb();
	public abstract ChildRelation getFireInviteSbb();

	/**
	 * Convenience method to retrieve the SbbContext object stored in
	 * setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove
	 * this method, the sbbContext variable and the variable assignment in
	 * setSbbContext().
	 * 
	 * @return this SBB's SbbContext object
	 */

	protected SbbContext getSbbContext() {
		return sbbContext;
	}
	
	private SbbContext sbbContext;
}
