package gregtech.api.metatileentity.multiblock;

import net.minecraftforge.items.ItemStackHandler;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class AbilityOwnershipTest {

    @Test
    void registrationRunsOnceAndPreservesOrderedAcceptedInstances() {
        ItemStackHandler first = new ItemStackHandler(1), second = new ItemStackHandler(1);
        AbilityInstances instances = new AbilityInstances(MultiblockAbility.IMPORT_ITEMS);
        TestPart owner = new TestPart(list -> {
            list.add(Arrays.asList(first, second));
            list.add("wrong ability type");
            list.add(first);
        });
        instances.registerFrom(owner);
        assertEquals(1, owner.calls);
        assertEquals(Arrays.asList(first, second), instances);
        assertSame(owner, instances.getOwner(first));
        assertSame(owner, instances.getOwner(second));
        ItemStackHandler unowned = new ItemStackHandler(1);
        instances.add(unowned);
        assertNull(instances.getOwner(unowned), "A later unscoped addition must never inherit the prior owner");
    }

    @Test
    void duplicateClaimsFailClosedWithoutChangingLegacyDedupOrReplacementSemantics() {
        ItemStackHandler shared = new ItemStackHandler(1), second = new ItemStackHandler(1);
        AbilityInstances instances = new AbilityInstances(MultiblockAbility.IMPORT_ITEMS);
        TestPart first = new TestPart(list -> list.add(shared));
        TestPart claimant = new TestPart(list -> {
            list.add(shared);
            list.add(second);
        });
        instances.registerFrom(first);
        instances.registerFrom(claimant);
        assertEquals(2, instances.size());
        assertNull(instances.getOwner(shared));
        assertSame(claimant, instances.getOwner(second));
        TestPart replacer = new TestPart(list -> list.set(0, second));
        instances.registerFrom(replacer);
        assertSame(shared, instances.get(0), "Setting a duplicate remains rejected");
        assertNull(instances.getOwner(second), "A duplicate replacement claim is also ambiguous");
    }

    private static final class TestPart implements IMultiblockAbilityPart<Object> {

        private final Consumer<AbilityInstances> registration;
        int calls;

        TestPart(Consumer<AbilityInstances> registration) {
            this.registration = registration;
        }

        @Override
        public void registerAbilities(AbilityInstances instances) {
            calls++;
            registration.accept(instances);
        }

        @Override
        public boolean isAttachedToMultiBlock() {
            return false;
        }

        @Override
        public void addToMultiBlock(MultiblockControllerBase controller) {}

        @Override
        public void removeFromMultiBlock(MultiblockControllerBase controller) {}
    }
}
