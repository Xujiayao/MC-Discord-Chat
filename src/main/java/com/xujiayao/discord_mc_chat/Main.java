package com.xujiayao.discord_mc_chat;

import com.xujiayao.discord_mc_chat.minecraft.MinecraftEventListener;
import com.xujiayao.discord_mc_chat.utils.PlaceholderParser;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Webhook;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.fabricmc.api.DedicatedServerModInitializer;
//#if MC >= 11900
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//#else
//$$ import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
//#endif
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.exception.ExceptionUtils;
//#if MC >= 11700
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
//$$ import org.apache.logging.log4j.Logger;
//$$ import org.apache.logging.log4j.LogManager;
//#endif
import com.xujiayao.discord_mc_chat.discord.DiscordEventListener;
import com.xujiayao.discord_mc_chat.minecraft.MinecraftCommands;
import com.xujiayao.discord_mc_chat.multi_server.MultiServer;
import com.xujiayao.discord_mc_chat.utils.ConfigManager;
import com.xujiayao.discord_mc_chat.utils.ConsoleLogListener;
import com.xujiayao.discord_mc_chat.utils.Translations;
import com.xujiayao.discord_mc_chat.utils.Utils;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Timer;

/**
 * @author Xujiayao
 */
public class Main implements DedicatedServerModInitializer {

	public static final OkHttpClient HTTP_CLIENT = new OkHttpClient();
	//#if MC >= 11700
	public static final Logger LOGGER = LoggerFactory.getLogger("Discord-MC-Chat");
	//#else
	//$$ public static final Logger LOGGER = LogManager.getLogger("Discord-MC-Chat");
	//#endif
	public static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "discord-mc-chat.json");
	public static final File CONFIG_BACKUP_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "discord-mc-chat-backup.json");
	public static final String VERSION = FabricLoader.getInstance().getModContainer("discord-mc-chat").orElseThrow().getMetadata().getVersion().getFriendlyString();
	public static Config CONFIG;
	public static JDA JDA;
	public static TextChannel CHANNEL;
	public static Webhook WEBHOOK;
	public static TextChannel CONSOLE_LOG_CHANNEL;
	public static Thread CONSOLE_LOG_THREAD = new Thread(new ConsoleLogListener(true));
	public static TextChannel UPDATE_NOTIFICATION_CHANNEL;
	public static long MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
	public static int MINECRAFT_SEND_COUNT = 0;
	public static MinecraftServer SERVER;
	public static Timer MSPT_MONITOR_TIMER = new Timer();
	public static Timer CHANNEL_TOPIC_MONITOR_TIMER = new Timer();
	public static Timer CHECK_UPDATE_TIMER = new Timer();
	public static MultiServer MULTI_SERVER;
	public static String SERVER_STARTED_TIME;

	@Override
	public void onInitializeServer() {
		try {
			ConfigManager.init(false);
			Translations.init();

			LOGGER.info("-----------------------------------------");
			LOGGER.info("Discord-MC-Chat (DMCC) " + VERSION);
			LOGGER.info("By Xujiayao");
			LOGGER.info("");
			LOGGER.info("More information + Docs:");
			LOGGER.info("https://blog.xujiayao.com/posts/4ba0a17a/");
			LOGGER.info("-----------------------------------------");

			JDA = JDABuilder.createDefault(CONFIG.generic.botToken)
					.setChunkingFilter(ChunkingFilter.ALL)
					.setMemberCachePolicy(MemberCachePolicy.ALL)
					.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.MESSAGE_CONTENT)
					.addEventListeners(new DiscordEventListener())
					.setStatus(CONFIG.generic.showServerStatusInBotStatus ? OnlineStatus.DO_NOT_DISTURB : OnlineStatus.ONLINE)
					.build();

			JDA.awaitReady();

			CHANNEL = JDA.getTextChannelById(CONFIG.generic.channelId);
			if (CHANNEL == null) {
				throw new NullPointerException("Invalid Channel ID");
			}
			if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
				CONSOLE_LOG_CHANNEL = JDA.getTextChannelById(CONFIG.generic.consoleLogChannelId);
				if (CONSOLE_LOG_CHANNEL == null) {
					throw new NullPointerException("Invalid Console Log Channel ID");
				}
				CONSOLE_LOG_THREAD.start();
			}
			if (!CONFIG.generic.updateNotificationChannelId.isEmpty()) {
				UPDATE_NOTIFICATION_CHANNEL = JDA.getTextChannelById(CONFIG.generic.updateNotificationChannelId);
				if (UPDATE_NOTIFICATION_CHANNEL == null) {
					UPDATE_NOTIFICATION_CHANNEL = CHANNEL;
					throw new NullPointerException("Invalid Update Notification Channel ID");
				}
				// TODO This (including other messages, /reload) should be checked every time before sending!
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
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
			System.exit(1);
		}

		if (CONFIG.multiServer.enable) {
			MULTI_SERVER = new MultiServer();
			MULTI_SERVER.start();
		}

		//#if MC >= 11900
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> MinecraftCommands.register(dispatcher));
		//#else
		//$$ CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> MinecraftCommands.register(dispatcher));
		//#endif

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			SERVER_STARTED_TIME = Long.toString(Instant.now().getEpochSecond());

			SERVER = server;

			if (CONFIG.generic.announceServerStartStop) {
				String message = PlaceholderParser.parseServerStarted().getString();

				CHANNEL.sendMessage(message).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, message);
				}
			}

			Utils.setBotPresence();

			Utils.initCheckUpdateTimer();

			if (CONFIG.generic.announceHighMspt) {
				Utils.initMsptMonitor();
			}

			if (CONFIG.generic.updateChannelTopic) {
				if (!CONFIG.multiServer.enable) {
					Utils.initChannelTopicMonitor();
				} else if (MULTI_SERVER.server != null) {
					MULTI_SERVER.initMultiServerChannelTopicMonitor();
				}
			}
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			if (CONFIG.generic.showServerStatusInBotStatus) {
				JDA.getPresence().setStatus(OnlineStatus.DO_NOT_DISTURB);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			MSPT_MONITOR_TIMER.cancel();
			CHANNEL_TOPIC_MONITOR_TIMER.cancel();
			CHECK_UPDATE_TIMER.cancel();

			CONSOLE_LOG_THREAD.interrupt();
			try {
				CONSOLE_LOG_THREAD.join(5000);
			} catch (Exception e) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}

			if (CONFIG.generic.updateChannelTopic) {
				String topic = PlaceholderParser.parseOfflineChannelTopic(
						Long.toString(Instant.now().getEpochSecond())
				).getString();

				CHANNEL.getManager().setTopic(topic).queue();
				if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
					CONSOLE_LOG_CHANNEL.getManager().setTopic(topic).queue();
				}
			}

			String message = PlaceholderParser.parseServerStopped().getString();

			if (CONFIG.multiServer.enable) {
				if (CONFIG.generic.announceServerStartStop) {
					MULTI_SERVER.sendMessage(false, false, false, null, message);
				}
				MULTI_SERVER.bye();
				MULTI_SERVER.stopMultiServer();
			}

			if (CONFIG.generic.announceServerStartStop) {
				CHANNEL.sendMessage(message)
						.submit()
						.whenComplete((v, ex) -> shutdown());
			} else {
				shutdown();
			}
		});

		MinecraftEventListener.init();
	}

	private void shutdown() {
		try {
			JDA.shutdown();
			if (!JDA.awaitShutdown(Duration.ofSeconds(10))) {
				if (CONFIG.generic.shutdownImmediately || !JDA.awaitShutdown(Duration.ofSeconds(900))) {
					JDA.shutdownNow();
				}
			}
			HTTP_CLIENT.connectionPool().evictAll();
			HTTP_CLIENT.dispatcher().executorService().shutdown();
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}
