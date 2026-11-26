package gregtech.api.util.input;

import org.lwjgl.input.Keyboard;

/**
 * Whether the player holds Shift or Ctrl, for tooltips and other text that shows more while one is held. These read
 * the client's keyboard, so call them only on the client.
 */
public final class ModifierKeys {

    private ModifierKeys() {}

    /** @return whether either Shift key is held */
    public static boolean isShiftDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
    }

    /** @return whether either Ctrl key is held */
    public static boolean isCtrlDown() {
        return Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL);
    }
}
