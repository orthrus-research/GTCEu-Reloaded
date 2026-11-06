package gregtech.api.recipes;

/** Publication is a value assignment; user code has already completed during preparation. */
record PreparedRecipeRegistrationState<T>(RecipeRegistrationState<T> state, T previous, T next) {

    boolean isCurrent() {
        return state.value() == previous;
    }

    void publish() {
        state.publish(next);
    }
}
