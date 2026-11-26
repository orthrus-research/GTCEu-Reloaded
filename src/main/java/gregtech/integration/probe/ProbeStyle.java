package gregtech.integration.probe;

/**
 * What a run of probe text means rather than how it looks. The One Probe draws each style in the colour its
 * client configuration gives it; HWYLA draws it in the colour The One Probe uses by default.
 */
public enum ProbeStyle {
    /** Ordinary information, in the probe's normal text colour. */
    INFO,
    /** The entry among several that applies, such as the current machine mode. */
    INFO_IMPORTANT,
    /** An entry among several that does not apply. */
    LABEL,
    /** A state that needs nothing from the player. */
    OK,
    /** A state the player may want to act on. */
    WARNING
}
