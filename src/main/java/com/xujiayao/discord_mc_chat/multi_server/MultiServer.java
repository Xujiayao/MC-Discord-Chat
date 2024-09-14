package com.xujiayao.discord_mc_chat.multi_server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.xujiayao.discord_mc_chat.utils.PlaceholderParser;
import org.apache.commons.lang3.exception.ExceptionUtils;
import com.xujiayao.discord_mc_chat.multi_server.client.Client;
import com.xujiayao.discord_mc_chat.multi_server.server.Server;
import com.xujiayao.discord_mc_chat.multi_server.server.UserThread;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimerTask;

import static com.xujiayao.discord_mc_chat.Main.CHANNEL;
import static com.xujiayao.discord_mc_chat.Main.CHANNEL_TOPIC_MONITOR_TIMER;
import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static com.xujiayao.discord_mc_chat.Main.CONSOLE_LOG_CHANNEL;
import static com.xujiayao.discord_mc_chat.Main.LOGGER;

/**
 * @author Xujiayao
 */
public class MultiServer extends Thread {

	public Server server;
	public Client client;
	public Set<JsonObject> channelTopicInfoList;

	@Override
	public void run() {
		try {
			client = new Client();
			client.connect();
		} catch (Exception e) {
			try {
				server = new Server();
				server.start();

				Thread.sleep(500);

				client = new Client();
				client.connect();
			} catch (Exception ex) {
				LOGGER.error(ExceptionUtils.getStackTrace(e));
			}
		}
	}

	public void sendMessage(boolean special, boolean isChat, boolean isText, String playerName, String message) {
		JsonObject json = new JsonObject();
		json.addProperty("serverName", CONFIG.multiServer.name);
		json.addProperty("special", special);
		json.addProperty("isChat", isChat);
		json.addProperty("isText", isText);
		json.addProperty("playerName", playerName);
		json.addProperty("message", message);

		client.writeThread.write(json.toString());
	}

	public void initMultiServerChannelTopicMonitor() {
		CHANNEL_TOPIC_MONITOR_TIMER.schedule(new TimerTask() {
			@Override
			public void run() {
				channelTopicInfoList = new HashSet<>();

				JsonObject json = new JsonObject();
				json.addProperty("special", true);
				json.addProperty("isChat", false);
				json.addProperty("playerName", "null");
				json.addProperty("message", "{\"type\":\"updateChannelTopic\"}");

				server.broadcast(json.toString());

				try {
					Thread.sleep(2000);
				} catch (Exception e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}

				int onlinePlayerCount = 0;
				int maxPlayerCount = 0;
				Set<String> uniquePlayers = new HashSet<>();
				int onlineServerCount = 0;
				Set<String> onlineServerList = new HashSet<>();
				Set<Long> serverStartedTime = new HashSet<>();

				for (JsonObject infoJson : channelTopicInfoList) {
					onlineServerCount++;

					onlinePlayerCount += infoJson.get("onlinePlayerCount").getAsInt();

					maxPlayerCount += infoJson.get("maxPlayerCount").getAsInt();

					String[] uniquePlayersList = new Gson().fromJson(infoJson.get("uniquePlayers").getAsJsonArray(), String[].class);
					uniquePlayers.addAll(List.of(uniquePlayersList));

					onlineServerList.add(infoJson.get("serverName").getAsString());

					serverStartedTime.add(Long.parseLong(infoJson.get("serverStartedTime").getAsString()));
				}

				long epochSecond = Instant.now().getEpochSecond();

				String topic = PlaceholderParser.parseOnlineChannelTopicForMultiServer(
						Integer.toString(onlinePlayerCount),
						Integer.toString(maxPlayerCount),
						Integer.toString(uniquePlayers.size()),
						Integer.toString(onlineServerCount),
						String.join(", ", onlineServerList),
						Long.toString(Collections.min(serverStartedTime)),
						Long.toString(epochSecond),
						Long.toString(epochSecond + CONFIG.generic.channelTopicUpdateInterval / 1000)
				).getString();

				CHANNEL.getManager().setTopic(topic).queue();

				if (!CONFIG.generic.consoleLogChannelId.isEmpty()) {
					CONSOLE_LOG_CHANNEL.getManager().setTopic(topic).queue();
				}
			}
		}, 0, CONFIG.generic.channelTopicUpdateInterval);
	}

	public void bye() {
		client.writeThread.write("bye");
	}

	public void stopMultiServer() {
		try {
			client.socket.close();

			if (server != null) {
				server.users.forEach(UserThread::stopUserThread);
				server.serverSocket.close();
			}
		} catch (Exception e) {
			LOGGER.error(ExceptionUtils.getStackTrace(e));
		}
	}
}
