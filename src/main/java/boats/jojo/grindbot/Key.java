package boats.jojo.grindbot;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public enum Key {
	FORWARD("forward", s -> s.keyBindForward),
	SIDE("side", s -> s.keyBindLeft, s -> s.keyBindRight),
	BACKWARD("backward", s -> s.keyBindBack),
	JUMP("jump", s -> s.keyBindJump),
	CROUCH("crouch", s -> s.keyBindSneak),
	SPRINT("sprint", s -> s.keyBindSprint),
//    ATTACK("attack", s -> s.keyBindAttack),
	USE("use", s -> s.keyBindUseItem);

	private final String name;
	private final List<Function<GameSettings, KeyBinding>> binds;

	@SafeVarargs
	Key(String name, Function<GameSettings, KeyBinding>... binds) {
		this.name = name;
		this.binds = ImmutableList.copyOf(binds);
	}

	public String formattedName() {
		return name;
	}

	public void set(boolean pressed) {
		GameSettings settings = Minecraft.getMinecraft().gameSettings;

		for (Function<GameSettings, KeyBinding> bind : binds) {
			KeyBinding.setKeyBindState(bind.apply(settings).getKeyCode(), pressed);
		}
	}

	public static void allKeysUp() {
		Arrays.stream(Key.values()).forEach(key -> key.set(false));
	}

	public static void doMovementKeys(GrindBot bot) {
		GameSettings settings = Minecraft.getMinecraft().gameSettings;

		for (Key key : Key.values()) {
			for (Function<GameSettings, KeyBinding> bind : key.binds) {
				if (Math.random() <= bot.keyUpChances.get(key)) {
					KeyBinding.setKeyBindState(bind.apply(settings).getKeyCode(), false);
				}
				if (Math.random() <= bot.keyUpChances.get(key)) {
					KeyBinding.setKeyBindState(bind.apply(settings).getKeyCode(), true);
				}
			}
		}

		if (bot.autoClickerEnabled && Math.random() < bot.keyAttackChance) {
			bot.doAttack();
		}
	}

	public static void keysUpAndOpenInventory(GrindBot bot) {
		allKeysUp();
		bot.pressInventoryKeyIfNoGuiOpen();
	}


}