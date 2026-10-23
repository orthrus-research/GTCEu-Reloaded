package gregtech.api.items.toolitem;

/** A prospective tool action, independent of rendering and server-side permission checks. */
public enum ToolAction {
    ROTATE_FRONT,
    ROTATE_OUTPUT,
    TOGGLE_INPUT,
    ENABLE_WORKING,
    DISABLE_WORKING,
    MUFFLE,
    UNMUFFLE,
    REMOVE_COVER,
    COVER_SETTINGS
}
