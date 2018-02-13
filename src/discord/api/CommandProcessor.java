package discord.api;

import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

public class CommandProcessor {

    public static void processCommand(IMessage message, String prefix) {
        IUser sender = message.getAuthor();
        IChannel channel = message.getChannel();
        IGuild guild = message.getGuild();

        // process imessage into string array with args and remove the prefix
        String[] command = message.getContent().toLowerCase().replaceFirst(prefix, "").split(" ");

        if (command[0].equals("ping")) {
            channel.sendMessage("pong!");
        } else if (command[0].equals("avatar")) {
            if (command.length == 2) {
                command[1] = command[1].replaceAll("[<>@!]", "");
                for (char alphabet = 'A'; alphabet <= 'Z'; alphabet++) {
                    if (command[1].toUpperCase().indexOf(alphabet) >= 0) {
                        channel.sendMessage(command[1] + " Is not a valid user!");
                        return;
                    }
                }
                IUser user = guild.getUserByID(Long.parseLong(command[1]));
                channel.sendMessage(user.mention() + "'s avatar: " + user.getAvatarURL()) ;
            } else {
                channel.sendMessage("Please enter valid arguments! `[" + prefix + "avatar <@user>]`");
            }
        }
    }

}
