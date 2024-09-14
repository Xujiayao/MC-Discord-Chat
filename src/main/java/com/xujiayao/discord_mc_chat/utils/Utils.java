package com.xujiayao.discord_mc_chat.utils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.xujiayao.discord_mc_chat.multi_server.MultiServer;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.DetectedVersion;
//#if MC >= 12005
import net.minecraft.core.RegistryAccess;
//#endif
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//#if MC > 12002
import net.minecraft.server.ServerTickRateManager;
//#endif
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.UserWhiteList;
import net.minecraft.server.players.UserWhiteListEntry;
//#if MC > 11502
import net.minecraft.util.TimeUtil;
//#endif
import net.minecraft.util.Tuple;
import okhttp3.CacheControl;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import static com.xujiayao.discord_mc_chat.Main.CHANNEL;
import static com.xujiayao.discord_mc_chat.Main.CHANNEL_TOPIC_MONITOR_TIMER;
import static com.xujiayao.discord_mc_chat.Main.CHECK_UPDATE_TIMER;
import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static com.xujiayao.discord_mc_chat.Main.CONSOLE_LOG_CHANNEL;
import static com.xujiayao.discord_mc_chat.Main.CONSOLE_LOG_THREAD;
import static com.xujiayao.discord_mc_chat.Main.HTTP_CLIENT;
import static com.xujiayao.discord_mc_chat.Main.JDA;
import static com.xujiayao.discord_mc_chat.Main.LOGGER;
import static com.xujiayao.discord_mc_chat.Main.MSPT_MONITOR_TIMER;
import static com.xujiayao.discord_mc_chat.Main.MULTI_SERVER;
import static com.xujiayao.discord_mc_chat.Main.SERVER;
import static com.xujiayao.discord_mc_chat.Main.SERVER_STARTED_TIME;
import static com.xujiayao.discord_mc_chat.Main.UPDATE_NOTIFICATION_CHANNEL;
import static com.xujiayao.discord_mc_chat.Main.VERSION;
import static com.xujiayao.discord_mc_chat.Main.WEBHOOK;

/**
 * @author Xujiayao
 */
public class Utils {

	public static boolean isAdmin(Member member) {
		if (CONFIG.generic.adminsIds.contains(member.getId())) {
			return true;
		}

		for (Role role : member.getRoles()) {
			if (CONFIG.generic.adminsIds.contains(role.getId())) {
				return true;
			}
		}

		return false;
	}

	public static String adminsMentionString() {
		StringBuilder text = new StringBuilder();

		for (String id : CONFIG.generic.adminsIds) {
			User user = JDA.getUserById(id);
			Role role = JDA.getRoleById(id);
			if (user != null) {
				text.append(user.getAsMention()).append(" ");
			} else if (role != null) {
				text.append(role.getAsMention()).append(" ");
			} else {
				throw new NullPointerException("User or role not found for target ID");
			}
		}

		return text.toString();
	}

	public static String checkUpdate(boolean isManualCheck) {
		try {
			Request request = new Request.Builder()
					.url("https://cdn.jsdelivr.net/gh/Xujiayao/Discord-MC-Chat@master/update/versions.json")
					.cacheControl(CacheControl.FORCE_NETWORK)
					.build();

			try (Response response = HTTP_CLIENT.newCall(request).execute()) {
				String result = Objects.requireNonNull(response.body()).string();

				String minecraftVersion = DetectedVersion.tryDetectVersion().getName();

				String latestVersion = "";
				String latestChangelog = "";

				JsonArray versions = new Gson().fromJson(result, JsonObject.class).getAsJsonArray("versions");
				for (JsonElement versionElement : versions) {
					boolean isCompatible = false;

					JsonObject object = versionElement.getAsJsonObject();

					JsonArray minecraft_dependency = object.getAsJsonArray("minecraft_dependency");
					for (JsonElement dependencyElement : minecraft_dependency) {
						String minecraft = dependencyElement.getAsString();

						if (minecraft.startsWith("~")) {
							String thisMinorVersion;
							if (StringUtils.countMatches(minecraft, ".") == 1) {
								thisMinorVersion = minecraft.substring(3);
							} else {
								thisMinorVersion = StringUtils.substringBetween(minecraft, ".");
							}

							String serverMinorVersion;
							if (StringUtils.countMatches(minecraftVersion, ".") == 1) {
								serverMinorVersion = minecraftVersion.substring(2);
							} else {
								serverMinorVersion = StringUtils.substringBetween(minecraftVersion, ".");
							}

							if (serverMinorVersion.equals(thisMinorVersion)) {
								int thisPatchVersion;
								if (minecraft.substring(5).isEmpty()) {
									thisPatchVersion = 0;
								} else {
									thisPatchVersion = Integer.parseInt(minecraft.substring(6));
								}

								int serverPatchVersion;
								if (minecraftVersion.substring(4).isEmpty()) {
									serverPatchVersion = 0;
								} else {
									serverPatchVersion = Integer.parseInt(minecraftVersion.substring(5));
								}

								if (serverPatchVersion >= thisPatchVersion) {
									isCompatible = true;
									break;
								}
							}
						} else {
							if (minecraftVersion.equals(minecraft)) {
								isCompatible = true;
								break;
							}
						}
					}

					if (isCompatible) {
						latestVersion = object.get("version").getAsString();
						latestChangelog = object.get("changelog").getAsString();
						break;
					}
				}

				StringBuilder message = new StringBuilder();

				if (!latestVersion.equals(VERSION)) {
					if (latestVersion.equals(CONFIG.latestVersion)
							&& CONFIG.latestCheckTime > (System.currentTimeMillis() - 172800000)
							&& !isManualCheck) {
						return "";
					}

					CONFIG.latestVersion = latestVersion;
					CONFIG.latestCheckTime = System.currentTimeMillis();
					ConfigManager.update();

					message.append(Translations.translate("utils.utils.cUpdate.newVersionAvailable"));
					message.append("\n\n");
					message.append("**Discord-MC-Chat ").append(VERSION).append(" -> ").append(latestVersion).append("**");
					message.append("\n\n");
					message.append(Translations.translate("utils.utils.cUpdate.downloadLink"));
					message.append("\n\n");
					message.append(Translations.translate("utils.utils.cUpdate.changelog")).append(latestChangelog);
					message.append("\n\n");

					if (CONFIG.generic.mentionAdminsForUpdates) {
						message.append(adminsMentionString());
					}

					return message.toString();
				} else {
					CONFIG.latestVersion = latestVersion;
					CONFIG.latestCheckTime = System.currentTimeMillis();
					ConfigManager.update();

					message.append("**Discord-MC-Chat ").append(VERSION).append("**");
					message.append("\n\n");
					message.append(Translations.translate("utils.utils.cUpdate.upToDate"));

					return isManualCheck ? message.toString() : "";
				}
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
			return "";
		}
	}

	public static String whitelist(String player) {
		UserWhiteList whitelist = SERVER.getPlayerList().getWhiteList();

		Request request = new Request.Builder()
				.url("https://api.mojang.com/users/profiles/minecraft/" + player)
				.cacheControl(CacheControl.FORCE_NETWORK)
				.build();

		try (Response response = HTTP_CLIENT.newCall(request).execute()) {
			if (response.body() != null && response.code() == 200) {
				JsonObject json = new Gson().fromJson(response.body().string(), JsonObject.class);
				String id = json.get("id").getAsString();
				UUID uuid = UUID.fromString(String.format("%s-%s-%s-%s-%s", id.substring(0, 8), id.substring(8, 12), id.substring(12, 16), id.substring(16, 20), id.substring(20, 32)));
				String name = json.get("name").getAsString();

				GameProfile profile = new GameProfile(uuid, name);
				if (whitelist.isWhiteListed(profile)) {
					return Translations.translate("utils.utils.whitelist.whitelistFailed");
				} else {
					whitelist.add(new UserWhiteListEntry(profile));
					return Translations.translate("utils.utils.whitelist.whitelistSuccess", name);
				}
			} else if (response.code() == 404) {
				return Translations.translate("utils.utils.whitelist.playerNotExist");
			}
		} catch (Exception ex) {
			LOGGER.error(ExceptionUtils.getStackTrace(ex));
		}

		return "";
	}

	public static String reload() {
		try {
			MSPT_MONITOR_TIMER.cancel();
			CHANNEL_TOPIC_MONITOR_TIMER.cancel();
			CHECK_UPDATE_TIMER.cancel();

			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.bye();
				MULTI_SERVER.stopMultiServer();
			}

			ConfigManager.init(true);
			Translations.init();

			Utils.setBotPresence();

			CHANNEL = JDA.getTextChannelById(CONFIG.generic.channelId);
			if (CHANNEL == null) {
				throw new NullPointerException("Invalid Channel ID");
			}
			CONSOLE_LOG_THREAD.interrupt();
			CONSOLE_LOG_THREAD.join(5000);
			if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
				CONSOLE_LOG_CHANNEL = JDA.getTextChannelById(CONFIG.generic.consoleLogChannelId);
				if (CONSOLE_LOG_CHANNEL == null) {
					throw new NullPointerException("Invalid Console Log Channel ID");
				}
				CONSOLE_LOG_THREAD = new Thread(new ConsoleLogListener(false));
				CONSOLE_LOG_THREAD.start();
			}
			if (!CONFIG.generic.updateNotificationChannelId.isEmpty()) {
				UPDATE_NOTIFICATION_CHANNEL = JDA.getTextChannelById(CONFIG.generic.updateNotificationChannelId);
				if (UPDATE_NOTIFICATION_CHANNEL == null) {
					UPDATE_NOTIFICATION_CHANNEL = CHANNEL;
					throw new NullPointerException("Invalid Update Notification Channel ID");
				}
				if (!UPDATE_NOTIFICATION_CHANNEL.canTalk()) {
					UPDATE_NOTIFICATION_CHANNEL = CHANNEL;
					LOGGER.warn("Unable to send messages in the Update Notification Channel; Using the default channel instead.");
				}
			} else {
				UPDATE_NOTIFICATION_CHANNEL = CHANNEL;
			}

			String webhookName = "DMCC Webhook" + " (" + JDA.getSelfUser().getApplicationId() + ")";
			WEBHOOK = null;
			List<Webhook> webhooks;
			try {
				webhooks = CHANNEL.getGuild().retrieveWebhooks().complete();
			} catch (InsufficientPermissionException e) {
				LOGGER.error("Insufficient guild permission, the full functionality of DMCC Webhook Check is not available!", e);
				webhooks = CHANNEL.retrieveWebhooks().complete();
			}
			for (Webhook webhook : webhooks) {
				if (webhook.getName().contains("MCDC Webhook") || "DMCC Webhook".equals(webhook.getName())) {
					webhook.delete().queue();
					continue;
				}

				if (webhook.getOwnerAsUser() == JDA.getSelfUser() && !webhookName.equals(webhook.getName())) {
					webhook.delete().queue();
					continue;
				}

				if (webhookName.equals(webhook.getName())) {
					if (CONFIG.generic.useWebhook
							&& webhook.getOwnerAsUser() == JDA.getSelfUser()
							&& webhook.getChannel().asTextChannel() == CHANNEL) {
						WEBHOOK = webhook;
					} else {
						webhook.delete().queue();
					}
				}
			}
			if (CONFIG.generic.useWebhook && WEBHOOK == null) {
				WEBHOOK = CHANNEL.createWebhook(webhookName).complete();
			}

			Utils.updateBotCommands();

			CHECK_UPDATE_TIMER = new Timer();
			Utils.initCheckUpdateTimer();

			MSPT_MONITOR_TIMER = new Timer();
			if (CONFIG.generic.announceHighMspt) {
				Utils.initMsptMonitor();
			}

			if (CONFIG.multiServer.enable) {
				MULTI_SERVER = new MultiServer();
				MULTI_SERVER.start();
			}

			CHANNEL_TOPIC_MONITOR_TIMER = new Timer();
			if (CONFIG.generic.updateChannelTopic) {
				new Timer().schedule(new TimerTask() {
					@Override
					public void run() {
						if (!CONFIG.multiServer.enable) {
							Utils.initChannelTopicMonitor();
						} else if (MULTI_SERVER.server != null) {
							MULTI_SERVER.initMultiServerChannelTopicMonitor();
						}
					}
				}, 2000);
			}

			return Translations.translate("utils.utils.reload.success");
		} catch (Exception ex) {
			LOGGER.error(ExceptionUtils.getStackTrace(ex));
			return Translations.translate("utils.utils.reload.fail");
		}
	}

	public static String getHelpCommandMessage(boolean isDiscordSide) {
		StringBuilder message = new StringBuilder();

		message.append("=============== ");
		message.append(Translations.translate("utils.utils.ghcMessage.help"));
		message.append(" ===============");

		message.append(isDiscordSide ? "\n/help                | " : "\n/dmcc help                  | ");
		message.append(Translations.translate("utils.utils.ubCommands.help"));

		message.append(isDiscordSide ? "\n/info                | " : "\n/dmcc info                  | ");
		message.append(Translations.translate("utils.utils.ubCommands.info"));

		message.append(isDiscordSide ? "\n/stats <type> <name> | " : "\n/dmcc stats <type> <name>   | ");
		message.append(Translations.translate("utils.utils.ubCommands.stats"));

		message.append(isDiscordSide ? "\n/update              | " : "\n/dmcc update                | ");
		message.append(Translations.translate("utils.utils.ubCommands.update"));

		// TODO Simplify
		for (int i = 0; i < 5; i++) {
			if ((i == 0 && !CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 4 && isDiscordSide && CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 1 && !isDiscordSide && CONFIG.generic.whitelistRequiresAdmin)) {
				message.append(isDiscordSide ? "\n/whitelist <player>  | " : "\n/dmcc whitelist <player>    | ");
				message.append(Translations.translate("utils.utils.ubCommands.whitelist"));
				if (CONFIG.generic.whitelistRequiresAdmin) {
					message.append(Translations.translate("utils.utils.ghcMessage.adminOnly"));
				}
			} else if ((i == 1 && isDiscordSide && !CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 0 && isDiscordSide && CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 2 && !isDiscordSide)) {
				message.append(isDiscordSide ? "\n/console <command>   | " : "\n~~/dmcc console <command>~~ | ");
				message.append(Translations.translate("utils.utils.ubCommands.console"));
				message.append(Translations.translate("utils.utils.ghcMessage.adminOnly"));
			} else if ((i == 2 && isDiscordSide && !CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 1 && isDiscordSide && CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 3 && !isDiscordSide)) {
				message.append(isDiscordSide ? "\n/log <file>          | " : "\n~~/dmcc log <file>~~        | ");
				message.append(Translations.translate("utils.utils.ubCommands.log"));
				message.append(Translations.translate("utils.utils.ghcMessage.adminOnly"));
			} else if ((i == 3 && isDiscordSide && !CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 2 && isDiscordSide && CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 1 && !isDiscordSide && !CONFIG.generic.whitelistRequiresAdmin)
					|| (i == 0 && !isDiscordSide && CONFIG.generic.whitelistRequiresAdmin)) {
				message.append(isDiscordSide ? "\n/reload              | " : "\n/dmcc reload                | ");
				message.append(Translations.translate("utils.utils.ubCommands.reload"));
				message.append(Translations.translate("utils.utils.ghcMessage.adminOnly"));
			} else {
				message.append(isDiscordSide ? "\n/stop                | " : "\n~~/dmcc stop~~              | ");
				message.append(Translations.translate("utils.utils.ubCommands.stop"));
				message.append(Translations.translate("utils.utils.ghcMessage.adminOnly"));
			}
		}

		return message.toString();
	}

	public static String getInfoCommandMessage() {
		StringBuilder message = new StringBuilder()
				.append("=============== ")
				.append(Translations.translate("utils.utils.gicMessage.serverStatus"))
				.append(" ===============\n\n");

		// Online players
		List<ServerPlayer> onlinePlayers = SERVER.getPlayerList().getPlayers();
		message.append(Translations.translate("utils.utils.gicMessage.onlinePlayers", onlinePlayers.size(), SERVER.getMaxPlayers()));

		if (onlinePlayers.isEmpty()) {
			message.append(Translations.translate("utils.utils.gicMessage.noPlayersOnline"));
		} else {
			for (ServerPlayer player : onlinePlayers) {
				//#if MC >= 12002
				message.append("[").append(player.connection.latency()).append("ms] ").append(Objects.requireNonNull(player.getDisplayName()).getString()).append("\n");
				//#else
				//$$ message.append("[").append(player.latency).append("ms] ").append(Objects.requireNonNull(player.getDisplayName()).getString()).append("\n");
				//#endif
			}
		}

		// Server TPS
		message.append(Translations.translate("utils.utils.gicMessage.serverTps", String.format("%.2f", getTickInfo().getA())));

		// Server MSPT
		message.append(Translations.translate("utils.utils.gicMessage.serverMspt", String.format("%.2f", getTickInfo().getB())));

		// Server used memory
		message.append(Translations.translate("utils.utils.gicMessage.serverUsedMemory", (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024, Runtime.getRuntime().totalMemory() / 1024 / 1024));

		return message.toString();
	}

	public static String getStatsCommandMessage(String type, String name, boolean hasCharLimit) {
		StringBuilder message = new StringBuilder()
				.append("=============== ")
				.append(Translations.translate("utils.utils.gscMessage.scoreboard"))
				.append(" ===============\n");

		Map<String, Integer> stats = new HashMap<>();

		try {
			JsonArray players = new Gson().fromJson(IOUtils.toString(new File(FabricLoader.getInstance().getGameDir().toAbsolutePath() + "/usercache.json").toURI(), StandardCharsets.UTF_8), JsonArray.class);

			Properties properties = new Properties();
			properties.load(new FileInputStream("server.properties"));

			Collection<File> files = new ArrayList<>();
			try {
				files = FileUtils.listFiles(new File((properties.getProperty("level-name") + "/stats/")), null, false);
			} catch (Exception ignored) {
			}

			for (File file : files) {
				for (JsonElement player : players) {
					if (player.getAsJsonObject().get("uuid").getAsString().equals(file.getName().replace(".json", ""))) {
						JsonObject json = new Gson().fromJson(IOUtils.toString(file.toURI(), StandardCharsets.UTF_8), JsonObject.class);

						try {
							stats.put(player.getAsJsonObject().get("name").getAsString(), json
									.getAsJsonObject("stats")
									.getAsJsonObject("minecraft:" + type)
									.get("minecraft:" + name)
									.getAsInt());
						} catch (NullPointerException ignored) {
						}
					}
				}
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}

		if (stats.isEmpty()) {
			message.append(Translations.translate("utils.utils.gscMessage.noResult"));
		} else {
			List<Map.Entry<String, Integer>> sortedlist = new ArrayList<>(stats.entrySet());
			sortedlist.sort((c1, c2) -> c2.getValue().compareTo(c1.getValue()));

			for (Map.Entry<String, Integer> entry : sortedlist) {
				message.append(String.format("\n%-8d %-8s", entry.getValue(), entry.getKey()));
				if (message.length() > 1700 && hasCharLimit) {
					message.append("\n...\n\n");
					message.append(Translations.translate("utils.utils.gscMessage.truncated"));
					break;
				}
			}
		}

		return message.toString();
	}

	public static void setBotPresence() {
		if (SERVER == null) {
			// Bot is registered before official server start
			return;
		}

		if (CONFIG.generic.showServerStatusInBotStatus && SERVER.getPlayerCount() == 0) {
			JDA.getPresence().setStatus(OnlineStatus.IDLE);
		} else {
			JDA.getPresence().setStatus(OnlineStatus.ONLINE);
		}

		if (!CONFIG.generic.botPlayingActivity.isEmpty()) {
			JDA.getPresence().setActivity(Activity.playing(CONFIG.generic.botPlayingActivity
					.replace("%onlinePlayerCount%", Integer.toString(SERVER.getPlayerCount()))
					.replace("%maxPlayerCount%", Integer.toString(SERVER.getMaxPlayers()))));
		} else if (!CONFIG.generic.botListeningActivity.isEmpty()) {
			JDA.getPresence().setActivity(Activity.listening(CONFIG.generic.botListeningActivity
					.replace("%onlinePlayerCount%", Integer.toString(SERVER.getPlayerCount()))
					.replace("%maxPlayerCount%", Integer.toString(SERVER.getMaxPlayers()))));
		} else {
			JDA.getPresence().setActivity(null);
		}
	}

	public static void updateBotCommands() {
		JDA.updateCommands()
				.addCommands(Commands.slash("help", Translations.translate("utils.utils.ubCommands.help")))
				.addCommands(Commands.slash("info", Translations.translate("utils.utils.ubCommands.info")))
				.addCommands(Commands.slash("stats", Translations.translate("utils.utils.ubCommands.stats"))
						.addOption(OptionType.STRING, "type", Translations.translate("utils.utils.ubCommands.stats.type"), true)
						.addOption(OptionType.STRING, "name", Translations.translate("utils.utils.ubCommands.stats.name"), true))
				.addCommands(Commands.slash("update", Translations.translate("utils.utils.ubCommands.update")))
				.addCommands(Commands.slash("whitelist", Translations.translate("utils.utils.ubCommands.whitelist"))
						.addOption(OptionType.STRING, "player", Translations.translate("utils.utils.ubCommands.whitelist.player"), true))
				.addCommands(Commands.slash("console", Translations.translate("utils.utils.ubCommands.console"))
						.addOption(OptionType.STRING, "command", Translations.translate("utils.utils.ubCommands.console.command"), true, true))
				.addCommands(Commands.slash("log", Translations.translate("utils.utils.ubCommands.log"))
						.addOption(OptionType.STRING, "file", Translations.translate("utils.utils.ubCommands.log.file"), true, true))
				.addCommands(Commands.slash("reload", Translations.translate("utils.utils.ubCommands.reload")))
				.addCommands(Commands.slash("stop", Translations.translate("utils.utils.ubCommands.stop")))
				.queue();
	}

	public static void initMsptMonitor() {
		MSPT_MONITOR_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				double mspt = getTickInfo().getB();

				if (mspt > CONFIG.generic.msptLimit) {
					String message = PlaceholderParser.parseHighMspt(
							String.format("%.2f", mspt),
							Integer.toString(CONFIG.generic.msptLimit)
					).getString();

					CHANNEL.sendMessage(message).queue();
					if (CONFIG.multiServer.enable) {
						MULTI_SERVER.sendMessage(false, false, false, null, MarkdownParser.parseMarkdown(message));
					}
				}
			}
		}, CONFIG.generic.msptCheckInterval, CONFIG.generic.msptCheckInterval);
	}

	public static void initChannelTopicMonitor() {
		CHANNEL_TOPIC_MONITOR_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				try {
					long epochSecond = Instant.now().getEpochSecond();

					Properties properties = new Properties();
					properties.load(new FileInputStream("server.properties"));

					int uniquePlayerCount = 0;
					try {
						uniquePlayerCount = FileUtils.listFiles(new File((properties.getProperty("level-name") + "/stats/")), null, false).size();
					} catch (Exception ignored) {
					}

					String topic = PlaceholderParser.parseOnlineChannelTopic(
							Integer.toString(SERVER.getPlayerCount()),
							Integer.toString(SERVER.getMaxPlayers()),
							Integer.toString(uniquePlayerCount),
							SERVER_STARTED_TIME,
							Long.toString(epochSecond),
							Long.toString(epochSecond + CONFIG.generic.channelTopicUpdateInterval / 1000)
					).getString();

					CHANNEL.getManager().setTopic(topic).queue();

					if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
						CONSOLE_LOG_CHANNEL.getManager().setTopic(topic).queue();
					}
				} catch (Exception e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}
			}
		}, 0, CONFIG.generic.channelTopicUpdateInterval);
	}

	public static void initCheckUpdateTimer() {
		CHECK_UPDATE_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				if (CONFIG.latestCheckTime > System.currentTimeMillis()) {
					CONFIG.latestCheckTime = 0;
					ConfigManager.update();
				}

				String message = checkUpdate(false);
				if (!message.isEmpty() && CONFIG.generic.notifyUpdates) {
					UPDATE_NOTIFICATION_CHANNEL.sendMessage(message).queue();
				}
			}
		}, 0, 21600000);
	}

	private static Tuple<Double, Double> getTickInfo() {
		//#if MC > 12002
		ServerTickRateManager manager = SERVER.tickRateManager();

		double mspt = ((double) SERVER.getAverageTickTimeNanos()) / TimeUtil.NANOSECONDS_PER_MILLISECOND;

		double tps = 1000.0D / Math.max(manager.isSprinting() ? 0.0 : manager.millisecondsPerTick(), mspt);
		if (manager.isFrozen()) {
			tps = 0;
		}
		//#else
		//$$ // TODO Compat TPS & MSPT (Issue #217)
		//$$ double mspt = SERVER.getAverageTickTime();
		//$$ double tps = Math.min(1000.0 / mspt, 20);
		//#endif

		return new Tuple<>(tps, mspt);
	}

	public static MutableComponent fromJson(String json) {
		//#if MC >= 12005
		return Component.Serializer.fromJson(json, RegistryAccess.EMPTY);
		//#else
		//$$ return Component.Serializer.fromJson(json);
		//#endif
	}
}
