package boats.jojo.grindbot;

import com.google.common.collect.ImmutableList;
import com.google.gson.*;
import jakarta.ws.rs.client.InvocationCallback;
import jakarta.ws.rs.core.Response;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class JojoAPI {

	public static final String API_URL = "https://pit-grinder-logic-api-jlrw3.ondigitalocean.app/";
//    public static final String API_URL = "http://127.0.0.1:5000/"; // Testing URL

	public static final String API_VERSION = "1.0.0";

	// Sorted roughly from best to worst
	private static final ImmutableList<String> CHECKED_PATHS = ImmutableList.of("key.txt", "key.txt.txt", "key", "token.txt", "token.txt.txt", "token");

	private static String API_KEY = null;

	public static String apiKey() {
		return API_KEY;
	}

	public static void setApiKey(String apiKey) {
		API_KEY = apiKey;
	}

	public static boolean hasApiKey() {
		return API_KEY != null;
	}

	public static boolean reloadKey(GrindBot bot) {
		for (String check : CHECKED_PATHS) {
			Path path = Paths.get(check);
			if (!Files.isRegularFile(path)) {
				continue;
			}

			List<String> lines;
			try {
				lines = Files.readAllLines(path);
			} catch (IOException exception){
				System.out.println("Error reading key from path " + path);
				bot.apiMessage = "error reading key";
				exception.printStackTrace();
				continue;
			}

			if (lines.size() == 1) {
				String key = lines.get(0);
				JojoAPI.setApiKey(key);
				System.out.println("set key: " + key);
				return true;
			}
		}

		bot.apiMessage = "no key file found";
		return false;
	}

	public static JsonElement buildClientData(GrindBot bot) {
		EntityPlayerSP player = bot.mcInstance.thePlayer;

		JsonObject data = new JsonObject();

		data.addProperty("grinder_auth", JojoAPI.apiKey());
		data.addProperty("grinder_api_version", API_VERSION);

		data.addProperty("client_username", player.getName());
		data.addProperty("health", player.getHealth());
		data.addProperty("experience_level", player.experienceLevel);

		data.addProperty("last_chat_message", bot.lastChatMsg);
		data.addProperty("important_chat_message", bot.importantChatMsg);

		UUID uuid = player.getGameProfile().getId();
		data.addProperty("client_uuid", uuid == null ? null : uuid.toString());

		GuiScreen screen = bot.mcInstance.currentScreen;
		data.addProperty("current_gui", screen == null ? null : screen.getClass().toString());

		bot.importantChatMsg = null;

		// Position
		{
			JsonObject position = new JsonObject();

			position.addProperty("x", player.posX);
			position.addProperty("y", player.posY);
			position.addProperty("z", player.posZ);

			position.addProperty("pitch", player.rotationPitch);
			position.addProperty("yaw", player.rotationYaw);

			data.add("position", position);
		}

		// Inventory
		{
			JsonArray items = new JsonArray();

			for (ItemStack item : player.inventoryContainer.getInventory()) {
				if (item != null) {
					JsonObject itemObject = new JsonObject();
					itemObject.addProperty("material", item.getItem().getRegistryName().split(":")[1]);
					itemObject.addProperty("count", item.stackSize);
					items.add(itemObject);
				} else {
					items.add(JsonNull.INSTANCE);
				}
			}

			data.add("inventory", items);
		}

		// Lobby
		{
			List<EntityPlayer> realPlayers = bot.mcInstance.theWorld.playerEntities.stream()
					.filter(p -> !p.isInvisible())
					.limit(128)
					.collect(Collectors.toList());

			JsonArray players = new JsonArray();

			for (EntityPlayer realPlayer : realPlayers) {
				JsonObject playerObject = new JsonObject();

				playerObject.addProperty("username", realPlayer.getName());
				playerObject.addProperty("x", realPlayer.posX);
				playerObject.addProperty("y", realPlayer.posY);
				playerObject.addProperty("z", realPlayer.posZ);
				playerObject.addProperty("health", realPlayer.getHealth());
				playerObject.addProperty("armor", realPlayer.getTotalArmorValue());

				players.add(playerObject);
			}

			data.add("players", players);
		}

		// Middle block
		{
			BlockPos pos = new BlockPos(0, (int) bot.mcInstance.thePlayer.posY - 1, 0);
			String middleBlockname = null;
			try {
				middleBlockname = bot.mcInstance.theWorld.getBlockState(pos).getBlock().getRegistryName().split(":")[1];
			} catch (Exception e) {
				e.printStackTrace();
			}

			data.addProperty("middle_block", middleBlockname);
		}

		// Container items
		{
			List<ItemStack> containerItems = player.openContainer.getInventory();

			if (containerItems.size() > 46) { // check if a container is open (definitely a better way to do that)
				JsonArray container = new JsonArray();

				for (int i = 0; i < containerItems.size() - 36; i++) { // minus 36 to cut off inventory
					ItemStack curItem = containerItems.get(i);

					if (curItem != null) {
						JsonObject itemObject = new JsonObject();
						itemObject.addProperty("material", curItem.getItem().getRegistryName().split(":")[1]);
						itemObject.addProperty("count", curItem.stackSize);
						itemObject.addProperty("display_name", curItem.getDisplayName());
						container.add(itemObject);
					} else {
						container.add(JsonNull.INSTANCE);
					}
				}

				data.add("container", container);
			}

		}

		// Dropped items
		{
			AxisAlignedBB box = new AxisAlignedBB(
					new BlockPos(player.posX - 32, player.posY - 4, player.posZ - 32),
					new BlockPos(player.posX + 32, player.posY + 32, player.posZ + 32)
			);

			List<EntityItem> itemEntities = bot.mcInstance.theWorld
					.getEntitiesWithinAABB(EntityItem.class, box)
					.stream()
					.limit(128)
					.collect(Collectors.toList());

			JsonArray droppedItems = new JsonArray();

			for (EntityItem item : itemEntities) {
				JsonObject itemObject = new JsonObject();
				itemObject.addProperty("x", item.posX);
				itemObject.addProperty("y", item.posY);
				itemObject.addProperty("z", item.posZ);
				itemObject.addProperty("name", item.getEntityItem().getItem().getRegistryName().split(":")[1]);
			}

			data.add("dropped_items", droppedItems);
		}

		// villager positions
		{
			JsonArray villagerPositions = new JsonArray();

			List<Entity> villagerEntities = bot.mcInstance.theWorld.getLoadedEntityList()
					.stream()
					.filter(entity -> entity.getClass().equals(EntityVillager.class))
					.limit(8)
					.collect(Collectors.toList());

			for (Entity villager : villagerEntities) {
				JsonObject position = new JsonObject();

				position.addProperty("x", villager.posX);
				position.addProperty("y", villager.posY);
				position.addProperty("z", villager.posZ);

				villagerPositions.add(position);
			}

			data.add("villager_positions", villagerPositions);
		}

		return data;
	}

	public static void sendClientData(GrindBot bot) {
		// Exit if there isn't a key and we can't read one
		if (!JojoAPI.hasApiKey() && !JojoAPI.reloadKey(bot)) {
			return;
		}

		System.out.println("Retrieving API URL: " + JojoAPI.API_URL);
		bot.preApiProcessingTime = System.currentTimeMillis();

		JsonElement data = buildClientData(bot);

		String json = new Gson().toJson(data);

		System.out.println("API header info has a length of " + json.length() + " characters");

		bot.ticksPerApiCall = 20;

		long preApiGotTime = System.currentTimeMillis();

		bot.webTarget.request().header("clientinfo", json).async().get(
				new InvocationCallback<Response>() {
					@Override
					public void completed(Response apiResponse) {
						try {
							bot.apiLastPing = (int) (System.currentTimeMillis() - preApiGotTime);

							System.out.println("api ping was " + bot.apiLastPing + "ms");

							if (bot.apiLastPing > 1000) {
								System.out.println("api ping too high");
								bot.apiMessage = "api ping too high - " + bot.apiLastPing + "ms";
								return;
							}

							String apiText = apiResponse.readEntity(String.class);

							JsonObject parsedElement = new Gson().fromJson(apiText, JsonObject.class);

							handleServerResponse(bot, parsedElement);
						} catch (JsonSyntaxException e) {
							System.out.println("api response error");
							bot.apiMessage = "api response failure";
							Key.keysUpAndOpenInventory(bot);
						} catch (Exception e) {
							e.printStackTrace();
							bot.apiMessage = "errored on ingesting api response";
						}
					}

					@Override
					public void failed(Throwable throwable) {
						throwable.printStackTrace();
						bot.apiMessage = "api call fatal error";
					}
				}
		);
	}

	public static void handleServerResponse(GrindBot bot, JsonObject apiResponse) {

		EntityPlayerSP player = bot.mcInstance.thePlayer;

		if (apiResponse.has("targets")) {
			List<String> names = new ArrayList<>();
			for (JsonElement target : apiResponse.getAsJsonArray("targets")) {
				names.add(target.getAsString());
			}
			bot.curTargetName = names.remove(0);
			bot.nextTargetNames = names;
		} else {
			bot.curTargetName = null;
			bot.nextTargetNames = new ArrayList<>();
		}

		if (apiResponse.has("command")) {
			String command = apiResponse.get("command").getAsString();
			if (!command.contains("/trade")) { // lol
				player.sendChatMessage(command);
			}
		}

		if (apiResponse.has("held_item")) {
			player.inventory.currentItem = apiResponse.get("held_item").getAsInt();
		}

		if (apiResponse.has("key_chances")) {
			JsonObject keyChances = apiResponse.getAsJsonObject("key_chances");
			for (Key value : Key.values()) {
				bot.keyDownChances.put(value, keyChances.get(value.formattedName() + "_down").getAsDouble());
				bot.keyUpChances.put(value, keyChances.get(value.formattedName() + "_up").getAsDouble());
			}
		}

		if (apiResponse.has("mouse_target")) {
			JsonObject object = apiResponse.getAsJsonObject("mouse_target");
			bot.mouseTarget = new BlockPos(
					object.get("x").getAsDouble(),
					object.get("y").getAsDouble(),
					object.get("z").getAsDouble()
			);
		} else {
			bot.mouseTarget = null;
		}

		if (apiResponse.has("press_container_item")) {
			Key.allKeysUp();
			int containerItemToPress = apiResponse.get("press_container_item").getAsInt();
			System.out.println("Pressing container item " + containerItemToPress);

			bot.mcInstance.playerController.windowClick(bot.mcInstance.thePlayer.openContainer.windowId, containerItemToPress, 1, 2, bot.mcInstance.thePlayer);
		}

		if (apiResponse.has("drop_inventory_item")) {
			Key.allKeysUp();
			int inventoryItemToDrop = apiResponse.get("drop_inventory_item").getAsInt();
			System.out.println("Dropping inventory item " + inventoryItemToDrop);

			bot.mcInstance.playerController.windowClick(bot.mcInstance.thePlayer.openContainer.windowId, inventoryItemToDrop, 1, 4, bot.mcInstance.thePlayer);
		}

		if (apiResponse.has("move_inventory_item")) {
			Key.allKeysUp();
			int inventoryItemToMove = apiResponse.get("move_inventory_item").getAsInt();
			System.out.println("Moving inventory item " + inventoryItemToMove);

			bot.mcInstance.playerController.windowClick(bot.mcInstance.thePlayer.openContainer.windowId, inventoryItemToMove, 1, 1, bot.mcInstance.thePlayer);
		}

		if (apiResponse.has("api_call_ticks")) {
			bot.ticksPerApiCall = apiResponse.get("api_call_ticks").getAsInt();
		}

		if (apiResponse.has("minimum_fps")) {
			bot.minimumFps = apiResponse.get("minimum_fps").getAsInt();
		}

		if (apiResponse.has("fov_when_grinding")) {
			bot.fovWhenGrinding = apiResponse.get("fov_when_grinding").getAsInt();
		}

		if (apiResponse.has("inventory_status")) {
			boolean open = apiResponse.get("inventory_status").getAsBoolean();
			Key.allKeysUp();
			if (open) {
				bot.pressInventoryKeyIfNoGuiOpen();
			} else {
				bot.mcInstance.currentScreen = null;
			}
		}

		if (apiResponse.has("api_message")) {
			bot.apiMessage = apiResponse.get("api_message").getAsString();
		}

		if (apiResponse.has("spawn_level")) {
			bot.curSpawnLevel = apiResponse.get("spawn_level").getAsDouble();
		}

		if (apiResponse.has("mouse_speed")) {
			bot.mouseSpeed = apiResponse.get("mouse_speed").getAsDouble();
		}

		bot.lastReceivedApiResponse = System.currentTimeMillis();
		bot.apiLastTotalProcessingTime = (int) (System.currentTimeMillis() - bot.preApiProcessingTime);

		System.out.println("total processing time was " + bot.apiLastTotalProcessingTime + "ms");
	}

}
