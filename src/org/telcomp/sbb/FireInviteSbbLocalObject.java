package org.telcomp.sbb;

import net.java.slee.resource.sip.DialogActivity;

public interface FireInviteSbbLocalObject extends javax.slee.SbbLocalObject {

	DialogActivity sendInitialInvite(String[] users);
	DialogActivity sendSecondInvite(String[] users, String remoteSdp);

}
