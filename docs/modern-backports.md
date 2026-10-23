# Modern-inspired features for 1.12.2

These features adapt selected CEu Modern behavior to the existing 1.12.2 machine, recipe and UI APIs. They do not provide general Modern addon or script compatibility.

## Recipe and machine controls

- JEI recipes have local tier controls for normal and perfect-overclock estimates, with an exact reset. Fusion and computation recipes keep their original values and explain that machine-specific estimates are unavailable.
- Machine screens, HWYLA and The One Probe show observed recipe failure reasons. Reading a status does not repeat recipe checks or consume resources. Maintenance hatches show current maintenance faults.
- Common simple machines show contextual tool hints using existing artwork. Unknown subclasses and covers require explicit addon support; the hints do not predict external protection-plugin permission decisions.
- Drum and quantum-tank inventory fluid badges are independently configurable and default off (`client.drumFluidBadges` and `client.quantumTankFluidBadges`).
- Electric Blast Furnace, Large Chemical Reactor, Distillation Tower and Assembly Line JEI previews support validated replacement, part counts and generated repetition controls. Existing custom examples remain available. Invalid changes preserve the previous preview; addons must explicitly opt in.

## Configuration card

Use the Configuration Memory Card in the main hand: sneak-right-click copies, right-click pastes, and crafting the card alone clears it. Common simple machines support facing, input/output settings, muffling and circuit settings. Existing pumps and conveyors support scalar settings when the complete source and destination cover layouts match. Item and fluid pipes support connections and blocked faces, with reciprocal neighbors checked before changes.

The card preserves filter items and filter settings. It does not copy inventory contents, energy, running work or overclock limits. Unsupported machines, covers, unavailable neighbors and denied edits reject the operation. The existing data stick keeps its separate behavior.

## Automation

ME stocking parts expose minimum visible stock, refresh timing and an optional most-abundant Auto-Pull policy. A minimum is an eligibility threshold, not a protected reserve. Refresh `0` preserves legacy timing; `1..200` selects a shared interval. The default First 16 policy keeps network ordering, while Most stock ranks extractable amounts and uses stable ties. Manual selections remain manual.

Composite item and fluid filters combine physical child filters with OR matching. Multiblock output filters participate in insertion checks. If completed products cannot fit, the machine retains them and waits without charging more processing resources.

Combined item/fluid hatches expose paired stores without flattening distinct inputs. Admitted controllers can opt into Paint Groups: painted inputs and outputs match by color, unpainted inputs are shared, and unpainted outputs accept every group. Grouping defaults off; a mode change applies to the next recipe. A running job retains its selected output route.

[AE processing-pattern buffers and proxies](ae-processing-buffers.md) add isolated batches supplied by real AE crafting handoffs, explicit refunds and saved recovery.

## Recipe and addon APIs

[Recipe conditions](recipe-conditions.md) provide immutable environment requirements, script methods and persisted processing holds. [Startup recipe modifiers](recipe-modifiers.md) provide ordered, opt-in transformations for custom machines. Both retain explicit compatibility bounds; existing machines without providers keep their legacy paths.

Recipe lookup can try eligible alternatives without consuming rejected inputs. Core scheduling changes avoid unchanged energy notifications, unnecessary idle recipe callbacks and redundant auto-output capability probes for admitted implementations. They preserve ordinary machine ticking and do not make a general performance guarantee for addons or modpacks.
