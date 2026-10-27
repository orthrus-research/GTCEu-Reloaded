# PipeNet compatibility contract

Baseline: `316f0df8d5b029f905b8af79ee34c09a15623c20`.

This implementation retains existing component and physical-walker ownership while repairing
transport accounting, capability lifetimes, cache invalidation, graph mutation and persistence.
The original baseline remains the reference for supported API and saved-data compatibility;
the intentional behavior corrections and migration limits are recorded below.

- Preserve registered blocks/tiles, material/type identities, enum ordering, concrete
  capabilities, cover wrappers, protected extension hooks and legacy NBT codecs.
- Preserve current physical routing across differently painted components. Correct stale
  routes caused by an edit in another traversed component; do not silently impose paint
  isolation as an optimization.
- Preserve ordinary source-owned 20-tick item allowance and routing/distribution order.
  Exact destination batches may accrue elapsed-time allowance independently of probe count;
  simulation must not spend live inventory, fairness or arm credit. All real transfers debit
  shared ingress accounting. This fixes loss/stalls and is an explicit behavior correction.
- Preserve source-arm exact acceptance against the whole pipe capability; a batch may span
  endpoints. Do not impose per-edge budgets on ordinary item routing.
- Refresh endpoint discovery within 200 ticks even when other destinations already work.
  Handle backwards world time. Live covers/capability admission remain operation-time checks.
- Preserve runtime-added cover modes and the exact selected addon cohort. Native qualification
  must prove required injections, mode save/sync and mixed buffered/tankless fluid connections.
- Reject malformed graph replacement without damaging current state. Never discard otherwise
  recoverable stored fluid merely because its saved channel layout is unexpected.
- Keep built-in fluid storage, distribution cadence, channels and hazards; energy path loss,
  heat and telemetry; optical/laser topology restrictions and query behavior.

The full gates include source/tests/build/reobfuscation, old/new save roundtrips, isolated
Cleanroom client/server, multiplayer, exact transformed cohort, copied-pack runs, and ten or
more matched process pairs with identical workloads. Reports distinguish each evidence scope.
No source test or microbenchmark alone establishes native or full-pack compatibility.

## Validation scope

The selected implementation passed source/build checks, original-compiled API and codec probes,
built-in native transport and save fixtures, and isolated client/server synchronization and
reconnection in Cleanroom 0.7.12-alpha on Java 25 with ModularUI 3.1.6. The runtime-added arm
mode was tested with an explicit RETAIN-only Sussy configuration, not every Sussy feature.

The broader retained Core/addon cohort fails on existing GregTech API mismatches, including
the `Elements.add` signature and `RecipeProperty` types. Its mixed tankless/buffered, bulk-tool
and telemetry fixtures cannot run past initialization. Full-pack compatibility therefore
remains unqualified. These repairs do not supply replacement addon APIs or loader shims.

The repairs, regression fixtures and compatibility investigation were AI-assisted with
OpenAI Codex. This disclosure does not expand the validation scopes stated here.

## Component identity and extension equality

World and chunk ownership lists retain their existing public/protected types, but
membership and removal compare network object identity. Republishing the same
object is idempotent. Distinct subclass networks that compare equal can still own
separate positions and must both be published. Removing one removes that exact
owner. Identity removal also corrects a pre-existing ambiguity where `List.remove`
could remove another equal owner; property and node equality rules remain unchanged.

## Exact-batch accounting and handler limits

Ordinary covers keep the existing transfer-before-refill order. A cover first observed
at timer phase zero can spend its constructor allowance and receive the next allowance
at the end of that same tick. This startup burst is preserved. Source exact arms reserve
an unused allowance once when banking it, so repeated five-tick retries cannot bank the
same allowance again. Both timer phases zero and one are regression fixtures.

Exact destination credit starts on the first observed path rate, using the remaining
current allowance. It does not accrue unseen world history. Its capacity retains the
largest observed configured batch plus one rate window of headroom. Actual outgoing
amounts debit every existing ingress rate bucket; zero transfers change no credit.
Simulation does not consume the live arm buffer, inventories or distribution state.

Forge item handlers do not offer a universal transaction across arbitrary endpoints.
A handler can disagree with its simulation. Rejected extracted items are restored to
the source where possible; leftovers return to the owning caller or a carrying
`ItemTransferException`, and world-aware callers drop them at their own position.
This preserves ownership without promising atomic rollback of arbitrary addon effects.

## Item priority aliases and first save

Item graph writers emit both integer `Resistance` and integer `Range` with the same
priority. Readers retain the existing policy: a typed integer `Resistance` wins;
otherwise a typed integer `Range` is used, including negative priorities. Invalid
types with no valid integer alias still fail validation.

After a complete item-world record passes validation, the world data is marked dirty
if any decoded properties lacked either integer alias or contained different alias
values. Forge's existing `MapStorage` then rewrites an otherwise untouched graph on
its next normal save. This matters because the original reader consumes `Range`;
merely dual-writing when some unrelated edit dirties a graph leaves untouched old
`Resistance`-only files incompatible with that downgrade reader.

A matching pair of integer aliases loads without requesting a rewrite, including
after the normalized file is reloaded. This applies only to item graph properties.
A rejected initial record retains its raw quarantine, and a failed manual reload
keeps the accepted graph, writer and prior dirty state. Normalization does not repair
malformed records or remove the separate dimension/downgrade limits below.

## Pinned tankless addon limits

The selected Susy-Core 0.2.5 binary owns its tankless graph, caches and handlers.
Its literal `susy.tankless_fluid_pipe_net` record still uses shared world storage;
its route cache is position-only without periodic discovery, and a retained fluid
handler does not revalidate current tile/net ownership on each fill. These pre-existing
addon limits are not repaired by the built-in optical/laser storage or handler changes.
Extension hooks remain eager and the existing physical hybrid-connection APIs are retained.

PipeNet rejects duplicate positions in a saved graph and preserves the raw
rejected record through quarantine. A historical shared addon record may already be
ambiguous; neither graph validation nor loaded-tile recovery can infer its original
dimension. Native hybrid transfer qualification does not imply tankless dimension or
retained-handler qualification. A future addon repair must be owned, pinned and tested
separately, with the same adaptation applied to both comparison candidates.

## Optical and laser dimension storage

Optical and laser topology now uses Forge's existing `World.getPerWorldStorage()`.
The identifiers remain `gregtech.optical_pipe_net` and `gregtech.laser_pipe_net`.
Forge's `WorldSpecificSaveHandler` places these records in each dimension's own
`data` directory. The overworld therefore keeps the existing literal filenames in
the save's root `data` directory. Other dimensions receive independent records;
the old shared root records are retained.

Old shared records do not identify the dimension of each saved position. They may
already contain collisions or topology from more than one dimension. Loading an
old save cannot resolve that ambiguity from coordinates alone. No root record is
cloned into another dimension, assigned a guessed dimension, or deleted. A warning
reports an existing shared record when a non-overworld local record is first created.

On a real server world, each loaded pipe schedules one task through the existing
`TaskScheduler`. It checks that the same valid tile is still loaded at the same
position in the same world, then restores only missing membership from the tile's
current properties, paint, open connections and activity. Detached previews do not
schedule recovery. Existing graph membership is retained. Unloaded chunks recover
as their actual tiles load; topology that has no surviving tile cannot be assigned
automatically. Reconstruction is not a general repair of ambiguous historical data.

Before downgrading, retain a full copy of the upgraded save. Older versions read the
shared root record and do not read the new non-overworld records, so changes made in
those dimensions after upgrade will not be represented by the older reader. There
is no automatic reverse migration. Any manual migration needs independent evidence
of dimension ownership and must preserve the original records.

## Runtime cover transfer modes

Robotic arms and fluid regulators share `TransferMode`. Its three declared constants
retain their original ordinals. `TransferMode.VALUES` retains its public static array
descriptor; its field changes from `final` to `volatile` so the owner can refresh the
snapshot after a supported enum extension. Existing compiled `GETSTATIC` consumers
continue to link. Saved and packet ordinals are unchanged.

The selected SussyPatches extension appends its fourth mode to the enum's synthetic
values array in a merged class initializer, after the original public snapshot was
created. Forge/Cleanroom enum helpers maintain the synthetic array and JVM enum
caches, but do not repair arbitrary owner-defined array aliases. GT refreshes its
snapshot at the end of initialization, post-initialization and load-complete. It
does not force the enum to load during construction or pre-initialization. Owned
arm/regulator NBT and synchronization readers obtain current runtime values through
`byOrdinal`, which also refreshes the public snapshot. Invalid ordinals still fail.

An external raw field read before the first owned refresh may observe the original
snapshot. An external caller retaining an old array keeps that old array; its size
cannot change. An addon extending the enum later must refresh the owner snapshot
before relying on raw field consumers. Dynamic ordinal decoding does not promise
that arbitrary additions after javac switch maps initialize have operational cover
behavior, or change the existing byte-sized packet ordinal format. This correction
adds no fourth-mode implementation, dependency or Mixin. Native qualification still
requires the original public-cache gate plus actual fourth-mode NBT and packet
roundtrips under the selected transformed addon cohort.

## Rejected initial graph records

Forge's `MapStorage` catches a saved-data reader exception and can retain the newly
created object. If an initial graph record fails validation, PipeNet retains a deep
copy of that complete raw NBT and reports quarantine in the log and through
`WorldPipeNet.hasQuarantinedData()`. Saving returns the quarantined record, including
unknown tags. Loaded-tile reconstruction may restore runtime membership but cannot
replace that saved record with an empty or partially reconstructed graph.

A failed manual reload into an existing valid graph keeps the existing graph and
writer. Quarantine is cleared only by an explicitly supplied replacement that
passes graph validation. Repair requires manual inspection and a backup; validation
does not establish which dimension historically owned ambiguous shared coordinates.
