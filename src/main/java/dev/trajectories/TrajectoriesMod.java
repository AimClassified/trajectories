package dev.trajectories;

import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;

@Mod(modid = TrajectoriesMod.MODID,
     name = "Trajectories",
     version = TrajectoriesMod.VERSION,
     clientSideOnly = true,
     acceptableRemoteVersions = "*",
     acceptedMinecraftVersions = "[1.8.9]")
public class TrajectoriesMod {

    public static final String MODID = "trajectories";
    public static final String VERSION = "1.0.0";

    private KeyBinding toggleKey;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return; // purely visual: never does anything on a dedicated server
        }
        TrajectoryConfig.load(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (event.getSide() != Side.CLIENT) {
            return;
        }
        toggleKey = new KeyBinding("Toggle Trajectories", Keyboard.KEY_J, "Trajectories");
        ClientRegistry.registerKeyBinding(toggleKey);

        MinecraftForge.EVENT_BUS.register(new TrajectoryRenderer());
        FMLCommonHandler.instance().bus().register(this);
        ClientCommandHandler.instance.registerCommand(new TrajectoryCommand());
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || toggleKey == null) {
            return;
        }
        while (toggleKey.isPressed()) {
            TrajectoryConfig.enabled = !TrajectoryConfig.enabled;
            TrajectoryConfig.save();
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                mc.thePlayer.addChatMessage(new ChatComponentText(
                        "\u00a78[\u00a7bTrajectories\u00a78] \u00a77"
                                + (TrajectoryConfig.enabled ? "\u00a7aenabled" : "\u00a7cdisabled")));
            }
        }
    }
}
