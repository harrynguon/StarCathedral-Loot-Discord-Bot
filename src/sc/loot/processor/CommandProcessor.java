package sc.loot.processor;

import sc.loot.util.Constants;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.impl.obj.Message;
import sx.blah.discord.handle.impl.obj.ReactionEmoji;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.MessageBuilder;
import sx.blah.discord.util.MessageHistory;

import java.awt.*;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CommandProcessor {

    /**
     * Process all input
     * @param message
     * @param prefix
     */
    public static void processCommand(IMessage message, String prefix, IDiscordClient client) {
        // discord user sender
        IUser sender = message.getAuthor();
        // discord channel
        IChannel channel = message.getChannel();
        // discord server
        IGuild guild = message.getGuild();

        // this only checks for the first occurrence of the role name. if the role does not exist,
        // then it exits, or if the sender does not have the role, then it exits.
        List<IRole> userRoles = guild.getRolesByName(Constants.BOT_AUTH_NAME);
        if (userRoles.isEmpty() || !sender.hasRole(userRoles.get(0))) {
            return;
        }

        // process imessage into string array with args and remove the prefix, then check
        // the corresponding command below
        String[] command = message.getContent().replaceFirst(prefix, "").split(" ");
        // !help or !HeLP; doesn't matter
        command[0] = command[0].toLowerCase();

        switch (command[0]) {
            case "setwelcome":
                if (command.length <= 1) {
                    sendInvalidArgumentMessage("setwelcome", channel, prefix);
                    return;
                }
                channel.sendMessage("To setwelcome, please message Rebuked");
                return;
            case "warn":
                if (command.length <= 2) {
                    sendInvalidArgumentMessage("warn", channel, prefix);
                    return;
                }
                Optional<IUser> __user = getUser(command[1], channel, guild);
                if (__user.isPresent()) {
                    IUser user = __user.get();
                    String warningMessage = createString(command, 2);
                    user.getOrCreatePMChannel().sendMessage("You have been warned for: `" +
                            warningMessage + "`");
                    // send to channel that the warn function was called
                    channel.sendMessage(user.mention() + " has been warned for: " + "`" +
                            warningMessage + "`");
                }
                return;
            // post a message to #board_of_punishments, pm the user informing they were banned,
            // and then ban the user.
            case "ban":
                if (command.length <=2) {
                    sendInvalidArgumentMessage("ban", channel, prefix);
                }
                Optional<IUser> userToBan = getUser(command[1], channel, guild);
                if (userToBan.isPresent()) {
                    IUser user = userToBan.get();
                    Optional<Message.Attachment> attachment =
                            message.getAttachments().isEmpty() ? Optional.empty() :
                                                        Optional.of(message.getAttachments().get(0));
                    String banMessage = createString(command, 2);
                    // TODO: create embed message to beautify the banning message
                    IMessage banMsg = new MessageBuilder(client)
                            .withContent(user.mention() + " has been banned for: `" + banMessage +
                                    "`\n")
                            .appendContent(attachment.isPresent() ? attachment.get().getUrl() : "")
                            .withChannel(message.getChannel())
                            .build();
                    user.getOrCreatePMChannel()
                            .sendMessage("You have been banned " +
                                    "from the SC Loot Discord server for: `" + banMessage + "`");
                    guild.banUser(user);
                }
                return;
            case "weeklyreport":
                createReport(client, Constants.WEEKLY);
                return;
            // for testing purposes, will be automated.
            case "monthlyreport":
                createReport(client, Constants.MONTHLY);
                return;
            case "help":
                channel.sendMessage(Constants.HELP_MESSAGE);
                return;
            case "changestatus":
                if (command.length < 2) {
                    return;
                }
                String newStatus = createString(command, 1);
                client.changePresence(StatusType.ONLINE, ActivityType.PLAYING, newStatus);
                channel.sendMessage("My online status has been changed to: `" + newStatus + "`");
                return;
            case "getroleid":
                if (command.length < 2) {
                    return;
                }
                String roleName = createString(command, 1);
                channel.sendMessage("The role ID for `" + roleName + "` is: " +
                        guild.getRolesByName(roleName).get(0).getLongID());
                return;
            default:
                sendInvalidArgumentMessage("invalidcommand", channel, prefix);
                return;
        }

    }

    /**
     * Concatenate the message contents into a String seperated by spaces.
     * @param command
     * @param startingIndex
     * @return
     */
    private static String createString(String[] command, int startingIndex) {
        StringBuilder message = new StringBuilder();
        for (int i = startingIndex; i < command.length; i++) {
            if (i == command.length - 1) {
                message.append(command[i]);
            } else {
                message.append(command[i] + " ");
            }
        }
        return message.toString();
    }

    /**
     * Creates the report and submits it to #weekly_report or #monthly_report, depending on its type
     * @param client
     */
    public static void createReport(IDiscordClient client, String reportType) {
        IGuild guild = client.getGuildByID(Constants.SC_LOOT_GUILD_ID);
        IChannel channel =
                reportType.equals(Constants.WEEKLY) ?
                guild.getChannelByID(Constants.WEEKLY_REPORT_CHANNEL_ID):
                guild.getChannelByID(Constants.MONTHLY_REPORT_CHANNEL_ID);

        // IChannel channel = guild.getChannelByID(413975567931670529L); // test channel ID
        Map<String, Integer> itemCount = createHashTable();
        final Instant currentTime = Instant.now();
        // Zoneoffset.UTC for UTC zone (future reference)
        final LocalDateTime currentTimeLDT = LocalDateTime.ofInstant(
                currentTime,
                ZoneOffset.systemDefault()
        );

        int numDaysInTheMonth = currentTimeLDT.toLocalDate().lengthOfMonth();

        final MessageHistory messageHistory =
                reportType.equals(Constants.WEEKLY) ?
                guild.getChannelByID(Constants.SC_LOOT_CHANNEL_ID)
                    .getMessageHistoryTo(currentTime.minus(Period.ofDays(7)))
                :
                guild.getChannelByID(Constants.SC_LOOT_CHANNEL_ID)
                        .getMessageHistoryTo(currentTime.minus(Period.ofDays(numDaysInTheMonth)));
        IMessage[] messages = messageHistory.asArray();
        Predicate<IMessage> withinTheTimePeriod =
                reportType.equals(Constants.WEEKLY) ?
                m -> m.getTimestamp().isAfter(currentTime.minus(Period.ofDays(7)))
                :
                m -> m.getTimestamp().isAfter(currentTime.minus(Period.ofDays(numDaysInTheMonth)));

        long totalMessages = Stream.of(messages)
                .filter(withinTheTimePeriod)
                .count();

        Set<IMessage> messagesForReactionPost = new HashSet<>();

        // process each message
        Stream.of(messages)
                .filter(withinTheTimePeriod)
                .forEach(m -> {
                    processMessage(m, itemCount);
                    if (m.getReactions().size() != 0) {
                        messagesForReactionPost.add(m);
                    }
                });

        // custom comparator that stores messages by their most-highest reaction count
        Comparator<IMessage> mostReactions = Comparator.comparingInt(
                m -> m.getReactions()
                        .stream()
                        .max(Comparator.comparingInt(r -> r.getCount())).get()
                        .getCount()
        );

        int maxReactionSubmissions =
                reportType.equals(Constants.WEEKLY) ?
                        5
                        :
                        10;

        // a list of the top N messages by their highest single reaction count, sorted in descending
        // order (from highest to lowest)
        List<IMessage> topReactionMessages = messagesForReactionPost.stream()
                .sorted(mostReactions.reversed())
                .limit(maxReactionSubmissions)
                .collect(Collectors.toList());

        System.out.println("Data has been processed for the "+ reportType + " report");

        LocalDate crtTimeMinusTimePeriod =
                reportType.equals(Constants.WEEKLY) ?
                currentTimeLDT.toLocalDate().minusDays(7)
                :
                currentTimeLDT.toLocalDate().minus(Period.ofDays(numDaysInTheMonth));

        EmbedBuilder builder1 = new EmbedBuilder();
        EmbedBuilder builder2 = new EmbedBuilder();
        builder1.withTitle(reportType + " Item Drop Count Report from __" +
                crtTimeMinusTimePeriod + "__ to __" +
                currentTimeLDT.toLocalDate() + "__ with a total number of `" +
                totalMessages + "` submissions.");
        builder2.withTitle("cont.");
        Random random = new Random();
        int r = random.nextInt(255);
        int g = random.nextInt(255);
        int b = random.nextInt(255);
        Color color = new Color(r, g, b);
        builder1.withColor(color);
        builder2.withColor(color);

        itemCount.entrySet()
                .stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    String k = entry.getKey();
                    int v = entry.getValue();
                    if (v > 0) {
                        if (builder1.getFieldCount() < 25) {
                            builder1.appendField(
                                    "<:" + k + ":" + guild.getEmojiByName(k).getLongID() + ">",
                                    "`Drop Count: " + v + "`", true);
                        } else {
                            builder2.appendField(
                                    "<:" + k + ":" + guild.getEmojiByName(k).getLongID() + ">",
                                    "`Drop Count: " + v + "`", true);
                        }
                    }
        });
        channel.sendMessage(builder1.build());
        channel.sendMessage(builder2.build());

        // MOST REACTION STATISTICS MESSAGE //
        EmbedBuilder statistics = new EmbedBuilder();
        statistics.withTitle("Extras");
        statistics.appendField("__Reactions__",
                "Top "+ maxReactionSubmissions +" distinct reactions from different " +
                        "submissions during this week.", true);

        // append all top reaction messages
        for (int i = 0; i < topReactionMessages.size(); i++) {
            IReaction reaction = topReactionMessages.get(i)
                    .getReactions()
                    .stream()
                    .max(Comparator.comparingInt(IReaction::getCount)).get();
            ReactionEmoji emoji = reaction.getEmoji();
            int noReactions = reaction.getCount();

            String message = "" + topReactionMessages.get(i) +
                    " \n*which has **" + noReactions +
                    "** <:" + emoji.getName() + ":" + emoji.getLongID() + ">" + " reactions.*";

            //add each post as a field to the post.
            int subNumber = i+1;
            statistics.appendField("Submission #" + subNumber + ":", message, true);
        }

        statistics.withColor(color);
        channel.sendMessage(statistics.build());

        System.out.println("Messages have been sent to the " + reportType + " report channel.");

        // send a log to #sc_loot_bot
        IChannel scLootBotChannel = guild.getChannelByID(Constants.SC_LOOT_BOT_CHANNEL_ID);
        new MessageBuilder(client).withChannel(scLootBotChannel)
                .withContent("`" + reportType + "` has just been initiated. The time is: "
                        + currentTime + ".")
                .build();

        System.out.println("A log has just been sent.");
    }

    /**
     * Finds the IUser from the input of the command (command[1]).
     * @param userName
     * @param channel
     * @param guild
     * @return
     */
    private static Optional<IUser> getUser(String userName, IChannel channel, IGuild guild) {
        userName = userName.replaceAll("[<>@!]", "");
        for (char alphabet = 'A'; alphabet <= 'Z'; alphabet++) {
            if (userName.toUpperCase().indexOf(alphabet) >= 0){
                channel.sendMessage(userName + " Is not a valid user!");
                return Optional.empty();
            }
        }
        return Optional.of(guild.getUserByID(Long.parseLong(userName)));
    }

    /**
     * The bot will send a message with why the user has sent an invalid command
     * @param type
     * @param channel
     * @param prefix
     */
    private static void sendInvalidArgumentMessage(String type, IChannel channel, String prefix) {
        switch(type) {
            case "setwelcome":
                channel.sendMessage("Please enter valid arguments!" +
                        " `[" + prefix + "setwelcome <String[]:message>]`");
                break;
            case "setrolecolour":
                channel.sendMessage("Please enter valid arguments!" +
                        " `[" + prefix + "setrolecolour <role> <colour>]`");
                break;
            case "kick":
                channel.sendMessage("Please enter valid arguments!" +
                        " `[" + prefix + "kick [@user]`");
                break;
            case "warn":
                channel.sendMessage("Please enter valid arguments!" +
                        " `[" + prefix + "warn [@user <message>]`");
                break;
            case "ban":
                break;
            case "invalidcommand":
                channel.sendMessage("Please enter a valid command!" +
                        " Type `" + prefix + "help` to view the available commands");
                break;
        }
    }

    /**
     * Collect segments of the message and update item Count Hash table
     * @param message
     * @param itemCount
     */
    private static void processMessage(IMessage message, Map<String, Integer> itemCount) {
        String content = message.getContent();
        Scanner scan = new Scanner(content);
        String previous = "";
        while (scan.hasNext()) {
            String segment = scan.next().toLowerCase();
            String finalPrevious = previous;
            Stream.of(Constants.ITEMS)
                    .filter(item -> segment.contains(item))
                    .forEach(item -> {
                        // check if the item is an implant. if so, check if the previous word is
                        // "inf"
                        if (Constants.IMPLANTS.contains(item)) {
                            if (!finalPrevious.contains("inf")) {
                                itemCount.put(item, itemCount.get(item) + 1);
                            }
                        } else {
                            itemCount.put(item, itemCount.get(item) + 1);
                        }
                    });
            previous = segment;
        }
        scan.close();
    }

    /**
     * Initialises the map of items, each with a count of 0 for the weekly/monthly report
     * @return
     */
    private static Map<String, Integer> createHashTable() {
        Map<String, Integer> itemCount = new HashMap<>();
        Stream.of(Constants.ITEMS).forEach(item -> itemCount.put(item, 0));
        return itemCount;
    }

}
