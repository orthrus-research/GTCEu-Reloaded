package gregtech.api.configuration;

import net.minecraft.world.storage.ISaveHandler;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

/** Records accidental legacy item loads, whose priority normalization can dirty the shared file. */
final class PipeDimensionGlobalStorage extends MapStorage {

    int itemLoadRequests;

    PipeDimensionGlobalStorage(ISaveHandler files) {
        super(files);
    }

    @Override
    public WorldSavedData getOrLoadData(Class<? extends WorldSavedData> type, String name) {
        if ("gregtech.item_pipe_net".equals(name)) this.itemLoadRequests++;
        return super.getOrLoadData(type, name);
    }
}
