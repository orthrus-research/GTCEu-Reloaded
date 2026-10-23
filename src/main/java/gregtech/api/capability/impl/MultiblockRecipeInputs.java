package gregtech.api.capability.impl;

import gregtech.api.capability.DualHandler;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.IRecipeInputViewProvider;
import gregtech.api.capability.RecipeInputView;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.MultiblockAbility;
import gregtech.api.metatileentity.multiblock.RecipeMapMultiblockController;
import gregtech.common.metatileentities.multi.multiblockpart.MetaTileEntityMultiblockPart;

import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Formation-owned views. No material is moved, copied, reserved or persisted here. */
public final class MultiblockRecipeInputs implements IRecipeInputViewProvider {

    private final RecipeMapMultiblockController controller;
    private final Map<IRecipeInputViewProvider, Long> providerRevisions = new IdentityHashMap<>();
    private final List<IRecipeInputViewProvider> providers = new ArrayList<>();
    private final Map<IRecipeInputViewProvider, Integer> providerColors = new IdentityHashMap<>();
    private final Map<Object, Integer> colors = new IdentityHashMap<>();
    private final Map<Object, IMultiblockPart> physicalOwners = new IdentityHashMap<>();
    private List<RecipeInputView> ordinaryViews = Collections.emptyList();
    private long revision;
    private boolean dirty = true;
    private boolean available;

    public MultiblockRecipeInputs(RecipeMapMultiblockController controller) {
        this.controller = controller;
    }

    public void invalidate() {
        this.dirty = true;
        this.revision++;
        this.ordinaryViews = Collections.emptyList();
        this.providerRevisions.clear();
    }

    public boolean hasProviders() {
        rebuild();
        return !providers.isEmpty();
    }

    public boolean isAvailable() {
        rebuild();
        return available && ownersAttached();
    }

    public boolean observeProviderChanges() {
        rebuild();
        boolean changed = false;
        for (IRecipeInputViewProvider provider : providers) {
            long current = provider.getRecipeInputRevision();
            Long previous = providerRevisions.put(provider, current);
            changed |= previous == null || previous.longValue() != current;
        }
        return changed;
    }

    @Override
    public List<RecipeInputView> getRecipeInputViews() {
        if (!isAvailable()) return Collections.emptyList();
        List<RecipeInputView> result = new ArrayList<>(ordinaryViews);
        Set<Object> privateKeys = new HashSet<>();
        for (IRecipeInputViewProvider provider : providers) {
            for (RecipeInputView view : provider.getRecipeInputViews()) {
                // A provider builds its complete private view; paint grouping must never append sources to it.
                if (view != null && view.getProvider() == provider && view.hasPrivateInputs() &&
                        provider.isRecipeInputViewValid(view) && privateKeys.add(view.getSelectionKey()))
                    result.add(view);
            }
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public long getRecipeInputRevision() {
        return revision;
    }

    /** Private material identity is independent of the attached consuming part's routing paint. */
    public int getPaintColor(RecipeInputView view) {
        return view.getProvider() == this ? view.getPaintColor() : providerColors.getOrDefault(view.getProvider(), -2);
    }

    @Override
    public boolean isRecipeInputViewValid(RecipeInputView view) {
        if (!isAvailable()) return false;
        if (view.getProvider() == this) return ordinaryViews.contains(view);
        return providers.contains(view.getProvider()) && view.hasPrivateInputs() &&
                view.getProvider().isRecipeInputViewValid(view);
    }

    public IItemHandlerModifiable outputItems(int color) {
        if (!isAvailable()) return new ItemHandlerList(Collections.emptyList());
        return new ItemHandlerList(selectPainted(controller.getAbilities(MultiblockAbility.EXPORT_ITEMS), color));
    }

    public IMultipleTankHandler outputFluids(int color) {
        if (!isAvailable()) return new FluidTankList(true);
        return FluidTankList.preservingTankOwners(controller.getOutputFluidInventory().allowSameFluidFill(),
                selectPainted(controller.getAbilities(MultiblockAbility.EXPORT_FLUIDS), color));
    }

    private void rebuild() {
        if (!dirty) return;
        this.dirty = false;
        this.available = controller.isStructureFormed();
        this.colors.clear();
        this.physicalOwners.clear();
        this.providers.clear();
        this.providerColors.clear();
        List<IItemHandlerModifiable> items = capture(MultiblockAbility.IMPORT_ITEMS, true);
        List<IFluidTank> fluids = capture(MultiblockAbility.IMPORT_FLUIDS, true);
        capture(MultiblockAbility.EXPORT_ITEMS, false);
        capture(MultiblockAbility.EXPORT_FLUIDS, false);
        if (!available) {
            this.ordinaryViews = Collections.emptyList();
            return;
        }
        List<IFluidTank> shared = MultiblockFluidInputs.shared(fluids, items);
        Set<Integer> groups = new LinkedHashSet<>();
        groups.add(-1);
        if (controller.isPaintGroupingEnabled()) {
            for (IItemHandlerModifiable item : items) groups.add(colors.get(item));
            for (IFluidTank fluid : shared) groups.add(colors.get(fluid));
        }
        List<RecipeInputView> views = new ArrayList<>();
        boolean distinct = controller.canBeDistinct() && controller.isDistinct();
        for (int group : groups) {
            List<IItemHandlerModifiable> groupItems = select(items, group);
            IMultipleTankHandler groupShared = FluidTankList.preservingTankOwners(
                    controller.getInputFluidInventory().allowSameFluidFill(), select(shared, group));
            if (distinct && !groupItems.isEmpty()) {
                for (IItemHandlerModifiable item : groupItems) {
                    views.add(new RecipeInputView(this, Arrays.asList(group, item), item,
                            MultiblockFluidInputs.extend(groupShared, Collections.singletonList(item)), group, false));
                }
                // A standalone fluid group does not require an item bus of its own color.
                if (groupShared.getTanks() > 0) views.add(new RecipeInputView(this, group,
                        new ItemHandlerList(Collections.emptyList()), groupShared, group, false));
            } else {
                views.add(new RecipeInputView(this, group, new ItemHandlerList(groupItems),
                        MultiblockFluidInputs.extend(groupShared, groupItems), group, false));
            }
        }
        this.ordinaryViews = Collections.unmodifiableList(views);
    }

    private <T> List<T> capture(MultiblockAbility<T> ability, boolean input) {
        List<T> ordinary = new ArrayList<>();
        for (T instance : controller.getAbilities(ability)) {
            IMultiblockPart owner = controller.getAbilityOwner(ability, instance);
            if (!(owner instanceof MetaTileEntity part) || !attached(owner)) {
                this.available = false;
                continue;
            }
            int color = part.getPaintingColor();
            capturePhysicalOwner(instance, owner, Collections.newSetFromMap(new IdentityHashMap<>()));
            if (color < -1 || color > 0xFFFFFF) this.available = false;
            Integer previous = colors.put(instance, color);
            if (previous != null && previous != color) this.available = false;
            if (input && owner instanceof IRecipeInputViewProvider provider) {
                if (!providers.contains(provider)) providers.add(provider);
                Integer priorColor = providerColors.put(provider, color);
                if (priorColor != null && priorColor != color) this.available = false;
            } else ordinary.add(instance);
        }
        return ordinary;
    }

    private void capturePhysicalOwner(Object handler, IMultiblockPart owner, Set<Object> visited) {
        if (!visited.add(handler)) return;
        IMultiblockPart previous = physicalOwners.putIfAbsent(handler, owner);
        if (previous != null && previous != owner) this.available = false;
        if (handler instanceof DualHandler dual) {
            capturePhysicalOwner(dual.getItemDelegate(), owner, visited);
            capturePhysicalOwner(dual.getFluidDelegate(), owner, visited);
        } else if (handler instanceof ItemHandlerList list) {
            for (Object child : list.getBackingHandlers()) capturePhysicalOwner(child, owner, visited);
        } else if (handler instanceof IMultipleTankHandler.ITankEntry entry) {
            capturePhysicalOwner(entry.getDelegate(), owner, visited);
        } else if (handler instanceof IMultipleTankHandler tanks) {
            for (IFluidTank tank : tanks.getFluidTanks()) capturePhysicalOwner(tank, owner, visited);
        }
    }

    private <T> List<T> select(List<T> instances, int color) {
        if (!controller.isPaintGroupingEnabled()) return new ArrayList<>(instances);
        return selectPainted(instances, color);
    }

    private <T> List<T> selectPainted(List<T> instances, int color) {
        List<T> selected = new ArrayList<>();
        for (T instance : instances) {
            Integer actual = colors.get(instance);
            if (actual != null && (actual == -1 || actual == color)) selected.add(instance);
        }
        return selected;
    }

    private boolean ownersAttached() {
        return controller.isStructureFormed() && attached(MultiblockAbility.IMPORT_ITEMS) &&
                attached(MultiblockAbility.IMPORT_FLUIDS) && attached(MultiblockAbility.EXPORT_ITEMS) &&
                attached(MultiblockAbility.EXPORT_FLUIDS);
    }

    private <T> boolean attached(MultiblockAbility<T> ability) {
        for (T instance : controller.getAbilities(ability)) {
            IMultiblockPart owner = controller.getAbilityOwner(ability, instance);
            if (!attached(owner) ||
                    !java.util.Objects.equals(colors.get(instance), ((MetaTileEntity) owner).getPaintingColor()))
                return false;
        }
        return true;
    }

    private boolean attached(IMultiblockPart owner) {
        return owner instanceof MetaTileEntity part && part.isValid() && owner.isAttachedToMultiBlock() &&
                (!(part instanceof MetaTileEntityMultiblockPart nativePart) ||
                        nativePart.getController() == controller) &&
                controller.getMultiblockParts().contains(owner);
    }
}
