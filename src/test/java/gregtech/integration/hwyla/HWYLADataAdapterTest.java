package gregtech.integration.hwyla;

import gregtech.BootstrapExtension;
import gregtech.api.util.world.DummyWorld;
import gregtech.integration.probe.ProbeInfoProducer;
import gregtech.integration.probe.ProbeLine;
import gregtech.integration.probe.ProbeText;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;

import com.mojang.authlib.GameProfile;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(BootstrapExtension.class)
class HWYLADataAdapterTest {

    private static final String KEY = "gregtech.test_lines";
    private static final ProbeInfoProducer CLIENT_LINE = (context, lines) -> lines
            .add(ProbeLine.text(ProbeText.literal("from the client")));

    @Test
    void aFallbackProducerShowsTheClientsLinesWhenTheServerSendsNone() {
        // a server without HWYLA never runs the NBT provider, so the client's data has no lines for it
        assertEquals(List.of("from the client"), body(HWYLADataAdapter.fromServerOrClient(KEY, TileEntity.class,
                CLIENT_LINE), new NBTTagCompound()));
    }

    @Test
    void theServersLinesWinOverTheClientsWhenItSendsThem() {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("gregtech.probe." + KEY,
                ProbeLine.writeList(List.of(ProbeLine.text(ProbeText.literal("from the server")))));
        assertEquals(List.of("from the server"), body(HWYLADataAdapter.fromServerOrClient(KEY, TileEntity.class,
                CLIENT_LINE), data));
    }

    @Test
    void aServerThatAnsweredWithoutLinesShowsNone() {
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("gregtech.probe." + KEY, ProbeLine.writeList(List.of()));
        assertEquals(List.of(), body(HWYLADataAdapter.fromServerOrClient(KEY, TileEntity.class, CLIENT_LINE), data));
    }

    @Test
    void aServerOnlyProducerShowsNothingWithoutTheServersLines() {
        assertEquals(List.of(), body(HWYLADataAdapter.fromServer(KEY, TileEntity.class, CLIENT_LINE),
                new NBTTagCompound()));
    }

    private static List<String> body(HWYLADataAdapter adapter, NBTTagCompound data) {
        EntityPlayer player = new EntityPlayer(DummyWorld.INSTANCE, new GameProfile(new UUID(0, 7), "hwyla_test")) {

            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        IWailaDataAccessor accessor = (IWailaDataAccessor) Proxy.newProxyInstance(
                IWailaDataAccessor.class.getClassLoader(), new Class<?>[] { IWailaDataAccessor.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "getNBTData" -> data;
                    case "getPlayer" -> player;
                    case "getSide" -> EnumFacing.NORTH;
                    case "getWorld" -> DummyWorld.INSTANCE;
                    default -> null;
                });
        IWailaConfigHandler config = (IWailaConfigHandler) Proxy.newProxyInstance(
                IWailaConfigHandler.class.getClassLoader(), new Class<?>[] { IWailaConfigHandler.class },
                (proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.TRUE : null);
        return adapter.getWailaBody(null, new ArrayList<>(), accessor, config);
    }
}
