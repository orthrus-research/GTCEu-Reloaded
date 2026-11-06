package gregtech.api.recipes;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * A map-owned immutable value, published alongside its native recipe indexes. The preparation function
 * must return a detached value and must not mutate its inputs, registries or other external state.
 * It runs only during registration, never during recipe matching. Values exposed by {@link #value()}
 * must be immutable. Declaration and explicit updates require quiescent registration on the owning thread.
 */
public final class RecipeRegistrationState<T> {

    private final RecipeMap<?> owner;
    private final BiFunction<T, RecipeRegistrationChange, T> prepare;
    private T value;

    RecipeRegistrationState(RecipeMap<?> owner, T initial, BiFunction<T, RecipeRegistrationChange, T> prepare) {
        this.owner = owner;
        this.value = Objects.requireNonNull(initial, "initial state");
        this.prepare = Objects.requireNonNull(prepare, "preparation function");
    }

    public T value() {
        return value;
    }

    /** Explicit secondary-index edits invalidate any prepared recipe transaction. */
    public void update(UnaryOperator<T> update) {
        T next = Objects.requireNonNull(update.apply(value), "updated state");
        value = next;
        owner.onRecipesChanged();
    }

    PreparedRecipeRegistrationState<T> prepare(RecipeRegistrationChange change) {
        T previous = value;
        T next = Objects.requireNonNull(prepare.apply(previous, change), "prepared state");
        return new PreparedRecipeRegistrationState<>(this, previous, next);
    }

    void publish(T next) {
        value = next;
    }
}
