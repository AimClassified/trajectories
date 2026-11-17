package dev.trajectories;

import java.util.Arrays;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client-side /traj command: /traj [toggle|hud|bow|throw|idle|walls|smooth|width <n>|ticks <n>|color <line|entity|miss> <RRGGBB>|status] */
@SideOnly(Side.CLIENT)
public class TrajectoryCommand extends CommandBase {

    private static final String PREFIX = "\u00a78[\u00a7bTrajectories\u00a78] \u00a77";

    @Override
    public String getCommandName() {
        return "traj";
    }

    @Override
    public List<String> getCommandAliases() {
        return Arrays.asList("trajectories");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/traj [toggle|hud|bow|throw|idle|walls|smooth|width <1-10>|ticks <20-1200>|color <line|entity|miss> <RRGGBB>|status]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        String sub = args.length == 0 ? "toggle" : args[0].toLowerCase();
        switch (sub) {
            case "toggle":
                TrajectoryConfig.enabled = !TrajectoryConfig.enabled;
                say(sender, "Trajectories " + flag(TrajectoryConfig.enabled));
                break;
            case "hud":
                TrajectoryConfig.showHud = !TrajectoryConfig.showHud;
                say(sender, "HUD " + flag(TrajectoryConfig.showHud));
                break;
            case "bow":
                TrajectoryConfig.showBow = !TrajectoryConfig.showBow;
                say(sender, "Bow " + flag(TrajectoryConfig.showBow));
                break;
            case "throw":
                TrajectoryConfig.showThrowables = !TrajectoryConfig.showThrowables;
                say(sender, "Throwables " + flag(TrajectoryConfig.showThrowables));
                break;
            case "idle":
                TrajectoryConfig.showWhenIdle = !TrajectoryConfig.showWhenIdle;
                say(sender, "Idle full-power bow preview " + flag(TrajectoryConfig.showWhenIdle));
                break;
            case "walls":
                TrajectoryConfig.throughWalls = !TrajectoryConfig.throughWalls;
                say(sender, "Draw through walls " + flag(TrajectoryConfig.throughWalls));
                break;
            case "smooth":
                TrajectoryConfig.smoothAim = !TrajectoryConfig.smoothAim;
                say(sender, "Smooth aim " + flag(TrajectoryConfig.smoothAim));
                break;
            case "width":
                if (args.length < 2) {
                    say(sender, "Usage: /traj width <1-10>");
                    return;
                }
                try {
                    TrajectoryConfig.lineWidth = TrajectoryConfig.clamp(Float.parseFloat(args[1]), 1.0F, 10.0F);
                    say(sender, "Line width " + TrajectoryConfig.lineWidth);
                } catch (NumberFormatException e) {
                    say(sender, "That is not a number.");
                    return;
                }
                break;
            case "ticks":
                if (args.length < 2) {
                    say(sender, "Usage: /traj ticks <20-1200>");
                    return;
                }
                try {
                    TrajectoryConfig.maxTicks = Math.max(20, Math.min(1200, Integer.parseInt(args[1])));
                    say(sender, "Max simulated ticks " + TrajectoryConfig.maxTicks);
                } catch (NumberFormatException e) {
                    say(sender, "That is not a whole number.");
                    return;
                }
                break;
            case "color":
                if (args.length < 3) {
                    say(sender, "Usage: /traj color <line|entity|miss> <RRGGBB>");
                    return;
                }
                int rgb = TrajectoryConfig.parseColor(args[2], -1);
                if (rgb < 0) {
                    say(sender, "Colour must be hex RRGGBB, e.g. 00E5FF.");
                    return;
                }
                String which = args[1].toLowerCase();
                if (which.equals("line")) {
                    TrajectoryConfig.lineColor = rgb;
                } else if (which.equals("entity")) {
                    TrajectoryConfig.entityColor = rgb;
                } else if (which.equals("miss")) {
                    TrajectoryConfig.missColor = rgb;
                } else {
                    say(sender, "Pick line, entity or miss.");
                    return;
                }
                say(sender, which + " colour set to #" + TrajectoryConfig.hex(rgb));
                break;
            case "status":
                say(sender, "enabled=" + TrajectoryConfig.enabled + " hud=" + TrajectoryConfig.showHud
                        + " bow=" + TrajectoryConfig.showBow + " throw=" + TrajectoryConfig.showThrowables
                        + " idle=" + TrajectoryConfig.showWhenIdle + " walls=" + TrajectoryConfig.throughWalls
                        + " smooth=" + TrajectoryConfig.smoothAim + " width=" + TrajectoryConfig.lineWidth
                        + " ticks=" + TrajectoryConfig.maxTicks);
                return;
            default:
                say(sender, getCommandUsage(sender));
                return;
        }
        TrajectoryConfig.save();
    }

    private static String flag(boolean on) {
        return on ? "\u00a7aon" : "\u00a7coff";
    }

    private static void say(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText(PREFIX + msg));
    }
}
