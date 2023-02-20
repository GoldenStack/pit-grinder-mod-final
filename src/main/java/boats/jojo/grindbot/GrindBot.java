package boats.jojo.grindbot;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Mod(
        modid = "keystrokesmod",
        name = "gb",
        version = "1.10",
        acceptedMinecraftVersions = "1.8.9"
)
public class GrindBot {
    @EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        ClientCommandHandler.instance.registerCommand(new KeyCommand());
    }

    @EventHandler
    public void serverLoad(FMLServerStartingEvent event) {
        event.registerServerCommand(new KeyCommand());
    }

    private static final Logger LOGGER = LogManager.getLogger();

    Minecraft mcInstance = Minecraft.getMinecraft();


    Client webClient = ClientBuilder.newClient();
    WebTarget webTarget = webClient.target(JojoAPI.API_URL);


    int currentFPS = 0;

    BlockPos mouseTarget;

    boolean attackedThisTick = false;

    String curTargetName = null;
    List<String> nextTargetNames = null;

    int minimumFps = 0;

    int ticksPerApiCall = 200;

    float initialFov = 120;
    float fovWhenGrinding = 120;

    double curSpawnLevel = 999;

    long lastCalledApi = 0;
    long lastReceivedApiResponse = 0;

    int apiLastPing = 0;
    int apiLastTotalProcessingTime = 0;

    long lastTickTime = 0;

    String lastChatMsg = "";
    String importantChatMsg = "";

    Map<Key, Double> keyDownChances = new EnumMap<>(Key.class);
    Map<Key, Double> keyUpChances = new EnumMap<>(Key.class);

    double keyAttackChance = 0;

    double mouseSpeed = 0;

    boolean autoClickerEnabled = false;
    long lastToggledAutoClicker = 0;

    boolean grinderEnabled = false;
    long lastToggledGrinder = 0;

    long preApiProcessingTime = 0;

    String apiMessage = null;

    @SubscribeEvent
    public void onKeyPress(InputEvent.KeyInputEvent event) {
        long curTime = System.currentTimeMillis();

        long toggledGrinderTimeDiff = curTime - lastToggledGrinder;

        if (toggledGrinderTimeDiff > 500 && org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_J)) {
            grinderEnabled = !grinderEnabled;

            if (grinderEnabled) { // newly enabled
                initialFov = mcInstance.gameSettings.fovSetting;
            } else { // newly disabled
                Key.allKeysUp();
                mcInstance.gameSettings.fovSetting = initialFov;
            }

            lastToggledGrinder = curTime;
        }

        long toggledAutoClickerTimeDiff = curTime - lastToggledAutoClicker;

        if (toggledAutoClickerTimeDiff > 500 && org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_K)) {
            autoClickerEnabled = !autoClickerEnabled;
            lastToggledAutoClicker = curTime;
        }
    }

    @SubscribeEvent
    public void overlayFunc(RenderGameOverlayEvent.Post event) {
        if (event.type == ElementType.HEALTH || event.type == ElementType.ARMOR) {
            return;
        }

        int screenWidth = event.resolution.getScaledWidth();
        int screenHeight = event.resolution.getScaledHeight();

        String[][] infoToDraw = {
                {"Username", mcInstance.thePlayer.getName()},
                {"FPS", Integer.toString(currentFPS)},
                {"API time", apiLastTotalProcessingTime + "ms"},
                {"AutoClicker", autoClickerEnabled ? "ENABLED" : "disabled"},
                {"X", Double.toString(Math.round(mcInstance.thePlayer.posX * 10.0) / 10.0)},
                {"Y", Double.toString(Math.round(mcInstance.thePlayer.posY * 10.0) / 10.0)},
                {"Z", Double.toString(Math.round(mcInstance.thePlayer.posZ * 10.0) / 10.0)},
                {"API msg", apiMessage},
        };

        for (int i = 0; i < infoToDraw.length; i++) {
            String[] curInfo = infoToDraw[i];

            drawText(curInfo[0] + ": " + curInfo[1], 4, 4 + i * 10, 0xFFFFFF);
        }

        int drawKeyboardPositionX = screenWidth - 77;
        int drawKeyboardPositionY = screenHeight - 60;

        if (mcInstance.gameSettings.keyBindForward.isKeyDown()) { // W
            drawText("W", drawKeyboardPositionX + 41, drawKeyboardPositionY + 4, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindBack.isKeyDown()) { // S
            drawText("S", drawKeyboardPositionX + 41, drawKeyboardPositionY + 22, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindLeft.isKeyDown()) { // A
            drawText("A", drawKeyboardPositionX + 23, drawKeyboardPositionY + 22, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindRight.isKeyDown()) { // D
            drawText("D", drawKeyboardPositionX + 59, drawKeyboardPositionY + 22, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindSneak.isKeyDown()) { // Shift
            drawText("Sh", drawKeyboardPositionX + 2, drawKeyboardPositionY + 22, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindSprint.isKeyDown()) { // Ctrl
            drawText("Ct", drawKeyboardPositionX + 3, drawKeyboardPositionY + 40, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindJump.isKeyDown()) { // Space
            drawText("Space", drawKeyboardPositionX + 28, drawKeyboardPositionY + 40, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindAttack.isKeyDown() || attackedThisTick) { // Mouse1
            drawText("LM", drawKeyboardPositionX + 2, drawKeyboardPositionY + 4, 0xFFFFFF);
        }

        if (mcInstance.gameSettings.keyBindUseItem.isKeyDown()) { // Mouse2
            drawText("RM", drawKeyboardPositionX + 20, drawKeyboardPositionY + 4, 0xFFFFFF);
        }

        // bot controlling
        try {
            currentFPS = Minecraft.getDebugFPS();

            // bot tick handler

            long curTime = System.currentTimeMillis();

            long tickTimeDiff = curTime - lastTickTime;

            // 1000ms per API call
            boolean apiTimer = grinderEnabled && curTime - (lastReceivedApiResponse - apiLastTotalProcessingTime) >= 1000;
            // absolute minimum time to avoid spamming before any responses received
            boolean absMin = curTime - lastCalledApi >= 500;

            if (apiTimer && absMin) {
                lastCalledApi = curTime;
                JojoAPI.sendClientData(this);
            }

            if (tickTimeDiff < 1000 / 20) { // 20 ticks per second
                return;
            }

            // doing bot tick

            lastTickTime = curTime;
            attackedThisTick = false;

            if (grinderEnabled) {
                mcInstance.gameSettings.fovSetting = fovWhenGrinding;
                doBotTick();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    public void onChat(ClientChatReceivedEvent event) {
        String curChatRaw = StringUtils.stripControlCodes(event.message.getUnformattedText());

        curChatRaw = new String(curChatRaw.getBytes(), StandardCharsets.UTF_8); // probably unnecessary

        String[] importantMessages = {
                "MAJOR EVENT!",
                "BOUNTY CLAIMED!",
                "NIGHT QUEST!",
                "QUICK MATHS!",
                "DONE!",
                "MINOR EVENT!",
                "MYSTIC ITEM!",
                "PIT LEVEL UP!",
                "A player has"
        };

        // idk what the first thing is for
        if (!curChatRaw.startsWith(":")) {
            for (String message : importantMessages) {
                if (curChatRaw.startsWith(message)) {
                    importantChatMsg = curChatRaw;
                    break;
                }
            }
        }

        // logging chat messages

        if (curChatRaw.split(":").length <= 1) {
            return;
        }

        lastChatMsg = curChatRaw;
    }

    public void doBotTick() {
        try {
            // go afk if fps too low (usually when world is loading)

            if (currentFPS < minimumFps) {
                Key.keysUpAndOpenInventory(this);
                apiMessage = "fps too low";
                return;
            }

            // main things

            long timeSinceReceivedApiResponse = System.currentTimeMillis() - lastReceivedApiResponse;

            if (timeSinceReceivedApiResponse > 2000) {
                Key.allKeysUp();

                if (Math.floor(timeSinceReceivedApiResponse / 50) % 20 == 0) {
                    pressInventoryKeyIfNoGuiOpen();
                    apiMessage = "too long since successful api response: " + timeSinceReceivedApiResponse + "ms. last api ping: " + apiLastPing + "ms. last api time: " + apiLastTotalProcessingTime + " ms.";
                    System.out.println("too long since successful api response: " + timeSinceReceivedApiResponse + "ms. last api ping: " + apiLastPing + "ms. last api time: " + apiLastTotalProcessingTime + " ms.");
                }

                return;
            }

            if (curTargetName != null) {
                BlockPos curTargetPos = getPlayerPos(curTargetName);

                if (curTargetPos.getY() > mcInstance.thePlayer.posY + 4 && !nextTargetNames.isEmpty()) {
                    System.out.println("switching to next target " + nextTargetNames.get(0) + " because Y of " + curTargetPos.getY() + " too high");

                    curTargetName = nextTargetNames.remove(0);

                    curTargetPos = getPlayerPos(curTargetName);
                }

                mouseTarget = curTargetPos;
            }

            if (mcInstance.currentScreen == null) {
                if (mouseTarget != null) {
                    mouseMove();
                }
                Key.doMovementKeys(this);
            } else {
                Key.allKeysUp();
            }

            if (mcInstance.thePlayer.posY > curSpawnLevel - 4 && curTargetName != null) {
                // in spawn but has target (bad)

                curTargetName = null;

                mouseTarget = new BlockPos(0, curSpawnLevel - 4, 0);

                Key.allKeysUp();

                if (!autoClickerEnabled) { // only needs to switch away from sword if an external KA is enabled
                    mcInstance.thePlayer.inventory.currentItem = 5;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doAttack() {
        KeyBinding.onTick(mcInstance.gameSettings.keyBindAttack.getKeyCode());
        attackedThisTick = true;
    }

    public void pressInventoryKeyIfNoGuiOpen() {
        if (mcInstance.currentScreen == null) {
            KeyBinding.onTick(mcInstance.gameSettings.keyBindInventory.getKeyCode());
        }
    }

    public BlockPos getPlayerPos(String playerName) { // weird
        return mcInstance.theWorld.playerEntities.stream()
                .filter(pl -> pl.getName().equals(playerName))
                .map(Entity::getPosition)
                .findFirst()
                .orElseGet(() -> {
                    System.out.println("could not find player");
                    return new BlockPos(0, 999, 0);
                });
    }

    public void drawText(String text, float x, float y, int col) {
        mcInstance.fontRendererObj.drawStringWithShadow(text, x, y, 0xffffff);
    }

    public void mouseMove() {
        // old af math probably stupid
        double targetRotY = fixRotY(360 - Math.toDegrees(Math.atan2(mouseTarget.getX() - mcInstance.thePlayer.posX, mouseTarget.getZ() - mcInstance.thePlayer.posZ)));
        double flatDist = Math.sqrt((mouseTarget.getX() - mcInstance.thePlayer.posX) * (mouseTarget.getX() - mcInstance.thePlayer.posX) + (mouseTarget.getZ() - mcInstance.thePlayer.posZ) * (mouseTarget.getZ() - mcInstance.thePlayer.posZ));
        double targetRotX = -Math.toDegrees(Math.atan((mouseTarget.getY() - mcInstance.thePlayer.posY - 1.62) / flatDist));

        // add random waviness to target

        targetRotY += timeSinWave(310) * 2;
        targetRotY += timeSinWave(500) * 2;
        targetRotY += timeSinWave(260) * 2;

        targetRotX += timeSinWave(290) * 2;
        targetRotX += timeSinWave(490) * 2;
        targetRotX += timeSinWave(270) * 2;

        targetRotY = fixRotY(targetRotY);
        targetRotX = fixRotX(targetRotX);

        // calculate mouse speed

        double mouseCurSpeed = mouseSpeed;

        mouseCurSpeed += timeSinWave(40) * 2;
        mouseCurSpeed += timeSinWave(50) * 2;
        mouseCurSpeed += timeSinWave(100) * 2;
        mouseCurSpeed += timeSinWave(150) * 4;
        mouseCurSpeed += timeSinWave(200) * 6;

        mcInstance.thePlayer.rotationYaw = (float) fixRotY(mcInstance.thePlayer.rotationYaw);

        double diffRotX = targetRotX - mcInstance.thePlayer.rotationPitch;
        double diffRotY = targetRotY - fixRotY(mcInstance.thePlayer.rotationYaw);

        if (diffRotY > 180) {
            diffRotY -= 360;
        } else if (diffRotY < -180) {
            diffRotY += 360;
        }

        double rotAng = Math.toDegrees(Math.atan2(diffRotY, diffRotX)) + 180;

        double changeRotX = -Math.cos(Math.toRadians(rotAng)) * mouseCurSpeed / 4;
        double changeRotY = -Math.sin(Math.toRadians(rotAng)) * mouseCurSpeed;

        if (true) {
            if (Math.abs(diffRotX) < Math.abs(changeRotX)) {
                mcInstance.thePlayer.rotationPitch = (float) targetRotX;
            } else {
                mcInstance.thePlayer.rotationPitch += changeRotX;
            }

            if (Math.abs(diffRotY) < Math.abs(changeRotY)) {
                changeRotY = targetRotY - mcInstance.thePlayer.rotationYaw;
                mcInstance.thePlayer.rotationYaw = (float) targetRotY;
            } else {
                mcInstance.thePlayer.rotationYaw += changeRotY;
            }
        }
    }

    public double timeSinWave(double div) { // little odd
        double num = System.currentTimeMillis() / div * 100.0D;
        num %= 360.0D;
        num = Math.toRadians(num);
        num = Math.sin(num);
        return num;
    }

    public double fixRotY(double rotY) {
        rotY = rotY % 360;
        while (rotY < 0) {
            rotY = rotY + 360;
        }
        return rotY;
    }

    public double fixRotX(double rotX) {
        if (rotX > 90) {
            rotX = 90;
        }
        if (rotX < -90) {
            rotX = -90;
        }
        return rotX;
    }
}
