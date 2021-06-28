/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package com.aliucord.api;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.aliucord.*;
import com.aliucord.entities.Plugin;
import com.aliucord.utils.ReflectUtils;
import com.aliucord.wrappers.ChannelWrapper;
import com.aliucord.wrappers.messages.MessageWrapper;
import com.discord.api.commands.ApplicationCommandData;
import com.discord.api.commands.ApplicationCommandType;
import com.discord.api.message.*;
import com.discord.api.message.embed.MessageEmbed;
import com.discord.models.commands.*;
import com.discord.models.domain.NonceGenerator;
import com.discord.models.user.User;
import com.discord.stores.StoreApplicationInteractions;
import com.discord.stores.StoreMessages;
import com.discord.stores.StoreStream;
import com.discord.utilities.SnowflakeUtils;
import com.discord.utilities.message.LocalMessageCreatorsKt;
import com.discord.utilities.time.Clock;
import com.discord.utilities.time.ClockFactory;
import com.discord.utilities.user.UserUtils;
import com.discord.widgets.chat.MessageContent;
import com.discord.widgets.chat.input.ChatInputViewModel;
import com.discord.widgets.chat.input.WidgetChatInput;
import com.discord.widgets.chat.input.WidgetChatInput$configureSendListeners$2;
import com.discord.widgets.chat.list.sheet.WidgetApplicationCommandBottomSheetViewModel;
import com.lytefast.flexinput.R$d;
import com.lytefast.flexinput.R$g;
import com.lytefast.flexinput.model.Attachment;

import java.util.*;

import kotlin.Unit;
import kotlin.jvm.functions.Function1;

@SuppressWarnings("unused")
public class CommandsAPI {
    private static final Logger logger = new Logger("CommandsAPI");
    public static class CommandResult {
        public String content;
        public List<MessageEmbed> embeds;
        public boolean send;
        public String username;
        public String avatarUrl;

        /**
         * calls {@link CommandResult#CommandResult(String, List, boolean)} with default arguments.
         * @see CommandResult#CommandResult(String, List, boolean)
         */
        public CommandResult() { this(null); }

        /**
         * calls {@link CommandResult#CommandResult(String, List, boolean)} with default arguments.
         * @param content Output message content
         * @see CommandResult#CommandResult(String, List, boolean)
         */
        public CommandResult(@Nullable String content) {
            this(content, null, true);
        }

        /**
         * @param content Output message content
         * @param embeds Embeds to include in the command output. Requires <code>send</code> to be false.
         * @param send Whether to send the message or not. If false, messages will appear locally, otherwise they'll be sent to the current channel.
         */
        public CommandResult(@Nullable String content, @Nullable List<MessageEmbed> embeds, boolean send) {
            this.content = content;
            this.embeds = embeds;
            this.send = send;
        }

        /**
         * @param content Output message content
         * @param embeds Embeds to include in the command output. Requires <code>send</code> to be false.
         * @param send Whether to send the message or not. If false, messages will appear locally, otherwise they'll be sent to the current channel.
         * @param username Username for Clyde's customization. Requires <code>send</code> to be false.
         */
        public CommandResult(@Nullable String content, @Nullable List<MessageEmbed> embeds, boolean send, String username) {
            this.content = content;
            this.embeds = embeds;
            this.username = username;
            this.send = send;
        }

        /**
         * @param content Output message content
         * @param embeds Embeds to include in the command output. Requires <code>send</code> to be false.
         * @param send Whether to send the message or not. If false, messages will appear locally, otherwise they'll be sent to the current channel.
         * @param username Username for Clyde. Requires <code>send</code> to be false.
         * @param avatarUrl Avatar URL for Clyde, must be a direct link, not a redirect. Requires <code>send</code> to be false.
         */
        public CommandResult(@Nullable String content, @Nullable List<MessageEmbed> embeds, boolean send, @Nullable String username, @Nullable String avatarUrl) {
            this.content = content;
            this.embeds = embeds;
            this.username = username;
            this.avatarUrl = avatarUrl;
            this.send = send;
        }
    }

    public static final long ALIUCORD_APP_ID = generateId();
    public static final String DONT_SEND_RESULT = "{ALIUCORD_COMMAND}";
    private static final Application aliucordApplication = new Application(ALIUCORD_APP_ID, "Aliucord", null, R$d.ic_slash_command_24dp, 0, null, true);
    public static Map<String, RemoteApplicationCommand> commands = new HashMap<>();
    public static Map<String, String> commandsAndPlugins = new HashMap<>();
    public static Map<Long, WidgetApplicationCommandBottomSheetViewModel.StoreState> interactionsStore = new HashMap<>();
    public static ApplicationCommandOption messageOption
            = new ApplicationCommandOption(ApplicationCommandType.STRING, "message", null, R$g.command_shrug_message_description, false, false, null, null);
    public static ApplicationCommandOption requiredMessageOption
            = new ApplicationCommandOption(ApplicationCommandType.STRING, "message", null, R$g.command_shrug_message_description, true, false, null, null);

    private static void _registerCommand(
            String pluginName,
            String name,
            String description,
            List<ApplicationCommandOption> options,
            Function1<? super Map<String, ?>, CommandResult> execute
    ) {
        RemoteApplicationCommand command = new RemoteApplicationCommand(generateIdString(), ALIUCORD_APP_ID, name, description, options, null, null, null, null, args -> {
            Clock clock = ClockFactory.get();
            long id = NonceGenerator.computeNonce(clock);
            long channelId = StoreStream.getChannelsSelected().getId();
            User me = StoreStream.getUsers().getMe();
            Message thinkingMsg = LocalMessageCreatorsKt.createLocalApplicationCommandMessage(
                    id, name, channelId, UserUtils.INSTANCE.synthesizeApiUser(me), Utils.buildClyde(null, null), false, true, id, clock);
            Class<Message> c = Message.class;
            try {
                ReflectUtils.setField(c, thinkingMsg, "flags", MessageFlags.EPHEMERAL | MessageFlags.LOADING, true);
                ReflectUtils.setField(c, thinkingMsg, "type", MessageTypes.LOCAL, true);
            } catch (Throwable ignored) {}
            StoreMessages storeMessages = StoreStream.getMessages();
            StoreMessages.access$handleLocalMessageCreate(storeMessages, thinkingMsg);

            WidgetChatInput$configureSendListeners$2 _this = (WidgetChatInput$configureSendListeners$2) args.get("__this");
            Object[] _args = (Object[]) args.get("__args");
            args.remove("__this");
            args.remove("__args");

            if (_this == null || _args == null) return null;
            MessageContent content = _this.$chatInput.getMatchedContentWithMetaData();
            WidgetChatInput.clearInput$default(_this.this$0, false, true, 0, null);

            Utils.threadPool.execute(() -> {
                try {
                    CommandResult res = execute.invoke(args);
                    if (res == null) {
                        storeMessages.deleteMessage(thinkingMsg);
                        return;
                    }
                    boolean hasContent = res.content != null && !res.content.equals("");
                    boolean hasEmbeds = res.embeds != null && res.embeds.size() != 0;
                    if (!res.send) {
                        if (!hasContent && !hasEmbeds) {
                            storeMessages.deleteMessage(thinkingMsg);
                            return;
                        }

                        try {
                            Message commandMessage = LocalMessageCreatorsKt.createLocalMessage(res.content == null ? "" : res.content, channelId, Utils.buildClyde(res.username, res.avatarUrl), null, false, false, null, null, clock, null, null, null, null, null, null, null);

                            ReflectUtils.setField(c, commandMessage, "embeds", res.embeds, true);
                            ReflectUtils.setField(c, commandMessage, "flags", MessageFlags.EPHEMERAL, true);
                            ReflectUtils.setField(c, commandMessage, "interaction", MessageWrapper.getInteraction(thinkingMsg), true);

                            // TODO: add arguments
                            long guildId = ChannelWrapper.getGuildId(StoreStream.getChannels().getChannel(channelId));
                            interactionsStore.put(id, new WidgetApplicationCommandBottomSheetViewModel.StoreState(
                                me,
                                guildId == 0 ? null : StoreStream.getGuilds().getMembers().get(guildId).get(me.getId()),
                                new StoreApplicationInteractions.State.Loaded(new ApplicationCommandData("", "", "", name, Collections.emptyList())),
                                CommandsAPI.getAliucordApplication(),
                                Collections.emptySet(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
                                Collections.emptyMap()
                            ));

                            StoreMessages.access$handleLocalMessageCreate(storeMessages, commandMessage);
                        } catch (Throwable e) { logger.error((String) null, e); }
                    } else {
                        if (hasEmbeds)
                            logger.error(String.format("[%s]", name), new IllegalArgumentException("Embeds may not be specified when send is set to true"));
                        List<? extends Attachment<?>> attachments = (List<? extends Attachment<?>>) _args[0];
                        if (!hasContent && attachments.size() == 0) {
                            storeMessages.deleteMessage(thinkingMsg);
                            return;
                        }

                        Utils.mainThread.post(() -> ChatInputViewModel.sendMessage$default(
                                WidgetChatInput.access$getViewModel$p(_this.this$0),
                                _this.$context,
                                _this.$messageManager,
                                new MessageContent(res.content, content != null ? content.getMentionedUsers() : Collections.emptyList()),
                                attachments,
                                false,
                                (Function1<? super Boolean, Unit>) _args[2],
                                16,
                                null
                        ));
                    }
                    storeMessages.deleteMessage(thinkingMsg);
                } catch (Throwable t) {
                    t.printStackTrace();
                    storeMessages.deleteMessage(thinkingMsg);
                    logger.error(String.format("[%s]", name), t);

                    StringBuilder argStringB = new StringBuilder();
                    for (Map.Entry<String, ?> entry : args.entrySet()) {
                        argStringB.append(entry).append('\n');
                    }
                    String argString = argStringB.toString();

                    Plugin.Manifest manifest = Objects.requireNonNull(PluginManager.plugins.get(pluginName)).getManifest();

                    String detailedError = String.format(
                            Locale.ENGLISH,
                            "Oops! Something went wrong while running this command:\n```java\n%s```\n" +
                            "Please search for this error on the Aliucord server to see if it's a known issue. " +
                            "If it isn't, report it to the plugin author%s.\n\n" +
                            "Debug:```\nCommand: %s\nPlugin: %s v%s\nDiscord v%s\nAndroid %s (SDK %d)\nAliucord %s```\nArguments:```\n%s```\n",
                            t.toString(),
                            manifest.authors.length != 0 ? " (" + manifest.authors[0].name + ")" : "",
                            name,
                            pluginName,
                            manifest.version,
                            Constants.DISCORD_VERSION,
                            Build.VERSION.RELEASE,
                            Build.VERSION.SDK_INT,
                            BuildConfig.GIT_REVISION,
                            argString.length() != 0 ? argString : "-"
                    );
                    Message commandMessage = LocalMessageCreatorsKt.createLocalMessage(detailedError, channelId, Utils.buildClyde(null, null), null, false, false, null, null, clock, null, null, null, null, null, null, null);

                    try {
                        ReflectUtils.setField(c, commandMessage, "flags", MessageFlags.EPHEMERAL, true);
                    } catch (Throwable ignored) {}
                    StoreMessages.access$handleLocalMessageCreate(storeMessages, commandMessage);
                }
            });
            return null;
        });
        try {
            ReflectUtils.setField(ApplicationCommand.class, command, "builtIn", true, true);
        } catch (Throwable e) { logger.error(e); }
        commands.put(name, command);
        updateCommandCount();
    }

    private static void _unregisterCommand(String name) {
        commands.remove(name);
        updateCommandCount();
    }

    public static Application getAliucordApplication() {
        updateCommandCount();
        return aliucordApplication;
    }

    private static void updateCommandCount() {
        if (aliucordApplication.getCommandCount() != commands.size()) {
            try {
                ReflectUtils.setField(aliucordApplication, "commandCount", commands.size(), true);
            } catch (Throwable ignored) {}
        }
    }

    public static long generateId() {
        return -SnowflakeUtils.fromTimestamp(System.currentTimeMillis() * 100);
    }

    public static String generateIdString() {
        return String.valueOf(generateId());
    }

    public final String pluginName;
    public final List<String> pluginCommands = new ArrayList<>();
    public CommandsAPI(String plugin) {
        pluginName = plugin;
    }

    /**
     * Registers a slash command.
     * @param name Name of the command.
     * @param description Description of the command.
     * @param options Arguments for the command. see {@link ApplicationCommandOption}
     * @param execute Callback for the command.
     */
    public void registerCommand(
            @NonNull String name,
            @NonNull String description,
            @NonNull List<ApplicationCommandOption> options,
            @NonNull Function1<? super Map<String, ?>, CommandResult> execute
    ) {
        _registerCommand(pluginName, name, description, options, execute);
        commandsAndPlugins.put(name, pluginName);
        pluginCommands.add(name);
    }

    /**
     * Unregisters a command.
     * @param name Command to unregister.
     */
    public void unregisterCommand(String name) {
        _unregisterCommand(name);
        commandsAndPlugins.remove(name);
        pluginCommands.remove(name);
    }

    /**
     * Unregisters all commands
     */
    public void unregisterAll() {
        for (String name : pluginCommands) {
            _unregisterCommand(name);
            commandsAndPlugins.remove(name);
        }
        pluginCommands.clear();
    }
}
