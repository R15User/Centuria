package org.asf.centuria.modules.events.accounts;

import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.modules.eventbus.EventObject;
import org.asf.centuria.modules.eventbus.EventPath;

/**
 * 
 * Disconnect Event - called on account disconnect (<b>should not be used in mod
 * logs as this also handles system-issued and user-issued
 * kicks/disconnects</b>)
 * 
 * @author Sky Swimmer - AerialWorks Software Foundation
 *
 */
@EventPath("accounts.disconnect")
public class AccountDisconnectEvent extends EventObject {

	private String reason;
	private CenturiaAccount account;
	private DisconnectType type;

	public static enum DisconnectType {
		UNKNOWN, SERVER_SHUTDOWN, KICKED, BANNED, MAINTENANCE
	}

	public AccountDisconnectEvent(CenturiaAccount account, String reason, DisconnectType type) {
		this.account = account;
		this.reason = reason;
		this.type = type;
	}

	/**
	 * Retrieves the type of disconnect
	 * 
	 * @return DisconnectType value
	 */
	public DisconnectType getType() {
		return type;
	}

	/**
	 * Retrieves the reason for the moderation action
	 *
	 * @return Action reason or null
	 */
	public String getReason() {
		return reason;
	}

	@Override
	public String eventPath() {
		return "accounts.disconnect";
	}

	/**
	 * Retrieves the account that is being disconnected
	 * 
	 * @return CenturiaAccount instance
	 */
	public CenturiaAccount getAccount() {
		return account;
	}

}
