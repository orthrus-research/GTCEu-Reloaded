package gregtech.api.capability;

import java.util.List;

/** Server-thread, loaded-source views. Each descriptor owns one recipe candidate, not copied material. */
public interface IRecipeInputViewProvider {

    /** Pure enumeration. Private views already contain every source they are allowed to consume. */
    List<RecipeInputView> getRecipeInputViews();

    /** Changes with content, availability or topology; existing input notifications must also wake the controller. */
    long getRecipeInputRevision();

    /** Pure current membership/generation/attachment check, including immediately before actual input consumption. */
    boolean isRecipeInputViewValid(RecipeInputView view);

    /** Called after successful consume/setup. May retire an emptied batch, never remove it during matching. */
    default void onRecipeInputViewStarted(RecipeInputView view) {}
}
