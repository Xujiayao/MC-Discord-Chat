package com.xujiayao.discord_mc_chat.minecraft;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.xujiayao.discord_mc_chat.utils.MarkdownParser;
import com.xujiayao.discord_mc_chat.utils.PlaceholderParser;
import com.xujiayao.discord_mc_chat.utils.Translations;
import com.xujiayao.discord_mc_chat.utils.Utils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import net.fellbaum.jemoji.EmojiManager;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
//#if MC < 11900
//$$ import net.minecraft.network.chat.TextComponent;
//#endif
import net.minecraft.network.chat.contents.TranslatableContents;
//#if MC <= 11802
//$$ import net.minecraft.server.level.ServerPlayer;
//#endif
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.xujiayao.discord_mc_chat.Main.CHANNEL;
import static com.xujiayao.discord_mc_chat.Main.CONFIG;
import static com.xujiayao.discord_mc_chat.Main.HTTP_CLIENT;
import static com.xujiayao.discord_mc_chat.Main.JDA;
import static com.xujiayao.discord_mc_chat.Main.LOGGER;
import static com.xujiayao.discord_mc_chat.Main.MINECRAFT_LAST_RESET_TIME;
import static com.xujiayao.discord_mc_chat.Main.MINECRAFT_SEND_COUNT;
import static com.xujiayao.discord_mc_chat.Main.MULTI_SERVER;
import static com.xujiayao.discord_mc_chat.Main.SERVER;
import static com.xujiayao.discord_mc_chat.Main.WEBHOOK;

/**
 * @author Xujiayao
 */
public class MinecraftEventListener {

	public static void init() {
		MinecraftEvents.COMMAND_MESSAGE.register((message, commandSourceStack) -> {
			String avatarUrl;

			// TODO May directly link to PLAYER_MESSAGE
			//#if MC > 11802
			if (commandSourceStack.isPlayer()) {
				avatarUrl = getAvatarUrl(commandSourceStack.getPlayer());
			//#else
			//$$ if (commandSourceStack.getEntity() instanceof ServerPlayer) {
			//$$ 	avatarUrl = getAvatarUrl((ServerPlayer) commandSourceStack.getEntity());
			//#endif
			} else {
				avatarUrl = JDA.getSelfUser().getAvatarUrl();
			}

			sendDiscordMessage(message, commandSourceStack.getDisplayName().getString(), avatarUrl);
			if (CONFIG.multiServer.enable) {
				MULTI_SERVER.sendMessage(false, true, false, commandSourceStack.getDisplayName().getString(), message);
			}
		});

		MinecraftEvents.PLAYER_MESSAGE.register((player, message) -> {
			String contentToDiscord = message;
			String contentToMinecraft = message;

			if (StringUtils.countMatches(contentToDiscord, ":") >= 2) {
				String[] emojiNames = StringUtils.substringsBetween(contentToDiscord, ":", ":");
				for (String emojiName : emojiNames) {
					List<RichCustomEmoji> emojis = JDA.getEmojisByName(emojiName, true);
					if (!emojis.isEmpty()) {
						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, (":" + emojiName + ":"), emojis.getFirst().getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (ChatFormatting.YELLOW + ":" + MarkdownSanitizer.escape(emojiName) + ":" + ChatFormatting.RESET));
					} else if (EmojiManager.getByAlias(emojiName).isPresent()) {
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (":" + emojiName + ":"), (ChatFormatting.YELLOW + ":" + MarkdownSanitizer.escape(emojiName) + ":" + ChatFormatting.RESET));
					}
				}
			}

			if (!CONFIG.generic.allowedMentions.isEmpty() && contentToDiscord.contains("@")) {
				if (CONFIG.generic.allowedMentions.contains("users")) {
					for (Member member : CHANNEL.getMembers()) {
						String usernameMention = "@" + member.getUser().getName();
						String displayNameMention = "@" + member.getUser().getEffectiveName();
						String formattedMention = ChatFormatting.YELLOW + "@" + member.getEffectiveName() + ChatFormatting.RESET;

						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, usernameMention, member.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, usernameMention, MarkdownSanitizer.escape(formattedMention));

						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, displayNameMention, member.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, displayNameMention, MarkdownSanitizer.escape(formattedMention));

						if (member.getNickname() != null) {
							String nicknameMention = "@" + member.getNickname();
							contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, nicknameMention, member.getAsMention());
							contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, nicknameMention, MarkdownSanitizer.escape(formattedMention));
						}
					}
				}

				if (CONFIG.generic.allowedMentions.contains("roles")) {
					for (Role role : CHANNEL.getGuild().getRoles()) {
						String roleMention = "@" + role.getName();
						String formattedMention = ChatFormatting.YELLOW + "@" + role.getName() + ChatFormatting.RESET;
						contentToDiscord = StringUtils.replaceIgnoreCase(contentToDiscord, roleMention, role.getAsMention());
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, roleMention, MarkdownSanitizer.escape(formattedMention));
					}
				}

				if (CONFIG.generic.allowedMentions.contains("everyone")) {
					contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@everyone", ChatFormatting.YELLOW + "@everyone" + ChatFormatting.RESET);
					contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, "@here", ChatFormatting.YELLOW + "@here" + ChatFormatting.RESET);
				}
			}

			for (String protocol : new String[]{"http://", "https://"}) {
				if (contentToMinecraft.contains(protocol)) {
					String[] links = StringUtils.substringsBetween(contentToMinecraft, protocol, " ");
					if (!StringUtils.substringAfterLast(contentToMinecraft, protocol).contains(" ")) {
						links = ArrayUtils.add(links, StringUtils.substringAfterLast(contentToMinecraft, protocol));
					}
					for (String link : links) {
						if (link.contains("\n")) {
							link = StringUtils.substringBefore(link, "\n");
						}

						contentToMinecraft = contentToMinecraft.replace(link, MarkdownSanitizer.escape(link));
					}
				}
			}

			contentToMinecraft = MarkdownParser.parseMarkdown(contentToMinecraft.replace("\\", "\\\\"));

			for (String protocol : new String[]{"http://", "https://"}) {
				if (contentToMinecraft.contains(protocol)) {
					String[] links = StringUtils.substringsBetween(contentToMinecraft, protocol, " ");
					if (!StringUtils.substringAfterLast(contentToMinecraft, protocol).contains(" ")) {
						links = ArrayUtils.add(links, StringUtils.substringAfterLast(contentToMinecraft, protocol));
					}
					for (String link : links) {
						if (link.contains("\n")) {
							link = StringUtils.substringBefore(link, "\n");
						}

						String hyperlinkInsert;
						if (StringUtils.containsIgnoreCase(link, "gif")
								&& StringUtils.containsIgnoreCase(link, "tenor.com")) {
							hyperlinkInsert = "\"},{\"text\":\"<gif>\",\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{\"text\":\"";
						} else {
							hyperlinkInsert = "\"},{\"text\":\"" + protocol + link + "\",\"underlined\":true,\"color\":\"yellow\",\"clickEvent\":{\"action\":\"open_url\",\"value\":\"" + protocol + link + "\"},\"hoverEvent\":{\"action\":\"show_text\",\"contents\":[{\"text\":\"Open URL\"}]}},{\"text\":\"";
						}
						contentToMinecraft = StringUtils.replaceIgnoreCase(contentToMinecraft, (protocol + link), hyperlinkInsert);
					}
				}
			}

			if (CONFIG.generic.broadcastChatMessages) {
				sendDiscordMessage(contentToDiscord, Objects.requireNonNull(player.getDisplayName()).getString(), getAvatarUrl(player));
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, true, false, Objects.requireNonNull(player.getDisplayName()).getString(), CONFIG.generic.formatChatMessages ? contentToMinecraft : message);
				}
			}

			if (CONFIG.generic.formatChatMessages) {
				return Optional.ofNullable(Utils.fromJson("[{\"text\":\"" + contentToMinecraft + "\"}]"));
			} else {
				return Optional.empty();
			}
		});

		MinecraftEvents.PLAYER_COMMAND.register((player, command) -> {
			if (CONFIG.generic.broadcastPlayerCommandExecution) {
				for (String excludedCommand : CONFIG.generic.excludedCommands) {
					if (command.matches(excludedCommand)) {
						return;
					}
				}

				if ((System.currentTimeMillis() - MINECRAFT_LAST_RESET_TIME) > 20000) {
					MINECRAFT_SEND_COUNT = 0;
					MINECRAFT_LAST_RESET_TIME = System.currentTimeMillis();
				}

				MINECRAFT_SEND_COUNT++;
				if (MINECRAFT_SEND_COUNT <= 20) {
					//#if MC >= 11900
					MutableComponent message = Component.literal("<" + Objects.requireNonNull(player.getDisplayName()).getString() + "> " + command);
					//#else
					//$$ MutableComponent message = new TextComponent("<" + Objects.requireNonNull(player.getDisplayName()).getString() + "> " + command);
					//#endif

					SERVER.getPlayerList().getPlayers().forEach(
							player1 -> player1.displayClientMessage(message, false));
					//#if MC >= 11900
					SERVER.sendSystemMessage(message);
					//#elseif MC > 11502
					//$$ SERVER.sendMessage(message, player.getUUID());
					//#else
					//$$ SERVER.sendMessage(message);
					//#endif

					sendDiscordMessage(MarkdownSanitizer.escape(command), Objects.requireNonNull(player.getDisplayName()).getString(), getAvatarUrl(player));
					if (CONFIG.multiServer.enable) {
						MULTI_SERVER.sendMessage(false, true, false, player.getDisplayName().getString(), MarkdownSanitizer.escape(command));
					}
				}
			}
		});

		MinecraftEvents.PLAYER_ADVANCEMENT.register((player, advancementHolder, isDone) -> {
			//#if MC >= 12002
			if (advancementHolder.value().display().isEmpty()) {
				return;
			}
			DisplayInfo display = advancementHolder.value().display().get();
			//#else
			//$$ if (advancementHolder.getDisplay() == null) {
			//$$ 	return;
			//$$ }
			//$$ DisplayInfo display = advancementHolder.getDisplay();
			//#endif

			if (CONFIG.generic.announceAdvancements
					&& isDone
					&& display.shouldAnnounceChat()
					&& player.level().getGameRules().getBoolean(GameRules.RULE_ANNOUNCE_ADVANCEMENTS)) {
				String title = Translations.translate("advancements." + advancementHolder.id().getPath().replace("/", ".") + ".title");
				String description = Translations.translate("advancements." + advancementHolder.id().getPath().replace("/", ".") + ".description");

				String message = PlaceholderParser.parseAdvancement(
						display.getType(),
						player,
						MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()),
						title.contains("TranslateError") ? display.getTitle().getString() : title,
						description.contains("TranslateError") ? display.getDescription().getString() : description
				).getString();

				CHANNEL.sendMessage(message).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, message);
				}
			}
		});

		MinecraftEvents.PLAYER_DIE.register(player -> {
			if (CONFIG.generic.announceDeathMessages) {
				//#if MC >= 11900
				TranslatableContents deathMessage = (TranslatableContents) player.getCombatTracker().getDeathMessage().getContents();
				//#else
				//$$ TranslatableComponent deathMessage = (TranslatableComponent) player.getCombatTracker().getDeathMessage();
				//#endif
				String key = deathMessage.getKey();

				String message = PlaceholderParser.parseDeathMessage(
						player,
						MarkdownSanitizer.escape(Translations.translate(key, deathMessage.getArgs())),
						MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString())
				).getString();

				CHANNEL.sendMessage(message).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, message);
				}
			}
		});

		MinecraftEvents.PLAYER_JOIN.register(player -> {
			Utils.setBotPresence();

			if (CONFIG.generic.announcePlayerJoinLeave) {
				CHANNEL.sendMessage(Translations.translateMessage("message.joinServer")
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.joinServer")
							.replace("%playerName%", MarkdownSanitizer.escape(player.getDisplayName().getString())));
				}
			}
		});

		MinecraftEvents.PLAYER_QUIT.register(player -> {
			Utils.setBotPresence();

			if (CONFIG.generic.announcePlayerJoinLeave) {
				CHANNEL.sendMessage(Translations.translateMessage("message.leftServer")
						.replace("%playerName%", MarkdownSanitizer.escape(Objects.requireNonNull(player.getDisplayName()).getString()))).queue();
				if (CONFIG.multiServer.enable) {
					MULTI_SERVER.sendMessage(false, false, false, null, Translations.translateMessage("message.leftServer")
							.replace("%playerName%", MarkdownSanitizer.escape(player.getDisplayName().getString())));
				}
			}
		});
	}

	private static void sendDiscordMessage(String content, String username, String avatar_url) {
		if (!CONFIG.generic.useWebhook) {
			if (CONFIG.multiServer.enable) {
				CHANNEL.sendMessage(PlaceholderParser.parseMessageWithoutWebhookForMultiServer(
						CONFIG.multiServer.name,
						username,
						content
				).getString()).queue();
			} else {
				CHANNEL.sendMessage(PlaceholderParser.parseMessageWithoutWebhook(
						username,
						content
				).getString()).queue();
			}
		} else {
			JsonObject body = new JsonObject();
			body.addProperty("content", content);
			body.addProperty("username", ((CONFIG.multiServer.enable) ? ("[" + CONFIG.multiServer.name + "] " + username) : username));
			body.addProperty("avatar_url", avatar_url);

			JsonObject allowedMentions = new JsonObject();
			allowedMentions.add("parse", new Gson().toJsonTree(CONFIG.generic.allowedMentions).getAsJsonArray());
			body.add("allowed_mentions", allowedMentions);

			Request request = new Request.Builder()
					.url(WEBHOOK.getUrl())
					.post(RequestBody.create(body.toString(), MediaType.get("application/json")))
					.build();

			ExecutorService executor = Executors.newFixedThreadPool(1);
			executor.submit(() -> {
				try (Response response = HTTP_CLIENT.newCall(request).execute()) {
					if (!response.isSuccessful()) {
						if (response.body() != null) {
							throw new Exception("Unexpected code " + response.code() + ": " + response.body().string());
						} else {
							throw new Exception("Unexpected code " + response.code());
						}
					}
				} catch (Exception e) {
					LOGGER.error(ExceptionUtils.getStackTrace(e));
				}
			});
			executor.shutdown();
		}
	}

	// {player_name} conflicts with nickname-changing mods
	// TODO Move to Placeholder class
	private static String getAvatarUrl(Player player) {
		String hash = "null";
		if (CONFIG.generic.avatarApi.contains("{player_textures}")) {
			try {
				//#if MC > 12001
				String textures = player.getGameProfile().getProperties().get("textures").iterator().next().value();
				//#else
				//$$ String textures = player.getGameProfile().getProperties().get("textures").iterator().next().getValue();
				//#endif

				JsonObject json = new Gson().fromJson(new String(Base64.getDecoder().decode(textures), StandardCharsets.UTF_8), JsonObject.class);
				String url = json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();

				hash = url.replace("http://textures.minecraft.net/texture/", "");
			} catch (NoSuchElementException ignored) {
			}
		}

		return CONFIG.generic.avatarApi
				.replace("{player_uuid}", player.getUUID().toString())
				.replace("{player_name}", player.getName().getString())
				.replace("{player_textures}", hash);
	}
}
