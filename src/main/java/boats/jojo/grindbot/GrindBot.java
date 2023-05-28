package boats.jojo.grindbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.commons.io.IOUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.lwjgl.input.Keyboard;

import java.util.concurrent.ForkJoinPool;
import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.StringUtils;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;

@Mod(
		modid = "keystrokesmod",
		name = "gb",
		version = "1.13",
		acceptedMinecraftVersions = "1.8.9"
)
public class GrindBot {
	public static final Logger LOGGER = LogManager.getLogger();
	
	Minecraft mcInstance = Minecraft.getMinecraft();

	float curFps = 0;
	
	double mouseTargetX, mouseTargetY, mouseTargetZ;
	
	boolean attackedThisTick = false;
	
	String curTargetName = "null";
	String[] nextTargetNames = null;
	
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
	
	List<String> chatMsgs = new ArrayList<>();
	String importantChatMsg = "";

	Map<Key, Double> keyUpChances = new HashMap<>();
	Map<Key, Double> keyDownChances = new HashMap<>();
	
	double keyAttackChance = 0;

	double mouseSpeed = 0;
	
	boolean autoClickerEnabled = false;
	long lastToggledAutoClicker = 0;

	boolean grinderEnabled = false;
	long lastToggledGrinder = 0;
	
	long preApiProcessingTime = 0;
	
	String apiMessage = "null";

	double mouseVelX, mouseVelY;
	long lastMouseUpdate;

	@EventHandler
	public void init(FMLInitializationEvent event) {
		MinecraftForge.EVENT_BUS.register(this);
		ClientCommandHandler.instance.registerCommand(new KeyCommand());
	}

	@EventHandler
	public void serverLoad(FMLServerStartingEvent event) {
		event.registerServerCommand(new KeyCommand());
	}

	@SubscribeEvent
	public void onKeyPress(InputEvent.KeyInputEvent event) {
		long curTime = System.currentTimeMillis();
		
		long toggledGrinderTimeDiff = curTime - lastToggledGrinder;
		
		if (toggledGrinderTimeDiff > 500 && org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_J)) {
			grinderEnabled = !grinderEnabled;
			
			if (grinderEnabled) { // newly enabled
				initialFov = mcInstance.gameSettings.fovSetting;
				chatMsgs.clear(); // reset list of chat messages to avoid picking up ones received earlier
			} else { // newly disabled
				Key.unpressAll();
				mcInstance.gameSettings.fovSetting = initialFov;
			}
			
			lastToggledGrinder = curTime;
		}
		
		long toggledAutoClickerTimeDiff = curTime - lastToggledAutoClicker;
		
		if (toggledAutoClickerTimeDiff > 500 && org.lwjgl.input.Keyboard.isKeyDown(Keyboard.KEY_K)) {
			autoClickerEnabled = !autoClickerEnabled;
			
			lastToggledAutoClicker = curTime;
		}

		Utils.setWindowTitle(); // putting this here is probably often enough idk can put it elsewhere later
	}
	
	@SubscribeEvent
	public void overlayFunc(RenderGameOverlayEvent.Post event) {
		long curTime = System.currentTimeMillis();
		try { // rendering GUI stuff, super low importance so errors don't matter
			if (event.type == ElementType.HEALTH || event.type == ElementType.ARMOR) { // these mess with the order or something
				return;
			}

			interpolateMousePosition();

			int screenWidth = event.resolution.getScaledWidth();
			int screenHeight = event.resolution.getScaledHeight();

			String[] infoToDraw = {
				"Username: " + mcInstance.thePlayer.getName(),
				"FPS: " + (int) curFps,
				"API time: " + apiLastTotalProcessingTime + "ms",
				"AutoClicker: " + (autoClickerEnabled ? "ENABLED" : "disabled"),
				"X: " + Math.round(mcInstance.thePlayer.posX * 10.0) / 10.0,
				"Y: " + Math.round(mcInstance.thePlayer.posY * 10.0) / 10.0,
				"Z: " + Math.round(mcInstance.thePlayer.posZ * 10.0) / 10.0,
				"API msg: " + apiMessage,
			};

			for(int i = 0; i < infoToDraw.length; i++) {
				drawText(infoToDraw[i], 4, 4 + i * 10, 0xFFFFFF);
			}

			int color = 0xFFFFFF;
			int keyboardPosX = screenWidth - 77;
			int keyboardPosY = screenHeight - 60;

			if (mcInstance.gameSettings.keyBindForward.isKeyDown()) { // W
				drawText("W", keyboardPosX + 41, keyboardPosY + 4, color);
			}

			if (mcInstance.gameSettings.keyBindBack.isKeyDown()) { // S
				drawText("S", keyboardPosX + 41, keyboardPosY + 22, color);
			}

			if (mcInstance.gameSettings.keyBindLeft.isKeyDown()) { // A
				drawText("A", keyboardPosX + 23, keyboardPosY + 22, color);
			}

			if (mcInstance.gameSettings.keyBindRight.isKeyDown()) { // D
				drawText("D", keyboardPosX + 59, keyboardPosY + 22, color);
			}

			if (mcInstance.gameSettings.keyBindSneak.isKeyDown()) { // Shift
				drawText("Sh", keyboardPosX + 2, keyboardPosY + 22, color);
			}

			if (mcInstance.gameSettings.keyBindSprint.isKeyDown()) { // Ctrl
				drawText("Ct", keyboardPosX + 3, keyboardPosY + 40, color);
			}

			if (mcInstance.gameSettings.keyBindJump.isKeyDown()) { // Space
				drawText("Space", keyboardPosX + 28, keyboardPosY + 40, color);
			}

			if (mcInstance.gameSettings.keyBindAttack.isKeyDown() || attackedThisTick) { // Mouse1
				drawText("LM", keyboardPosX + 2, keyboardPosY + 4, color);
			}

			if (mcInstance.gameSettings.keyBindUseItem.isKeyDown()) { // Mouse2
				drawText("RM", keyboardPosX + 20, keyboardPosY + 4, color);
			}
		} catch(Exception e){
			e.printStackTrace();
		}

		// get fps
		curFps = Minecraft.getDebugFPS();
					
		// bot tick handling
		long tickTimeDiff = curTime - lastTickTime;

		if (grinderEnabled && Utils.onHypixel()
			&& curTime - (lastReceivedApiResponse - apiLastTotalProcessingTime) >= 1000 // 1000ms per api call
			&& curTime - lastCalledApi >= 500 // absolute minimum time to avoid spamming before any responses received
		) {
			lastCalledApi = curTime;
			callBotApi();
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
	}
	
	@SubscribeEvent
	public void onChat(ClientChatReceivedEvent event) {
		String curChatRaw = StringUtils.stripControlCodes(event.message.getUnformattedText());

		// idk what the first thing is for `!curChatRaw.startsWith(":")`
		if (!curChatRaw.startsWith(":") && (curChatRaw.startsWith("MAJOR EVENT!") || curChatRaw.startsWith("BOUNTY CLAIMED!") || curChatRaw.startsWith("NIGHT QUEST!") || curChatRaw.startsWith("QUICK MATHS!") || curChatRaw.startsWith("DONE!") || curChatRaw.startsWith("MINOR EVENT!") || curChatRaw.startsWith("MYSTIC ITEM!") || curChatRaw.startsWith("PIT LEVEL UP!") || curChatRaw.startsWith("A player has"))) {
			importantChatMsg = curChatRaw;
		}

		if (grinderEnabled) {
			chatMsgs.add(curChatRaw);
		}
	}
	
	public void doBotTick() {
		try {
			// go afk if fps too low (usually when world is loading)

			if (curFps < minimumFps) {
				goAfk();
				apiMessage = "FPS too low!";
				return;
			}
			
			// main things
			
			long timeSinceReceivedApiResponse = System.currentTimeMillis() - lastReceivedApiResponse;
			
			if (timeSinceReceivedApiResponse > 3000) {
				Key.unpressAll();

				if (Math.floor(timeSinceReceivedApiResponse / 50) % 20 == 0) {
					goAfk();
					String issueStr = "Too long since successful api response: " + Math.min(999999, timeSinceReceivedApiResponse) + "ms. (last api ping: " + apiLastPing + "ms. last api time: " + apiLastTotalProcessingTime + " ms.)";
					apiMessage = issueStr;
					LOGGER.warn(issueStr);
				}

				return;
			}

			if (!curTargetName.equals("null")) {
				double[] curTargetPos = getPlayerPos(curTargetName);
				
				if (curTargetPos[1] > mcInstance.thePlayer.posY + 4 && nextTargetNames.length > 0) {
					LOGGER.debug("Switching to next target " + nextTargetNames[0] + " because target Y of " + curTargetPos[1] + " is too high");
					
					curTargetName = nextTargetNames[0];
					nextTargetNames = Arrays.copyOfRange(nextTargetNames, 1, nextTargetNames.length);
					
					curTargetPos = getPlayerPos(curTargetName);
				}
				
				mouseTargetX = curTargetPos[0];
				mouseTargetY = curTargetPos[1] + 1;
				mouseTargetZ = curTargetPos[2];
			}
			
			if (mcInstance.currentScreen == null) {
				if (mouseTargetX != 0 || mouseTargetY != 0 || mouseTargetZ != 0) { // dumb null check
					mouseMove();
				}
				Key.doMovement(this);
			} else {
				Key.unpressAll();
			}
			
			if (mcInstance.thePlayer.posY > curSpawnLevel - 4 && !curTargetName.equals("null") && !farFromMid()) {

				// in spawn but has target (bad)

				curTargetName = "null";
				
				mouseTargetX = 0;
				mouseTargetY = curSpawnLevel - 4;
				mouseTargetZ = 0;

				Key.unpressAll();

				if (!autoClickerEnabled) { // only needs to switch away from sword if an external KA is enabled
					mcInstance.thePlayer.inventory.currentItem = 5;
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public void reloadKey() {
		Path file = Paths.get("key.txt");

		if (!Files.isRegularFile(file)) {
			apiMessage = "No key file found!";
			return;
		}

		try {
			LOGGER.debug("Loaded key from file");

			GrinderAPI.setApiKey(String.join("\n", Files.readAllLines(file)));
		} catch (IOException exception) {
			LOGGER.error("Error reading key from file " + file + "\"!");
			exception.printStackTrace();

			apiMessage = "Error reading key from file!";
		}
	}
	
	public void callBotApi() {
		// set key from file if unset
		if (!GrinderAPI.hasApiKey()) {
			reloadKey();
		}

		// return if key is still null - no key was read so no point calling API
		if (!GrinderAPI.hasApiKey()) {
			return;
		}
		
		preApiProcessingTime = System.currentTimeMillis();
		
		// construct client info string
		String builtInfo = GrinderAPI.buildPayload(this);

		// Cleanup data that will be sent
		chatMsgs.clear();
		importantChatMsg = "";

		// Compress and add "this is compressed" tag to support API version compatibility
		String finalBuiltInfo = Utils.compressString(builtInfo) + "xyzcompressed";
		
		// done, set client info header
		LOGGER.debug("API info header length is " + finalBuiltInfo.length() + " characters!");
		
		// do request
		
		ticksPerApiCall = 20;
		
		long preApiGotTime = System.currentTimeMillis();

		LOGGER.debug("Sending payload to " + GrinderAPI.API_URL);

		ForkJoinPool.commonPool().execute(() -> {
			HttpGet get = new HttpGet(GrinderAPI.API_URL);

			int timeoutMs = 5000;
			RequestConfig requestConfig = RequestConfig.custom()
					.setConnectionRequestTimeout(timeoutMs)
					.setConnectTimeout(timeoutMs)
					.setSocketTimeout(timeoutMs)
					.build();

			get.setConfig(requestConfig);
			get.setHeader("clientinfo", finalBuiltInfo);

			String apiResponse;
			try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
				HttpResponse response = httpclient.execute(get);
				apiResponse = IOUtils.toString(response.getEntity().getContent());
			} catch (IOException e) {
				e.printStackTrace();
				apiMessage = "Fatal error while sending API payload!";
				return;
			}

			apiLastPing = (int)(System.currentTimeMillis() - preApiGotTime);

			LOGGER.debug("API ping was " + apiLastPing + "ms");

			if (apiLastPing > 1000) {
				LOGGER.warn("API ping too high!");
				apiMessage = "API ping too high (" + apiLastPing + "ms)!";
				return;
			}

			try {
				ingestApiResponse(apiResponse);
			} catch (Exception exception) {
				exception.printStackTrace();
				apiMessage = "Error while ingesting API response!";
			}
		});
	}
	
	public void ingestApiResponse(String apiText) {

		// check if the apiText starts with the compression flag

		if (!apiText.startsWith("xyzcompressed")) {
			String errorStr = "Invalid API response (was not indicated as compressed): " + apiText;
			LOGGER.error(errorStr);
			apiMessage = errorStr;
			return;
		}

		// remove the compression flag from the apiText before decompression
		apiText = apiText.substring("xyzcompressed".length());

		// now decompress
		apiText = Utils.decompressString(apiText);

		// deal with given instructions
		String[] apiStringSplit = apiText.split("##!##");
		
		if (!apiStringSplit[0].equals("null")) {
			nextTargetNames = apiStringSplit[0].split(":::");
			curTargetName = nextTargetNames[0];
			nextTargetNames = Arrays.copyOfRange(nextTargetNames, 1, nextTargetNames.length);
		} else {
			curTargetName = "null";
			nextTargetNames = null;
		}
		
		if (!apiStringSplit[1].equals("null")) {
			String chatToSend = apiStringSplit[1];
			if (!chatToSend.contains("/trade")) { // lol
				mcInstance.thePlayer.sendChatMessage(apiStringSplit[1]);
			}
		}
		
		if (!apiStringSplit[2].equals("null")) {
			mcInstance.thePlayer.inventory.currentItem = Integer.parseInt(apiStringSplit[2]);
		}
		
		if (!apiStringSplit[3].equals("null")) {
			String[] chances = apiStringSplit[3].split(":::");
			
			if (chances.length != 15) {
				LOGGER.error("Key chances array from server had an incorrect length!");
				apiMessage = "Key chances from API failed!";
				return;
			}

			keyDownChances.put(Key.FORWARD, Double.parseDouble(chances[0]));
			keyUpChances.put(Key.FORWARD, Double.parseDouble(chances[1]));

			keyDownChances.put(Key.SIDE, Double.parseDouble(chances[2]));
			keyUpChances.put(Key.SIDE, Double.parseDouble(chances[3]));

			keyDownChances.put(Key.BACKWARD, Double.parseDouble(chances[4]));
			keyUpChances.put(Key.BACKWARD, Double.parseDouble(chances[5]));

			keyDownChances.put(Key.JUMP, Double.parseDouble(chances[6]));
			keyUpChances.put(Key.JUMP, Double.parseDouble(chances[7]));

			keyDownChances.put(Key.CROUCH, Double.parseDouble(chances[8]));
			keyUpChances.put(Key.CROUCH, Double.parseDouble(chances[9]));

			keyDownChances.put(Key.SPRINT, Double.parseDouble(chances[10]));
			keyUpChances.put(Key.SPRINT, Double.parseDouble(chances[11]));

			keyDownChances.put(Key.USE, Double.parseDouble(chances[12]));
			keyUpChances.put(Key.USE, Double.parseDouble(chances[13]));
			
			keyAttackChance = Double.parseDouble(chances[14]);
		}
		
		mouseTargetX = 0;
		mouseTargetY = 0;
		mouseTargetZ = 0;
		if (!apiStringSplit[4].equals("null")) {
			String[] mouseTargetStringSplit = apiStringSplit[4].split(":::");
			
			mouseTargetX = Double.parseDouble(mouseTargetStringSplit[0]);
			mouseTargetY = Double.parseDouble(mouseTargetStringSplit[1]);
			mouseTargetZ = Double.parseDouble(mouseTargetStringSplit[2]);
		}
		
		if (!apiStringSplit[5].equals("null")) {
			Key.unpressAll();
			
			int containerItemToPress = Integer.parseInt(apiStringSplit[5]);

			LOGGER.debug("Pressing container slot " + containerItemToPress);
			
			mcInstance.playerController.windowClick(mcInstance.thePlayer.openContainer.windowId, containerItemToPress, 1, 2, mcInstance.thePlayer);
		}
		
		if (!apiStringSplit[6].equals("null")) {
			Key.unpressAll();
			
			int inventoryItemToDrop = Integer.parseInt(apiStringSplit[6]);

			LOGGER.debug("Dropping item at slot " + inventoryItemToDrop);
			
			mcInstance.playerController.windowClick(mcInstance.thePlayer.openContainer.windowId, inventoryItemToDrop, 1, 4, mcInstance.thePlayer);
		}
		
		if (!apiStringSplit[7].equals("null")) {
			Key.unpressAll();
			
			int inventoryItemToMove = Integer.parseInt(apiStringSplit[7]);

			LOGGER.debug("Moving inventory item at slot " + inventoryItemToMove);
			
			mcInstance.playerController.windowClick(mcInstance.thePlayer.openContainer.windowId, inventoryItemToMove, 1, 1, mcInstance.thePlayer);
		}
		
		if (!apiStringSplit[8].equals("null")) {
			ticksPerApiCall = Integer.parseInt(apiStringSplit[8]);
		}
		
		if (!apiStringSplit[9].equals("null")) {
			minimumFps = Integer.parseInt(apiStringSplit[9]);
		}
		
		if (!apiStringSplit[10].equals("null")) {
			fovWhenGrinding = Float.parseFloat(apiStringSplit[10]);
		}
		
		if (!apiStringSplit[11].equals("null")) {
			Key.unpressAll();
			
			if (apiStringSplit[11].equals("true")) {
				mcInstance.currentScreen = null;
			}
		}
		
		if (!apiStringSplit[12].equals("null")) {
			Key.unpressAll();
			
			if (apiStringSplit[12].equals("true")) {
				Key.pressInventoryKeyIfNoGuiOpen();
			}
		}
		
		if (!apiStringSplit[13].equals("null")) {
			apiMessage = apiStringSplit[13];
		}
		
		if (!apiStringSplit[14].equals("null")) {
			curSpawnLevel = Double.parseDouble(apiStringSplit[14]);
		}

		if (!apiStringSplit[15].equals("null")) {
			mouseSpeed = Double.parseDouble(apiStringSplit[15]);
		}

		if (!apiStringSplit[16].equals("null")) {
			if (apiStringSplit[16].equals("true")) {
				grinderEnabled = false;
				Key.unpressAll();
				Key.pressInventoryKeyIfNoGuiOpen();
			}
		}
		
		lastReceivedApiResponse = System.currentTimeMillis();
		apiLastTotalProcessingTime = (int) (System.currentTimeMillis() - preApiProcessingTime);

		LOGGER.debug("Total API ingestion time was " + apiLastTotalProcessingTime + "ms");
	}

	public void goAfk() {
		mouseVelX = 0;
		mouseVelY = 0;
		Key.unpressAll();
		Key.pressChatKeyIfNoGuiOpen();
	}
	
	public double[] getPlayerPos(String playerName) { // weird
		EntityPlayer player = mcInstance.theWorld.getPlayerEntityByName(playerName);
		if (player != null) {
			return new double[] {player.getPosition().getX(), player.getPosition().getY(), player.getPosition().getZ()};
		}  else {
			LOGGER.debug("Could not find a player of the name " + playerName);
			return new double[] {0, 999, 0};
		}
	}
	
	public void drawText(String text, float x, float y, int col) {
		mcInstance.fontRendererObj.drawStringWithShadow(text, x, y, col);
	}

	public void interpolateMousePosition() {
		if (!grinderEnabled || mcInstance.currentScreen != null) {
			lastMouseUpdate = 0;
			mouseVelX = 0;
			mouseVelY = 0;
			return;
		}

		long currentTime = System.currentTimeMillis();
		if (lastMouseUpdate != 0) {
			float timePassed = (currentTime - lastMouseUpdate) / 1000f;

			// Limit time passed (so that the change can't be that large)
			timePassed = Math.min(timePassed, 1);

			mcInstance.thePlayer.rotationYaw += mouseVelY * timePassed;
			mcInstance.thePlayer.rotationPitch += mouseVelX * timePassed;

			// Limit pitch
			float pitch = mcInstance.thePlayer.rotationPitch;
			mcInstance.thePlayer.rotationPitch = Math.min(Math.max(-90, pitch), 90);
		}
		lastMouseUpdate = currentTime;
	}

	public void mouseMove() {
		interpolateMousePosition();

		float currentYaw = mcInstance.thePlayer.rotationYaw, currentPitch = mcInstance.thePlayer.rotationPitch;
		double x = mcInstance.thePlayer.posX, y = mcInstance.thePlayer.posY, z = mcInstance.thePlayer.posZ;

		double headHeight = 1.62;

		// old af math probably stupid
		double targetRotY = Utils.fixRotY(360 - Math.toDegrees(Math.atan2(mouseTargetX - x, mouseTargetZ - z)));
		double targetRotX = -Math.toDegrees(Math.atan(
				(mouseTargetY - y - headHeight) / Math.hypot(mouseTargetX - x, mouseTargetZ - z)
		));
		
		// add random waviness to target
		targetRotY += timeSinWave(310) * 2 + timeSinWave(500) * 2 + timeSinWave(260) * 2;
		targetRotX += timeSinWave(290) * 2 + timeSinWave(490) * 2 + timeSinWave(270) * 2;
		
		targetRotY = Utils.fixRotY(targetRotY);
		targetRotX = Utils.fixRotX(targetRotX);
		
		// calculate mouse speed
		double timeNoise = timeSinWave(40)  * 2 +
				timeSinWave(50)  * 2 +
				timeSinWave(100) * 2 +
				timeSinWave(150) * 4 +
				timeSinWave(200) * 6;

		double mouseCurSpeed = mouseSpeed + timeNoise;

		currentYaw = (float) Utils.fixRotY(currentYaw);
		
		double diffRotX = targetRotX - currentPitch;
		double diffRotY = Utils.range180(targetRotY - currentYaw);
		
		double rotAng = Math.atan2(diffRotY, diffRotX) + Math.PI;
		
		double changeRotX = -Math.cos(rotAng) * mouseCurSpeed / 4;
		double changeRotY = -Math.sin(rotAng) * mouseCurSpeed;

		mouseVelX = Math.abs(diffRotX) < Math.abs(changeRotX)
				? targetRotX - currentPitch
				: changeRotX;
		mouseVelY = Math.abs(diffRotY) < Math.abs(changeRotY)
				? targetRotY - currentYaw
				: changeRotY;

		// Reach target in 1/20th of a second (1 tick)
		double TPS = 20.0;
		mouseVelX *= TPS;
		mouseVelY *= TPS;
	}
	
	public boolean farFromMid() {
		return mcInstance.thePlayer.posX > 32 || mcInstance.thePlayer.posZ > 32;
	}

	public double timeSinWave(double div) { // little odd
		double num = System.currentTimeMillis() / div * 100.0D;
		num %= 360.0D;
		num = Math.toRadians(num);
		num = Math.sin(num);
		return num;
	}

}
