# ME processing pattern buffers

The **ME Processing Pattern Buffer** keeps each accepted AE crafting CPU delivery
in a separate private batch. A supported multiblock consumes one batch together
with the buffer's shared ingredients. A **ME Pattern Buffer Proxy** lets another
multiblock use those batches without copying their contents.

Both IV-tier parts count as item and fluid inputs, so placement must satisfy both
structure limits. Addon controllers require explicit private-input-view support.

## Set up processing

1. Place the buffer, connect a powered, active ME network and enable **Toggle work**.
2. Insert an exact **processing** pattern with substitutions disabled in
   **Processing patterns**. Crafting-table patterns and duplicate patterns are rejected.
3. Supply the multiblock's energy and environment, and return its products to ME
   storage through its output system so the crafting CPU can receive them.
4. Request the product from an AE terminal. In **Private batches**, use the arrows
   to select a batch and **Next entries** to inspect its remaining ingredients.

Each delivery stays separate, including repeated deliveries of the same pattern.
Private batches work with Paint Groups on or off. Ordinary buses, hatches and
other batches do not supplement a private batch's recipe inputs.

## Shared ingredients

**Shared ingredients** provides nine item slots and nine 64,000 mB tanks, available
to every batch in this buffer, including through proxies. External item/fluid
automation accesses these shared stores; private contents remain internal.

Put deliberately shared ingredients here, such as nonconsumable catalysts.
Catalysts are checked at recipe start and are not reserved throughout processing.
Consumable shared ingredients are spent normally.

## Link a proxy

With a data stick, **left-click the buffer, then right-click the placed proxy**.
Both parts must have the same owner and be loaded in the same dimension. Open
their normal interfaces to establish ownership if needed. Interaction requires
owner/operator access, normal block permissions and applicable AE security access.

The proxy does not load chunks or duplicate storage. Its screen reports source
availability and provides **Unlink**. Several proxies still share the same batches.
After breaking and placing the buffer again, copy a fresh link and relink proxies.

## Pause and refund

**Toggle work** stops new deliveries and starts from this buffer. Already-started
recipes remain controller-owned. An offline ME network does not erase stored inputs.

**CPU cancellation does not automatically refund delivered ingredients.** Use
**Refund selected batch** or **Refund all**. Refunding batches stop supplying new
recipes. Remaining private inputs return to ME storage as space and power permit;
partial or refused transfers stay stored and retry every 20 ticks while the
network is active. Shared ingredients are excluded.

Refunds do not undo consumed work or affect products waiting for output space.
A pattern remains locked while its batches own ingredients. Finish those batches
or refund their remainders before editing the pattern.

## Capacity and fluid patterns

| Capacity | Limit |
| --- | --- |
| Pattern slots | 27 |
| Accepted private batches | 27 |
| Ordinary AE processing input positions | 16 |
| Supported FluidCraft input positions | 100 |
| Serialized private batch | 64 KiB |
| Combined private storage | 640 KiB |
| Individual pattern or shared-item/fluid identity | 8 KiB |

Tagged ingredients can fill the storage budget before all 27 batches are occupied.
Oversized or unsupported deliveries are rejected whole, retaining existing contents.

Ordinary item patterns use the default **AE2 UEL v0.56.4**. Fluid patterns require the separately
installed, pinned pair **AE2FluidCraft 2.6.6-r + AE2 UEL v0.56.5**, with an active
**Fluid Discretizer** connected for fluid crafting. Fluid drops and packets retain
their fluid tags. Gas patterns and other FluidCraft versions are unsupported.

## Save, move and recover

Normal saves retain patterns, shared ingredients, private remainders and links.
Normal removal keeps recoverable contents in the machine item. Place that item to
restore them, then relink proxies.

**Stored data inactive; repair required** retains undecodable state without making
it available to recipes. **Recovery blocked; world data retained** means the state
is too large for item recovery: normal removal is blocked. Preserve a backup and
repair the world data before moving it. Administrative deletion or another mod
bypassing normal removal hooks cannot guarantee recovery.
