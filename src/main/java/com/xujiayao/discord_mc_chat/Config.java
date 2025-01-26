package com.xujiayao.discord_mc_chat;

import java.util.ArrayList;
import java.util.List;

import static com.xujiayao.discord_mc_chat.Main.VERSION;

/**
 * @author Xujiayao
 */
public class Config {

	// More information + Docs: https://blog.xujiayao.com/posts/4ba0a17a/

	public Generic generic = new Generic();
	public MultiServer multiServer = new MultiServer();
	public CustomMessage customMessage = new CustomMessage();

	public String latestVersion = VERSION;
	public long latestCheckTime = 0;

	public static class Generic {
		public String language = "en_us";

		public String botToken = "";

		public boolean showServerStatusInBotStatus = true;
		public String botPlayingActivity = "Minecraft (%onlinePlayerCount%/%maxPlayerCount%)";
		public String botListeningActivity = "";

		public boolean useWebhook = true;

		public String channelId = "";
		public String consoleLogChannelId = "";
		public String updateNotificationChannelId = "";

		public String avatarApi = "https://mc-heads.net/avatar/{player_uuid}.png";

		public boolean broadcastPlayerCommandExecution = true;
		public boolean broadcastSlashCommandExecution = true;

		public boolean announceServerStartStop = true;
		public boolean announcePlayerJoinLeave = true;
		public boolean announceDeathMessages = true;
		public boolean announceAdvancements = true;

		public boolean broadcastChatMessages = true;
		public boolean formatChatMessages = true;
		public List<String> allowedMentions = List.of("everyone", "users", "roles");
		public boolean useServerNickname = true;
		public int discordNewlineLimit = 3;

		public boolean announceHighMspt = true;
		public int msptCheckInterval = 5000;
		public int msptLimit = 50;

		public boolean whitelistRequiresAdmin = true;

		public boolean notifyUpdates = true;
		public boolean mentionAdminsForUpdates = false;

		public boolean updateChannelTopic = true;
		public int channelTopicUpdateInterval = 600000;

		public boolean shutdownImmediately = false;

		public List<String> excludedCommands = List.of("\\/msg ([^@].*)", "\\/tell ([^@].*)", "\\/tellraw ([^@].*)", "\\/w ([^@].*)", "\\/teammsg (.*)", "\\/tm (.*)");

		public List<String> adminsIds = new ArrayList<>();
	}

	public static class MultiServer {
		public boolean enable = false;
		public String host = "127.0.0.1";
		public int port = 5000;
		public String name = "SMP";
		public List<String> botIds = new ArrayList<>();
	}

	@SuppressWarnings("unused")
	public static class CustomMessage {
		public String unformattedResponseMessage = "";
		public String unformattedChatMessage = "";
		public String unformattedOtherMessage = "";
		public String unformattedCommandNotice = "";

		public String formattedResponseMessage = "";
		public String formattedChatMessage = "";
		public String formattedOtherMessage = "";
		public String formattedCommandNotice = "";

		public String messageWithoutWebhook = "";
		public String messageWithoutWebhookForMultiServer = "";

		public String serverStarted = "";
		public String serverStopped = "";

		public String joinServer = "";
		public String leftServer = "";

		public String deathMessage = "";

		public String advancementTask = "";
		public String advancementGoal = "";
		public String advancementChallenge = "";

		public String highMspt = "";

		public String offlineChannelTopic = "";
		public String onlineChannelTopic = "";
		public String onlineChannelTopicForMultiServer = "";
	}
}

