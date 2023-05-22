package boats.jojo.grindbot;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public enum Key {
    FORWARD(s -> s.keyBindForward),
    SIDE(s -> s.keyBindLeft, s -> s.keyBindRight),
    BACKWARD(s -> s.keyBindBack),
    JUMP(s -> s.keyBindJump),
    CROUCH(s -> s.keyBindSneak),
    SPRINT(s -> s.keyBindSprint),
    //    ATTACK(s -> s.keyBindAttack),
    USE(s -> s.keyBindUseItem);

    private final List<Function<GameSettings, KeyBinding>> binds;

    @SafeVarargs
    Key(Function<GameSettings, KeyBinding>... binds) {
        this.binds = ImmutableList.copyOf(binds);
    }

    public void set(boolean pressed) {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;

        for (Function<GameSettings, KeyBinding> bind : binds) {
            KeyBinding.setKeyBindState(bind.apply(settings).getKeyCode(), pressed);
        }
    }

    public static void unpressAll() {
        Arrays.stream(Key.values()).forEach(key -> key.set(false));
    }

    public static void doMovement(GrindBot bot) {
        GameSettings settings = Minecraft.getMinecraft().gameSettings;

        for (Key key : Key.values()) {
            for (Function<GameSettings, KeyBinding> bind : key.binds) {
                if (Math.random() <= bot.keyUpChances.get(key)) {
                    KeyBinding.setKeyBindState(bind.apply(settings).getKeyCode(), false);
                }
                if (Math.random() <= bot.keyDownChances.get(key)) {
                    KeyBinding.setKeyBindState(bind.apply(settings).getKeyCode(), true);
                }
            }
        }

        if (bot.autoClickerEnabled && Math.random() < bot.keyAttackChance) {
            KeyBinding.onTick(Minecraft.getMinecraft().gameSettings.keyBindAttack.getKeyCode());
            bot.attackedThisTick = true;
        }
    }

    public static void pressInventoryKeyIfNoGuiOpen() {
        if (Minecraft.getMinecraft().currentScreen == null) {
            KeyBinding.onTick(Minecraft.getMinecraft().gameSettings.keyBindInventory.getKeyCode());
        }
    }

    public static void pressChatKeyIfNoGuiOpen() {
        if (Minecraft.getMinecraft().currentScreen == null) {
            KeyBinding.onTick(Minecraft.getMinecraft().gameSettings.keyBindChat.getKeyCode());
        }
    }

}