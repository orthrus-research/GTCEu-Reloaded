# PipeNet helpers and ownership

PipeNet uses the existing Minecraft, Forge and GregTech capability and persistence APIs.
The table identifies which component owns each behavior and the constraints for extending it.

| Responsibility | Owner | Extension constraints |
| --- | --- | --- |
| Neighbor discovery and endpoints | `INeighborCache`, `IPipeTile`, `IRoutePath`, Forge item/fluid capabilities and GregTech energy/data capabilities | Preserve concrete handler contracts and operation-time cover checks. |
| Route keys and caches | `FacingPos`, JDK collections and fastutil | Keys distinguish all six ingress faces and null without truncating coordinates. |
| World persistence and chunk lifetime | `WorldSavedData`, native tile NBT, unload hooks and `TaskScheduler` | Use the existing dirty/save lifecycle. Avoid duplicate tickers, chunk loaders or persistence services. |
| Item, optical and laser dimension ownership | `World.getPerWorldStorage()` and `WorldSpecificSaveHandler` | Keep existing record IDs. Recover missing membership from loaded tiles; old shared records cannot establish non-overworld ownership. |
| Exact item batches | Ingress-owned allowance accounting and the robotic-arm buffer | Rejected simulations must not spend live inventory, fairness or elapsed-time credit. Real transfers debit shared ingress accounting. |
| Virtual cover-admission packets | Transient `ItemStackHandler` subclass | Preserve the requested virtual packet count without changing real inventory stack limits. |
| Runtime cover modes | Forge/Cleanroom enum helpers and `TransferMode` | Refresh the owner-defined `VALUES` snapshot separately from JVM enum caches. Preserve saved ordinals; external references to an older array remain unchanged. |
| Pipe promotion to a ticking tile | Existing pipe-cover holder and promotion handoff | Retain live cover/filter/wrapper identity during promotion. Generic copies remain detached and do not share covers. |
| In-flight transfers during promotion | Shared transient guard and successor forwarding | A transfer must release the same guard after promotion. Counter, budget and fairness access forwards to the replacement owner. |
| Network identity | Existing component and chunk collections | Distinct network instances remain distinct even if an addon gives them equal values. Membership and removal use object identity. |
| Graph and fluid records | Native NBT codecs and validation | Reject malformed graph replacement atomically. Preserve recoverable fluid records that cannot be assigned to the current channel layout. |

These responsibilities stay in the transport implementation. Presentation libraries do not own
route budgets, graph mutation or saved pipe state. Additional helpers should have one clearly
defined owner and preserve the contracts in [PipeNet compatibility](pipenet-compatibility.md).

The supported runtime and validation scope are recorded in
[release validation](pre-delivery-qualification.md). Optional addon behavior requires its own
compatibility checks; a successful loader start does not establish transfer or save correctness.
