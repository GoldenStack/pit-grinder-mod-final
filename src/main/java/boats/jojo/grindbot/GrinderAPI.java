package boats.jojo.grindbot;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.passive.EntityVillager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.ModContainer;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class GrinderAPI {

    public static final String API_URL = "https://pit-grinder-logic-api-jlrw3.ondigitalocean.app/api/grinder";
//    public static final String API_URL = "http://127.0.0.1:5000/api/grinder"; // Testing URL

    private static String API_KEY = null;

    public static boolean hasApiKey() {
        return API_KEY != null;
    }

    public static String apiKey() {
        return API_KEY;
    }

    public static void setApiKey(@NotNull String apiKey) {
        API_KEY = apiKey;
    }

    public static void reloadKey(@NotNull GrindBot bot) {
        Path file = Paths.get("key.txt");

        if (!Files.isRegularFile(file)) {
            bot.setApiMessage("No key file found!");
            return;
        }

        try {
            GrindBot.LOGGER.debug("Loaded key from file");

            GrinderAPI.setApiKey(String.join("\n", Files.readAllLines(file)));
        } catch (IOException exception) {
            GrindBot.LOGGER.error("Error reading key from file " + file + "\"!", exception);

            bot.setApiMessage("Error reading key from file!");
        }
    }

    public static @NotNull String buildPayload(@NotNull GrindBot bot) {
        if (!hasApiKey()) {
            throw new IllegalStateException("Payload must be built with an API key!");
        }

        Minecraft mcInstance = bot.mcInstance;
        EntityPlayerSP player = mcInstance.thePlayer;

        String dataSeparator = "##!##";
        StringJoiner info = new StringJoiner(dataSeparator);

        info.add(GrinderAPI.apiKey()); // Auth key
        info.add(player.getName()); // Client username
        info.add(player.getGameProfile().getId().toString()); // Client UUID - will be null when in offline mode, but Hypixel is online

        // Client position and rotation
        info.add(join(":::", player.posX, player.posY, player.posZ, player.rotationPitch, player.rotationYaw));

        { // Client inventory
            StringJoiner invStr = new StringJoiner("!!!");

            for (ItemStack item : player.inventoryContainer.getInventory()) {
                if (item == null) {
                    invStr.add(join(":::", "air", 0));
                } else {
                    invStr.add(join(":::", item.getItem().delegate.getResourceName().getResourcePath(), item.stackSize));
                }
            }

            info.add(invStr.toString());
        }

        { // Players
            List<EntityPlayer> playerList = mcInstance.theWorld.playerEntities.stream()
                    .filter(user -> !user.isInvisible())
                    .limit(128)
                    .collect(Collectors.toList());

            StringJoiner playersStr = new StringJoiner("!!!");

            for (EntityPlayer entityPlayer : playerList) {
                BlockPos block = entityPlayer.getPosition();

                playersStr.add(join(":::", entityPlayer.getName(), block.getX(), block.getY(), block.getZ(), entityPlayer.getHealth(), entityPlayer.getTotalArmorValue()));
            }

            info.add(playersStr.toString());
        }

        { // Middle block
            Block middleBlock = mcInstance.theWorld.getBlockState(new BlockPos(0, (int) mcInstance.thePlayer.posY - 1, 0)).getBlock();
            String blockName = middleBlock.delegate.getResourceName().getResourcePath();

            info.add(blockName);
        }

        { // Last chat message
            String chatMsgSeparator = "!#!";

            StringJoiner chatMessages = new StringJoiner(chatMsgSeparator);

            for (int i = 0; i < Math.min(32, bot.chatMsgs.size()); i++) {
                chatMessages.add(bot.chatMsgs.get(i).replaceAll(dataSeparator, "").replaceAll(chatMsgSeparator, ""));
            }

            info.add(chatMessages.toString());
        }

        { // Container items
            List<ItemStack> containerItems = mcInstance.thePlayer.openContainer.getInventory();

            if (containerItems.size() <= 46) { // exit if a container is open (definitely a better way to do that)
                info.add("null");
            } else {
                StringJoiner items = new StringJoiner("!!!");

                for (int i = 0; i < containerItems.size() - 36; i++) { // minus 36 to cut off inventory
                    ItemStack item = containerItems.get(i);

                    if (item == null) {
                        items.add(join(":::", "air", "air", 0));
                    } else {
                        items.add(join(":::", item.getItem().delegate.getResourceName().getResourcePath(), item.stackSize, item.getDisplayName()));
                    }
                }

                info.add(items.toString());
            }
        }

        { // Dropped items
            StringJoiner droppedItemsStr = new StringJoiner("!!!");

            AxisAlignedBB box = new AxisAlignedBB(player.getPosition(), player.getPosition()).expand(32, 32, 32);
            List<EntityItem> droppedItems = mcInstance.theWorld.getEntitiesWithinAABB(EntityItem.class, box);

            for (int i = 0; i < Math.min(128, droppedItems.size()); i++) {
                EntityItem curItem = droppedItems.get(i);

                String curItemName = curItem.getEntityItem().getItem().delegate.getResourceName().getResourcePath();

                BlockPos itemPos = curItem.getPosition();

                droppedItemsStr.add(join(":::", curItemName, itemPos.getX(), itemPos.getY(), itemPos.getZ()));
            }

            info.add(droppedItemsStr.toString());
        }

        // Important chat message
        info.add(bot.importantChatMsg.equals("") ? "null" : bot.importantChatMsg);

        // Current open GUI
        info.add(mcInstance.currentScreen == null ? "null" : mcInstance.currentScreen.getClass().toString());

        { // Villager positions
            StringJoiner villagersStr = new StringJoiner("!!!");

            List<Entity> villagerEntities = mcInstance.theWorld.getLoadedEntityList()
                    .stream().filter(entity -> entity.getClass().equals(EntityVillager.class)).collect(Collectors.toList());

            for (int i = 0; i < Math.min(8, villagerEntities.size()); i++) {
                BlockPos villagerPos = villagerEntities.get(i).getPosition();

                villagersStr.add(join(":::", villagerPos.getX(), villagerPos.getY(), villagerPos.getZ()));
            }

            info.add(villagersStr.toString());
        }

        info.add(String.valueOf(player.getHealth())); // Client health

        info.add(String.valueOf(player.experienceLevel)); // Client XP level

        // Mod version
        ModContainer modContainer = Loader.instance().getIndexedModList().get("keystrokesmod");
        info.add(modContainer == null ? null : modContainer.getVersion());

        { // Blocks proximity player
            StringBuilder proximityBlocksStr = new StringBuilder();

            int proximityBlocksHorizontalRange = 8;
            int proximityBlocksVerticalRange = 2;
            int playerPosX = (int) player.posX;
            int playerPosY = (int) player.posY;
            int playerPosZ = (int) player.posZ;

            for (int x = playerPosX - proximityBlocksHorizontalRange; x <= playerPosX + proximityBlocksHorizontalRange; x++) {
                for (int y = playerPosY - proximityBlocksVerticalRange; y <= playerPosY + proximityBlocksVerticalRange; y++) {
                    for (int z = playerPosZ - proximityBlocksHorizontalRange; z <= playerPosZ + proximityBlocksHorizontalRange; z++) {

                        Map<String, Character> blockChars = new HashMap<>();
                        blockChars.put("minecraft:air", '0');
                        blockChars.put("minecraft:obsidian", 'o');
                        blockChars.put("minecraft:glass", 'g');
                        blockChars.put("minecraft:slime", 's');
                        blockChars.put("minecraft:enchanting_table", 'e');
                        blockChars.put("minecraft:ender_chest", 'c');
                        blockChars.put("minecraft:sea_lantern", 'l');
                        blockChars.put("minecraft:carpet", 'r');
                        blockChars.put("char '.' is reserved for other blocks or error, do not use", '.');

                        char blockChar;

                        try { // i am scared of it not working with some blocks
                            String blockName = mcInstance.theWorld.getBlockState(new BlockPos(x, y, z)).getBlock().getRegistryName();
                            blockChar = blockChars.getOrDefault(blockName, '.');
                        } catch (Exception e) {
                            e.printStackTrace();
                            proximityBlocksStr.append('.');
                            continue;
                        }

                        proximityBlocksStr.append(blockChar);
                    }
                }
            }

            info.add(proximityBlocksStr.toString());
        }

        info.add(String.valueOf(bot.apiLastPing)); // Ping

        // Build string and replace newlines because they apparently mess with the header
        return info.toString().replaceAll("\n", " ");
    }


    public static String join(@NotNull CharSequence delimiter, @NotNull Object @NotNull ... objects) {
        return String.join(delimiter, Arrays.stream(objects).map(Object::toString).toArray(CharSequence[]::new));
    }

    public static String join(@NotNull CharSequence delimiter, @NotNull Collection<@NotNull Object> objects) {
        return String.join(delimiter, objects.stream().map(Object::toString).toArray(CharSequence[]::new));
    }

}
