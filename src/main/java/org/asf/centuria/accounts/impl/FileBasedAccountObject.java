package org.asf.centuria.accounts.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.UUID;

import org.asf.centuria.Centuria;
import org.asf.centuria.accounts.AccountManager;
import org.asf.centuria.accounts.CenturiaAccount;
import org.asf.centuria.accounts.LevelInfo;
import org.asf.centuria.accounts.PlayerInventory;
import org.asf.centuria.dms.DMManager;
import org.asf.centuria.entities.players.Player;
import org.asf.centuria.modules.eventbus.EventBus;
import org.asf.centuria.modules.events.accounts.AccountDeletionEvent;
import org.asf.centuria.packets.xt.gameserver.inventory.InventoryItemDownloadPacket;
import org.asf.centuria.social.SocialEntry;
import org.asf.centuria.social.SocialManager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class FileBasedAccountObject extends CenturiaAccount {

	private int userID;
	private boolean isNew;
	private String userUUID;
	private String loginName;
	private String displayName;
	private FileBasedPlayerInventory inv;
	private JsonObject privacy;
	private LevelInfo level;
	private long lastLogin = -1;
	private File userFile;

	private static String[] nameBlacklist = new String[] { "kit", "kitsendragn", "kitsendragon", "fera", "fero",
			"wwadmin", "ayli", "komodorihero", "wwsam", "blinky", "fer.ocity" };

	private static ArrayList<String> banWords = new ArrayList<String>();
	private static ArrayList<String> filterWords = new ArrayList<String>();

	static {
		// Load filter
		try {
			InputStream strm = InventoryItemDownloadPacket.class.getClassLoader()
					.getResourceAsStream("textfilter/filter.txt");
			String lines = new String(strm.readAllBytes(), "UTF-8").replace("\r", "");
			for (String line : lines.split("\n")) {
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String data = line.trim();
				while (data.contains("  "))
					data = data.replace("  ", "");

				for (String word : data.split(" "))
					filterWords.add(word.toLowerCase());
			}
			strm.close();
		} catch (IOException e) {
		}

		// Load ban words
		try {
			InputStream strm = InventoryItemDownloadPacket.class.getClassLoader()
					.getResourceAsStream("textfilter/instaban.txt");
			String lines = new String(strm.readAllBytes(), "UTF-8").replace("\r", "");
			for (String line : lines.split("\n")) {
				if (line.isEmpty() || line.startsWith("#"))
					continue;

				String data = line.trim();
				while (data.contains("  "))
					data = data.replace("  ", "");

				for (String word : data.split(" "))
					banWords.add(word.toLowerCase());
			}
			strm.close();
		} catch (IOException e) {
		}
	}

	public FileBasedAccountObject(File uf) throws IOException {
		// Parse account file
		userUUID = Files.readAllLines(uf.toPath()).get(0);
		loginName = Files.readAllLines(uf.toPath()).get(1);
		isNew = Files.readAllLines(uf.toPath()).get(2).equals("true");
		displayName = Files.readAllLines(uf.toPath()).get(3);
		userID = Integer.parseInt(Files.readAllLines(uf.toPath()).get(4));

		// Find existing inventory
		Player old = getOnlinePlayerInstance();
		if (old == null || !(old.account.getPlayerInventory() instanceof FileBasedPlayerInventory)) {
			// Load inventory
			inv = new FileBasedPlayerInventory(userUUID);
		} else {
			// Use the existing inventory object
			inv = (FileBasedPlayerInventory) old.account.getPlayerInventory();
		}

		// Load login timestamp
		lastLogin = uf.lastModified() / 1000;
		userFile = uf;
	}

	@Override
	public String getLoginName() {
		return loginName;
	}

	@Override
	public String getDisplayName() {
		return displayName;
	}

	@Override
	public String getAccountID() {
		return userUUID;
	}

	@Override
	public int getAccountNumericID() {
		return userID;
	}

	@Override
	public boolean isPlayerNew() {
		return isNew;
	}

	@Override
	public void finishedTutorial() {
		isNew = false;

		try {
			// Save
			Files.writeString(new File("accounts/" + userUUID).toPath(),
					userUUID + "\n" + loginName + "\n" + isNew + "\n" + displayName + "\n" + userID);
		} catch (IOException e) {
		}
	}

	@Override
	public boolean updateDisplayName(String name) {
		// Check validity
		if (!name.matches("^[0-9A-Za-z\\-_. ]+") || name.length() > 16 || name.length() < 2)
			return false;

		// Prevent blacklisted names from being used
		for (String nm : nameBlacklist) {
			if (name.equalsIgnoreCase(nm))
				return false;
		}

		// Prevent banned and filtered words
		for (String word : name.split(" ")) {
			if (banWords.contains(word.replaceAll("[^A-Za-z0-9]", "").toLowerCase())) {
				return false;
			}

			if (filterWords.contains(word.replaceAll("[^A-Za-z0-9]", "").toLowerCase())) {
				return false;
			}
		}

		// Remove lockout
		if (isRenameRequired())
			new File("accounts/" + userUUID + ".requirechangename").delete();

		try {
			// Store the name
			displayName = name;

			// Save to disk
			Files.writeString(new File("accounts/" + userUUID).toPath(),
					userUUID + "\n" + loginName + "\n" + isNew + "\n" + displayName + "\n" + userID);
			return true;
		} catch (IOException e) {
		}
		return false;
	}

	@Override
	public PlayerInventory getPlayerInventory() {
		return inv;
	}

	@Override
	public JsonObject getPrivacySettings() {
		if (privacy != null)
			return privacy;

		File privacyFile = new File("accounts/" + userUUID + ".privacy");
		if (privacyFile.exists()) {
			try {
				privacy = JsonParser.parseString(Files.readString(privacyFile.toPath())).getAsJsonObject();
				return privacy;
			} catch (JsonSyntaxException | IOException e) {
				privacy = new JsonObject();
				privacy.addProperty("voice_chat", "following");
			}
		}

		privacy = new JsonObject();
		privacy.addProperty("voice_chat", "following");
		savePrivacySettings(privacy);
		return privacy;
	}

	@Override
	public void savePrivacySettings(JsonObject settings) {
		privacy = settings;
		File privacyFile = new File("accounts/" + userUUID + ".privacy");
		try {
			Files.writeString(privacyFile.toPath(), privacy.toString());
		} catch (IOException e) {
		}
	}

	@Override
	public String getActiveLook() {
		// Looks
		File lookFiles = new File("accounts/" + userUUID + ".looks");
		lookFiles.mkdirs();

		// Active look
		File activeLookFileC = new File("accounts/" + userUUID + ".looks/active.look");
		String activeLook = UUID.randomUUID().toString();
		try {
			if (activeLookFileC.exists()) {
				activeLook = Files.readAllLines(activeLookFileC.toPath()).get(0);
			} else {
				Files.writeString(activeLookFileC.toPath(), activeLook);
			}
		} catch (IOException e) {
		}

		return activeLook;
	}

	@Override
	public String getActiveSanctuaryLook() {
		// Sanctuary looks
		File sLookFiles = new File("accounts/" + userUUID + ".sanctuary.looks");
		sLookFiles.mkdirs();

		// Active look
		File activeSLookFileC = new File("accounts/" + userUUID + ".sanctuary.looks/active.look");
		String activeSanctuaryLook = UUID.randomUUID().toString();
		try {
			if (activeSLookFileC.exists()) {
				activeSanctuaryLook = Files.readAllLines(activeSLookFileC.toPath()).get(0);
			} else {
				Files.writeString(activeSLookFileC.toPath(), activeSanctuaryLook);
			}
		} catch (IOException e) {
		}

		return activeSanctuaryLook;
	}

	@Override
	public void setActiveLook(String lookID) {
		try {
			File activeLookFileC = new File("accounts/" + userUUID + ".looks/active.look");
			Files.writeString(activeLookFileC.toPath(), lookID);
		} catch (IOException e) {
		}
	}

	@Override
	public void setActiveSanctuaryLook(String lookID) {
		try {
			File activeSLookFileC = new File("accounts/" + userUUID + ".sanctuary.looks/active.look");
			Files.writeString(activeSLookFileC.toPath(), lookID);
		} catch (IOException e) {
		}
	}

	@Override
	public boolean isRenameRequired() {
		return new File("accounts/" + userUUID + ".requirechangename").exists();
	}

	@Override
	public void forceNameChange() {
		if (!isRenameRequired())
			try {
				new File("accounts/" + userUUID + ".requirechangename").createNewFile();
			} catch (IOException e) {
			}
	}

	@Override
	public LevelInfo getLevel() {
		// TODO Auto-generated method stub

		if (level == null)
			level = new LevelInfo() {

				@Override
				public boolean isLevelAvailable() {
					return false;
				}

				@Override
				public int getLevel() {
					return -1;
				}

				@Override
				public int getTotalXP() {
					return -1;
				}

				@Override
				public int getCurrentXP() {
					return -1;
				}

				@Override
				public int getLevelupXPCount() {
					return -1;
				}

				@Override
				public void addXP(int xp) {
				}
			};

		return level;
	}

	@Override
	public long getLastLoginTime() {
		return lastLogin;
	}

	@Override
	public void login() {
		long time = System.currentTimeMillis();
		userFile.setLastModified(time);
		lastLogin = time / 1000;
	}

	@Override
	public Player getOnlinePlayerInstance() {
		return Centuria.gameServer.getPlayer(getAccountID());
	}

	@Override
	public void deleteAccount() {
		if (!new File("accounts/" + loginName).exists()) {
			// Account does not exist
			return;
		}

		// Dispatch event
		EventBus.getInstance().dispatchEvent(new AccountDeletionEvent(this));

		// Delete login file
		new File("accounts/" + loginName).delete();

		// Kick online player first
		kick("Account deletion");

		// Delete account file
		new File("accounts/" + userUUID).delete();

		// Delete account password file
		if (new File("accounts/" + userUUID + ".cred").exists())
			new File("accounts/" + userUUID + ".cred").delete();

		// Delete looks
		deleteDir(new File("accounts/" + userUUID + ".looks"));
		deleteDir(new File("accounts/" + userUUID + ".sanctuary.looks"));

		// Release display name
		AccountManager.getInstance().releaseDisplayName(displayName);

		// Delete account from the social system
		if (SocialManager.getInstance().socialListExists(userUUID)) {
			SocialEntry[] followers = SocialManager.getInstance().getFollowerPlayers(userUUID);
			SocialEntry[] followings = SocialManager.getInstance().getFollowingPlayers(userUUID);
			for (SocialEntry user : followers) {
				SocialManager.getInstance().setBlockedPlayer(user.playerID, userUUID, false);
				SocialManager.getInstance().setFollowerPlayer(user.playerID, userUUID, false);
				SocialManager.getInstance().setFollowingPlayer(user.playerID, userUUID, false);
			}
			for (SocialEntry user : followings) {
				SocialManager.getInstance().setBlockedPlayer(user.playerID, userUUID, false);
				SocialManager.getInstance().setFollowerPlayer(user.playerID, userUUID, false);
				SocialManager.getInstance().setFollowingPlayer(user.playerID, userUUID, false);
			}
			SocialManager.getInstance().deleteSocialList(userUUID);
		}

		// Delete DMs
		DMManager manager = DMManager.getInstance();
		if (getPlayerInventory().containsItem("dms")) {
			// Loop through all DMs and close them
			JsonObject dms = getPlayerInventory().getItem("dms").getAsJsonObject();
			for (String userID : dms.keySet()) {
				// Load DM id
				String dmID = dms.get(userID).getAsString();

				// Remove all participants
				String[] participants = manager.getDMParticipants(dmID);
				for (String participant : participants) {
					// Remove the DM from player
					CenturiaAccount otherAccount = AccountManager.getInstance().getAccount(participant);
					if (otherAccount != null) {
						// Find DMs
						if (otherAccount.getPlayerInventory().containsItem("dms")) {
							// Load dm from player
							JsonObject otherDMs = otherAccount.getPlayerInventory().getItem("dms").getAsJsonObject();

							// Find DM
							for (String plr : otherDMs.keySet()) {
								if (otherDMs.get(plr).getAsString().equals(dmID)) {
									// Remove DM from player
									otherDMs.remove(plr);
									break;
								}
							}

							// Save DM object
							otherAccount.getPlayerInventory().setItem("dms", dms);
						}
					}
				}

				// Delete DM
				manager.deleteDM(dmID);
			}
			getPlayerInventory().setItem("dms", dms);
		}

		// Log
		Centuria.logger.info("Account deleted: " + getLoginName());

		// Delete inventory
		inv.delete();
	}

	private void deleteDir(File dir) {
		if (!dir.exists())
			return;

		for (File subDir : dir.listFiles(t -> t.isDirectory())) {
			deleteDir(subDir);
		}
		for (File file : dir.listFiles(t -> !t.isDirectory())) {
			file.delete();
		}
		dir.delete();
	}

}
