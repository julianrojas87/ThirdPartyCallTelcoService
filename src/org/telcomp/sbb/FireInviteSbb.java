package org.telcomp.sbb;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.slee.ActivityContextInterface;
import javax.slee.RolledBackContext;
import javax.slee.SbbContext;

import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

public abstract class FireInviteSbb implements javax.slee.Sbb {
	
	SleeSipProvider sipFactoryProvider;
	SipActivityContextInterfaceFactory sipActivityContextInterfaceFactory;
	MessageFactory messageFactory;
	HeaderFactory headerFactory;
	AddressFactory addressFactory;
	
	public DialogActivity sendInitialInvite(String[] users){
		DialogActivity out = null;
		Request inviteRequest = this.createInviteRequest(users[0], users[1], users[2]);
		FromHeader fromHeader = (FromHeader) inviteRequest.getHeader(FromHeader.NAME);
		ToHeader toHeader = (ToHeader) inviteRequest.getHeader(ToHeader.NAME);
		
		try {
			inviteRequest = this.setFalseSDP(inviteRequest);
			out = sipFactoryProvider.getNewDialog(fromHeader.getAddress(), toHeader.getAddress());
			out.sendRequest(inviteRequest);
		} catch (SipException e) {
			e.printStackTrace();
		}
		return out;
	}
	
	public DialogActivity sendSecondInvite(String[] users, String remoteSdp){
		DialogActivity out = null;
		Request inviteRequest = this.createInviteRequest(users[1], users[0], users[2]);
		FromHeader fromHeader = (FromHeader) inviteRequest.getHeader(FromHeader.NAME);
		ToHeader toHeader = (ToHeader) inviteRequest.getHeader(ToHeader.NAME);
		
		try {
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
			byte[] contents = remoteSdp.getBytes();
			inviteRequest.setContent(contents, contentTypeHeader);
			out = sipFactoryProvider.getNewDialog(fromHeader.getAddress(), toHeader.getAddress());
			out.sendRequest(inviteRequest);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return out;
	}
	
	private Request createInviteRequest(String fromName, String toName, String toUri){
		Address addressTo;
		Address addressFrom;
		Address addressContact;
		ToHeader to;
		List<ViaHeader> vias;
		CallIdHeader callId;
		SipURI requestURI;
		Request inviteReq = null;
		
		try{
			addressFrom = addressFactory.createAddress("sip:"+fromName+"@"+System.getProperty("jboss.bind.address")+":5060");
			addressFrom.setDisplayName(fromName);
			FromHeader from = headerFactory.createFromHeader(addressFrom, fromName + (Math.random() * 100000));
			CSeqHeader cshead = headerFactory.createCSeqHeader(1L, Request.INVITE); 
			MaxForwardsHeader maxhead = headerFactory.createMaxForwardsHeader(70);
			SipURI contactURI = addressFactory.createSipURI(((SipURI) from.getAddress().getURI()).getUser(), 
					sipFactoryProvider.getListeningPoints()[0].getIPAddress() + ":5060");
			addressContact = addressFactory.createAddress(contactURI);
			ContactHeader contact = headerFactory.createContactHeader(addressContact);
			addressTo = addressFactory.createAddress("sip:"+toName+"@"+toUri);
			addressTo.setDisplayName(toName);
			vias = new ArrayList<ViaHeader>(1);
			vias.add(sipFactoryProvider.getLocalVia("UDP", "z9hC4GbK095871331.0"));
			to = headerFactory.createToHeader(addressTo, null);			 
			callId = sipFactoryProvider.getNewCallId();
			requestURI = (SipURI)to.getAddress().getURI();
			inviteReq = messageFactory.createRequest(requestURI, Request.INVITE, callId, cshead, from, to, vias, maxhead);
			inviteReq.addHeader(contact);
		} catch(Exception e){
			e.printStackTrace();
		}
		
		return inviteReq;
	}
	
	private Request setFalseSDP(Request request){
		String falseSDP = "v=0\r\n" + "o=JSLEEAS 615 615" + " IN IP4 192.175.16.130\r\n" 
				+ "s=Talk\r\n" + "c=IN IP4 192.175.16.130\r\n" + "t=0 0\r\n" + "m=audio 1666 RTP/AVP 0\r\n" 
				+ "a=rtpmap:0 PCMU/8000\r\n";
		ContentTypeHeader contentTypeHeader;
		try {
			contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
			byte[] contents = falseSDP.getBytes();
			request.setContent(contents, contentTypeHeader);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return request;
	}
	
	// TODO: Perform further operations if required in these methods.
	public void setSbbContext(SbbContext context) { 
		this.sbbContext = context; 
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			sipActivityContextInterfaceFactory = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resources/jainsip/1.2/acifactory");
			sipFactoryProvider = (SleeSipProvider) ctx.lookup("slee/resources/jainsip/1.2/provider");
			messageFactory = sipFactoryProvider.getMessageFactory();
			headerFactory = sipFactoryProvider.getHeaderFactory();
			addressFactory = sipFactoryProvider.getAddressFactory();
		} catch (NamingException namingException) {
			namingException.printStackTrace();
		}
	}
    public void unsetSbbContext() { this.sbbContext = null; }
    
    // TODO: Implement the lifecycle methods if required
    public void sbbCreate() throws javax.slee.CreateException {}
    public void sbbPostCreate() throws javax.slee.CreateException {}
    public void sbbActivate() {}
    public void sbbPassivate() {}
    public void sbbRemove() {}
    public void sbbLoad() {}
    public void sbbStore() {}
    public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {}
    public void sbbRolledBack(RolledBackContext context) {}
	

	
	/**
	 * Convenience method to retrieve the SbbContext object stored in setSbbContext.
	 * 
	 * TODO: If your SBB doesn't require the SbbContext object you may remove this 
	 * method, the sbbContext variable and the variable assignment in setSbbContext().
	 *
	 * @return this SBB's SbbContext object
	 */
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

	private SbbContext sbbContext; // This SBB's SbbContext

}
