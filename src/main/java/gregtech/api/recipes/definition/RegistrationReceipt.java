package gregtech.api.recipes.definition;

/** A successful owned-group replacement. Replacing with an empty group removes its recipes. */
public record RegistrationReceipt(String group, long generation, int recipes) {}
