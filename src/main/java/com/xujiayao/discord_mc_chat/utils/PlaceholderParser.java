package com.xujiayao.discord_mc_chat.utils;

import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.node.parent.ClickActionNode;
import eu.pb4.placeholders.api.node.parent.FormattingNode;
import eu.pb4.placeholders.api.node.parent.HoverNode;
import eu.pb4.placeholders.api.parsers.MarkdownLiteParserV1;
import eu.pb4.placeholders.api.parsers.NodeParser;
import eu.pb4.placeholders.api.parsers.ParserBuilder;
import eu.pb4.placeholders.api.parsers.TagLikeParser;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;

import static com.xujiayao.discord_mc_chat.Main.SERVER;

/**
 * @author Xujiayao
 */
public class PlaceholderParser {

	private static final NodeParser MINECRAFT_PARSER = ParserBuilder.of()
			.globalPlaceholders()
			.quickText()
			.simplifiedTextFormat()
			.markdown(MarkdownLiteParserV1::defaultSpoilerFormatting,
					MarkdownLiteParserV1::defaultQuoteFormatting,
					PlaceholderParser::customUrlFormatting,
					MarkdownLiteParserV1.MarkdownFormat.values())
			.build();

	private static final NodeParser DISCORD_PARSER = ParserBuilder.of()
			.globalPlaceholders()
			.build();

	private static TextNode customUrlFormatting(TextNode[] textNodes, TextNode url) {
		// TODO Toggle: Yellow vs Blue
		// TODO Hover "Open URL" translation
		return new ClickActionNode(TextNode.array(new HoverNode<>(TextNode.array(new FormattingNode(textNodes, ChatFormatting.YELLOW, ChatFormatting.UNDERLINE)), HoverNode.Action.TEXT, TextNode.of("Open URL"))), ClickEvent.Action.OPEN_URL, url);
	}

	public static Component parseOtherMessage(String server, Component message) {
		Map<String, TextNode> placeholders = Map.of(
				"server", TextNode.of(server),
				"message", TextNode.convert(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(MINECRAFT_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.otherMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseCommandNotice(String name, String roleName, String roleColor, String command) {
		Map<String, TextNode> placeholders = Map.of(
				"name", TextNode.of(name),
				"roleName", TextNode.of(roleName),
				"roleColor", TextNode.of(roleColor),
				"command", TextNode.of(command)
		);

		NodeParser parser = ParserBuilder.of()
				.add(MINECRAFT_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.commandNotice")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseResponseMessage(String server, String name, String roleName, String message) {
		Map<String, TextNode> placeholders = Map.of(
				"server", TextNode.of(server),
				"name", TextNode.of(name),
				"roleName", TextNode.of(roleName),
				"message", TextNode.of(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(MINECRAFT_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.responseMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseChatMessage(String server, String name, String roleName, String message) {
		Map<String, TextNode> placeholders = Map.of(
				"server", TextNode.of(server),
				"name", TextNode.of(name),
				"roleName", TextNode.of(roleName),
				"message", TextNode.of(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(MINECRAFT_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.chatMessage")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseServerStarted() {
		return DISCORD_PARSER.parseText(TextNode.of(Translations.translateMessage("message.serverStarted")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseServerStopped() {
		return DISCORD_PARSER.parseText(TextNode.of(Translations.translateMessage("message.serverStopped")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseAdvancement(AdvancementType type, ServerPlayer player, String playerName, String advancement, String description) {
		Map<String, TextNode> placeholders = Map.of(
				"playerName", TextNode.of(playerName),
				"advancement", TextNode.of(advancement),
				"description", TextNode.of(description)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		switch (type) {
			case GOAL -> {
				return parser.parseText(TextNode.of(Translations.translateMessage("message.advancementGoal")), PlaceholderContext.of(player).asParserContext());
			}
			case TASK -> {
				return parser.parseText(TextNode.of(Translations.translateMessage("message.advancementTask")), PlaceholderContext.of(player).asParserContext());
			}
			case CHALLENGE -> {
				return parser.parseText(TextNode.of(Translations.translateMessage("message.advancementChallenge")), PlaceholderContext.of(player).asParserContext());
			}
		}
		return TextNode.of("[TranslateError: Unknown advancement type]").toText();
	}

	public static Component parseOnlineChannelTopic(String onlinePlayerCount, String maxPlayerCount, String uniquePlayerCount, String serverStartedTime, String lastUpdateTime, String nextUpdateTime) {
		Map<String, TextNode> placeholders = Map.of(
				"onlinePlayerCount", TextNode.of(onlinePlayerCount),
				"maxPlayerCount", TextNode.of(maxPlayerCount),
				"uniquePlayerCount", TextNode.of(uniquePlayerCount),
				"serverStartedTime", TextNode.of(serverStartedTime),
				"lastUpdateTime", TextNode.of(lastUpdateTime),
				"nextUpdateTime", TextNode.of(nextUpdateTime)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.onlineChannelTopic")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseOnlineChannelTopicForMultiServer(String onlinePlayerCount, String maxPlayerCount, String uniquePlayerCount, String onlineServerCount, String onlineServerList, String serverStartedTime, String lastUpdateTime, String nextUpdateTime) {
		Map<String, TextNode> placeholders = Map.of(
				"onlinePlayerCount", TextNode.of(onlinePlayerCount),
				"maxPlayerCount", TextNode.of(maxPlayerCount),
				"uniquePlayerCount", TextNode.of(uniquePlayerCount),
				"onlineServerCount", TextNode.of(onlineServerCount),
				"onlineServerList", TextNode.of(onlineServerList),
				"serverStartedTime", TextNode.of(serverStartedTime),
				"lastUpdateTime", TextNode.of(lastUpdateTime),
				"nextUpdateTime", TextNode.of(nextUpdateTime)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.onlineChannelTopicForMultiServer")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseOfflineChannelTopic(String lastUpdateTime) {
		Map<String, TextNode> placeholders = Map.of(
				"lastUpdateTime", TextNode.of(lastUpdateTime)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.offlineChannelTopic")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseHighMspt(String mspt, String msptLimit) {
		Map<String, TextNode> placeholders = Map.of(
				"mspt", TextNode.of(mspt),
				"msptLimit", TextNode.of(msptLimit)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.highMspt")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseDeathMessage(ServerPlayer player, String deathMessage, String playerName) {
		Map<String, TextNode> placeholders = Map.of(
				"deathMessage", TextNode.of(deathMessage),
				"playerName", TextNode.of(playerName)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.deathMessage")), PlaceholderContext.of(player).asParserContext());
	}

	public static Component parseMessageWithoutWebhook(String name, String message) {
		Map<String, TextNode> placeholders = Map.of(
				"name", TextNode.of(name),
				"message", TextNode.of(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.messageWithoutWebhook")), PlaceholderContext.of(SERVER).asParserContext());
	}

	public static Component parseMessageWithoutWebhookForMultiServer(String server, String name, String message) {
		Map<String, TextNode> placeholders = Map.of(
				"server", TextNode.of(server),
				"name", TextNode.of(name),
				"message", TextNode.of(message)
		);

		NodeParser parser = ParserBuilder.of()
				.add(DISCORD_PARSER)
				.customTags(TagLikeParser.PLACEHOLDER_ALTERNATIVE, TagLikeParser.Provider.placeholder(placeholders::get))
				.build();

		return parser.parseText(TextNode.of(Translations.translateMessage("message.messageWithoutWebhookForMultiServer")), PlaceholderContext.of(SERVER).asParserContext());
	}
}
