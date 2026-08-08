I read every file listed, plus `AppModule.kt`, `GameViewModel.kt`, `DefaultGameRepository.kt`, `BoardGraph.kt`, `RuleEngine.kt`, `TurnManager.kt`, `VictoryChecker.kt`, `build.gradle.kts` and the `strings.xml` locales. Below is the single spec.

---

# Koridor — EXPERT ("Uzman") bot: implementation spec

## Part 0 — Verdict on the two designs, before anything else

A and B agree on the skeleton (mutable board, negamax + alpha-beta + iterative deepening, flagged Zobrist TT, ~700 ms/1200 ms, differential tests, root re-validation through `GameEngine`). That skeleton survives contact with the constraints and is what gets built. The disagreements, decided:

**Evaluation unit — B wins, outright.** A prices a tempo as a tuned constant (`W_TEMPO = 55`). B derives it: `plyToFinish(p) = 2*d_p - (1 if p to move)`, so `race = 2*d_you - 2*d_me + 1`, always odd, and "a wall must cost the opponent two steps or it is a losing trade" is arithmetic rather than a weight. That is the single most valuable idea in either document. Take B's formulation and delete A's tempo constant.

**Exact wall-less race — both proposed it; B's one-sided version is stronger and I keep it with a margin.** A only fires it when *both* reserves are zero. B also fires it when only the opponent is out of walls, which is sound here: `BoardGraph.canTraverse` consults only `walls`, so with the opponent holding zero walls my distance can never grow. I verified that in the source. B's margin of 5 plies is a guess about jump tempo; I keep 5 and say exactly what it buys.

**B's `solveRace()` sub-solver — cut.** B claims "a few thousand nodes" for a pawn-only search to depth `2*(d_me+d_opp)+4`. At `d_me = d_opp = 8` that is depth 36 at branching 3-5; it is not a few thousand nodes, it is unbounded. It is also redundant: once both reserves are zero the wall generator is switched off by rule, branching collapses to ~3.5, and the *main* iterative-deepening search reaches depth 12-14 inside the normal budget with the exact race arithmetic as its leaf score. No second search engine. This removes a whole subsystem and a whole risk.

**A's anchor filter — as written it is wrong, and I have a counterexample.** A claims a new wall can only complete a separating barrier if *both its end posts* are anchored. It can also complete one using only *half* of itself, turning at its own centre post. Concretely, on the real geometry: place `Wall(7,0,VERTICAL)` (posts (7,1),(8,1),(9,1); blocks column boundary 0/1 at rows 7 and 8) — legal, cuts nobody off. Then place `Wall(6,0,HORIZONTAL)` (posts (7,0),(7,1),(7,2); blocks row boundary 6/7 at columns 0 and 1). Cells (7,0) and (8,0) are now sealed off entirely — a `PLAYER_ONE` pawn standing there has no path to row 0, so `WallValidator.isValid` rejects it. But that wall's *end* posts are (7,0) (on the boundary, anchored) and (7,2) (unanchored), so A's filter says "cannot cut off, skip the BFS" and the engine would search an illegal move. That is precisely the failure the brief calls worse than a weak bot.

The corrected filter, which *is* sound and just as cheap: a wall's three posts are `endA`, `centre`, `endB`; a post is **anchored** if it lies on the outer lattice boundary (coordinate 0 or 9) or is already touched by a placed wall. Run the two BFS iff **at least two of the three** posts are anchored. Proof: the barrier's intersection with the new wall is a maximal sub-path of one or two of its unit segments; both endpoints of that sub-path must be on the outer boundary or incident to another wall's segment; those endpoints are two of `{endA, centre, endB}`. In the opening no wall has two anchored posts (a horizontal wall spans post columns `c..c+2` with `c ≤ 7`, so it can never reach both `0` and `9`), so **zero** cut-off BFS run early in the game. This is A's optimisation, fixed.

A also has the post lattice wrong: it is 10×10 (coordinates 0..9), not 9×9 with boundary at 8. `IntArray(81)` for `postTouch` would be an out-of-bounds bug.

**Move generation — merged, but deliberately, not as mush.** A's "extensions of existing walls" and B's class (c) are the same idea; B's "slot denial on my own path" is a distinct and genuinely expert-level class that A lacks. Both are in. B's *depth-1 wall filter* (only walls gaining ≥2 plies at the frontier) is cut: it needs 2 BFS per candidate exactly where nodes are most numerous, and B itself lists the blind spot it creates. B's class (e) ("root only, when I hold a wall surplus and the path has a narrow section") is cut as unspecifiable.

**Aspiration windows — cut.** Both designs propose them and both admit they are marginal. With a saturating race term the score swings hard exactly when a wall trap appears, which is the worst case for aspiration. Not in v1.

**Null-move and quiescence — both designs reject them; agreed, rejected.**

**A's `@Singleton` + `@Synchronized`** — the concurrency hazard is real (I confirmed `runAiTurn` at `GameViewModel.kt:356` does `aiJob?.cancel()` then relaunches; cancelling a coroutine does not interrupt a non-suspending CPU loop). But `@Singleton` is the wrong half of the fix. Engines are constructor-injected into `AIEngineFactory`, which is constructor-injected into `GameViewModel`, so unscoped already gives one instance per screen and a fresh one per restart. Keep it unscoped, allocate the tables lazily on first `chooseAction`, and make `chooseAction` `@Synchronized`.

**Both designs' claimed depths (6-8) are cost models, not device measurements.** I redo the arithmetic in §6 and land on a more conservative number.

---

## Part 1 — The audit, re-checked against the source

Every claim below I verified by reading. Where the audit asserted a *measurement* I could not reproduce, I say so and the implementer should not rely on it.

### Confirmed, structural, act on these

**1. `orderedActions` sorts every wall ahead of every non-winning pawn move — `HardAI.kt:95-107`. CONFIRMED.** The second selector returns `-pathFinder.distance(...)` for a `MovePawn` (≤ -1 for any non-winning move) and `1` or `0` for a `PlaceWall`. `thenByDescending` puts `1`/`0` before `-1..-8`. Both at maximising and minimising nodes. Since the loop improves on strict `>` / `<`, **every tie goes to a wall**, and `fallback` at line 24 is a wall. Certain.

**2. The transposition cache is both useless and unsound — `HardAI.kt:31, 64, 65, 91`. CONFIRMED, three separate faults.**
   - `CacheKey(state.hashCode(), ...)`: `BoardState` (`BoardState.kt:5-12`) is a data class whose generated `hashCode` covers `history: List<TurnRecord>` and `turnNumber`. Two nodes share a key only if they share the whole move sequence, which inside one tree means they are the same node. Hit rate is structurally ~0 (bar 32-bit collisions), while the hash itself is O(turns) and is paid at every interior node.
   - `cache[key] = bestScore` at line 91 runs after `if (beta <= alpha) break` at line 89, so a fail-high/fail-low **bound** is filed and later returned as an **exact** score, with no flag, no depth check and no key verification.
   - `val cache = HashMap<...>()` is inside the depth loop at line 31, so nothing survives between iterative-deepening iterations.
   
   *Downgrade one sub-claim:* Design A says the root can return a null action on a cache hit. Not reachable — the cache is empty when the root probes it each iteration. Ignore that argument.

**3. Wall candidates are structurally almost all horizontal, and truncated in the wrong place — `AIActionGenerator.kt:39-44, 60-82`. CONFIRMED.** `wallsBlockingEdge` emits `HORIZONTAL` walls for a vertical path edge and `VERTICAL` for a horizontal one; a pawn walking straight up column 4 produces horizontal candidates only. `.take(maxWallCandidates)` (10) is applied after the validity filter over a `LinkedHashSet` in insertion order, and the opponent's path is inserted first (line 37 before line 38), so the AI's own-path candidates essentially never survive. Certain.
   
   The audit's opening arithmetic checks out: at move one both pawns are in column 4, and every candidate is a horizontal wall at `(r,3)` or `(r,4)`, each of which blocks column 4 for **both** pawns. Under a symmetric evaluation each is worth zero and costs a wall; under `OPPONENT_DISTANCE_WEIGHT=20` vs `OWN_DISTANCE_WEIGHT=18` each scores `+2*20 - 2*18 = +4` — HardAI is paid to throw a wall away on move one. That single line explains most of the owner's complaint.

**4. Evaluation has no side-to-move term, and the weights are backwards — `HardAI.kt:109-123`, `Constants.kt:33-36`. CONFIRMED.** `evaluate` never reads `state.currentPlayer`. `OPPONENT_DISTANCE_WEIGHT (20) > OWN_DISTANCE_WEIGHT (18)` makes delaying worth more than advancing. `WALL_COUNT_WEIGHT = 3` prices a whole wall at one sixth of a step, so the wall cost cannot even break a tie against a useless placement. `IMMEDIATE_THREAT_WEIGHT = 2000` is a ±2000 cliff, turn-blind, inside an evaluation whose other terms span ~±200. The audit's worked example is right: both pawns at distance 1 with equal walls scores `20 - 18 + 2000 - 2000 = 2` whether the position is won or lost.

**5. `pathFinder.distance` is called from inside the sort comparator — `HardAI.kt:102`. CONFIRMED.** `compareBy{}.thenByDescending{}` re-evaluates its selector on every comparison, so a full A* (PriorityQueue + `HashMap<Position,Position>` + a fresh `Array(9){IntArray(9)}`, with `BoardGraph.canTraverse` linearly scanning the wall `Set` per edge) runs O(n log n) times per node instead of once per pawn move.

**6. Every wall candidate is fully validated twice per node — `AIActionGenerator.kt:41` then `HardAI.kt:74`. CONFIRMED.** `strategicActions` filters with `wallValidator.isValid`, which runs `bfsValidator.hasPath` for both players (`WallValidator.kt:19-22`); `gameEngine.perform` then calls `ruleEngine.validate` (`GameEngine.kt:22`) which calls the identical `isValid` again.

**7. Terminal score is not ply-adjusted — `HardAI.kt:111`. CONFIRMED as code.** *Downgrade the impact:* the audit measured this fix at exactly 16/32, i.e. no effect at depth 3. It matters only once the search is deep. Fix it, don't credit it.

**8. `history = state.history + record` copies the whole game history per node — `GameEngine.kt:42`. CONFIRMED.** Nothing in `RuleEngine`, `MoveValidator`, `WallValidator`, `VictoryChecker` or `TurnManager` reads `history`, and only `TurnManager` reads `turnNumber` (to increment it). So the fast board may omit both and remain observation-equivalent for legality and outcome. **Do not change `GameEngine`** — the UI's undo depends on `history`. The fix is not to search through `GameEngine` at all.

**9. Search limits are inlined `const val`s — `Constants.kt:29-30`, read at `HardAI.kt:28,30`. CONFIRMED.** No test can lower them. This is what blocks any strength harness, so it is fixed first.

**10. The AI module does not compile. CONFIRMED, and it is the first thing to fix.** `Difficulty` has `EXPERT` (`GameTypes.kt:17`); `AIEngineFactory.forDifficulty` (`AIEngineFactory.kt:11-15`) has three branches and no `else`. Everything downstream is already wired and waiting: `DefaultGameRepository.kt:147,154` map to `Keys.expertWins`/`expertLosses`, `Constants.Data.KEY_EXPERT_WINS`/`KEY_EXPERT_LOSSES` exist (lines 90, 94), `GlyphKind.EXPERT` exists (`KoridorGlyphs.kt:28,143`), `PlayScreens.kt:92` offers the tier, and `difficulty_expert` is translated in all ten locales including `values-tr` → **"Uzman"**. **The AI engine is the only missing piece. No resource, stats-key or navigation work is required.** (Both designs list that work as a risk; it is already done.)

### Downgraded — real code facts, unverifiable numbers

- **Time budget inert (`HARD_MAX_DEPTH = 3` stops the search before 850 ms).** The *structure* is certain: the loop runs `1..3`, and `catch (_: SearchTimedOut) { break }` at lines 43-45 discards the whole partial iteration. The specific desktop timings (19 ms median, 421 ms max, 0/289 at budget) I could not reproduce and the implementer should not quote them.
- **Every measured match result** (25/32 for the ordering fix, 22/32 for the weight fix, 18/32 for depth 4, 16/32 for the mate-score fix, 8/12 for full wall generation, 0 cache hits in 38 probes, 337 ns `hashCode`). Plausible, consistent with the code, unverified here. Treat them as hypotheses the new harness will re-measure.
- **The "medium considers a wider wall set than hard" oddity** (`MediumAI.kt:24` passes `HARD_MAX_WALL_CANDIDATES * 2 = 20`) is confirmed as code. It disappears in this spec because HARD stops using `AIActionGenerator`.

---

## Part 2 — What happens to HARD

**HARD and EXPERT share one engine, `SearchAI`, differing only in an injected `SearchConfig`. `HardAI.kt` is deleted.**

Defence:

1. Every defect above is a defect of *quality*, not of *strength*. Keeping a separate HARD means keeping a second evaluation function that is known to be wrong about tempo, a second move generator that cannot produce a vertical wall in the opening, and a second search with a cache that files bounds as exact scores. The owner's complaint is that the top of the ladder is bad; shipping EXPERT next to an unchanged HARD leaves three quarters of the players still playing the bad bot.
2. Two evaluations means the ladder can go non-monotone under maintenance — the exact failure the owner is reporting. One evaluation, two budgets, makes "harder" mean "searches deeper", which is the only definition that stays true after a refactor.
3. It halves the test surface. The differential test, the tactics tests and the equivalence guarantees cover both tiers at once.
4. Deleting the file rather than deprecating it removes the broken cache permanently. The frozen copy that the strength harness needs lives in **test** sources as `LegacyHardAI`, so production carries none of it and the regression baseline can never drift.

HARD's config (§4.7) gives it depth 4 at a 200 ms soft budget with 8/6/4 wall candidates and LMR off — measurably weaker than EXPERT's depth 6-7, and far stronger than today's depth 3 with inverted ordering. **MEDIUM and EASY are untouched**; they define the low rungs, they are cheap, and perturbing them would invalidate the ladder assertions for no gain.

---

## Part 3 — Files

### Added

| Path (under `app/src/main/kotlin/com/duzman46/gridbound/`) | Contents |
|---|---|
| `game/ai/search/SearchConfig.kt` | `data class SearchConfig`, `fun interface SearchClock`, companion presets |
| `game/ai/search/WallGeometry.kt` | `internal object` of precomputed tables — slot ↔ `Wall`, edge-clear masks, conflict masks, post ids, slot adjacency |
| `game/ai/search/Zobrist.kt` | `internal class Zobrist(seed: Long)` |
| `game/ai/search/FastBoard.kt` | `internal class FastBoard` — the mutable position with make/unmake, move generation, BFS |
| `game/ai/search/TranspositionTable.kt` | `internal class TranspositionTable(sizeLog2: Int)` |
| `game/ai/SearchAI.kt` | `class SearchAI(...) : AIEngine` — the search, evaluation and ordering |

### Changed

| Path | Change |
|---|---|
| `game/ai/AIEngineFactory.kt` | Add the `EXPERT` branch; HARD now resolves to a qualified `SearchAI` |
| `di/AppModule.kt` | Add `@ExpertEngine` / `@HardEngine` qualifiers and three `@Provides` in the existing `AiModule` |
| `core/Constants.kt` | Rewrite `object Ai` (§4.8) |
| `game/ai/MediumAI.kt` | One line: `Constants.Ai.HARD_MAX_WALL_CANDIDATES * 2` → `Constants.Ai.MEDIUM_MAX_WALL_CANDIDATES` |

### Deleted

`game/ai/HardAI.kt`.

### Test sources (`app/src/test/kotlin/com/duzman46/gridbound/game/`)

`ai/LegacyHardAI.kt` (frozen baseline), `ai/FastBoardEquivalenceTest.kt`, `ai/SearchAITacticsTest.kt`, `ai/StrengthHarness.kt`, `ai/StrengthTest.kt`, and edits to `AIEngineTest.kt`.

---

## Part 4 — The engine

### 4.1 Encodings — fixed for the life of the project

```
cell   = row * 9 + column                                   0..80
slot   = (row * 8 + column) * 2 + orientation.ordinal        0..127   (HORIZONTAL = 0)
move   = cell                     for a pawn move            0..80
       = 128 + slot               for a wall placement       128..255
post   = postRow * 10 + postCol                              0..99    (both 0..9)
side   = PlayerId.ordinal                                    0 = PLAYER_ONE, 1 = PLAYER_TWO
goalRow[0] = 0,  goalRow[1] = 8
NO_MOVE = 511
```

Direction bits in `open[cell]`: `UP = 1` (row-1), `DOWN = 2` (row+1), `LEFT = 4` (col-1), `RIGHT = 8` (col+1). Board-edge bits are cleared at construction, so `open[]` alone answers "is this step on the board and unblocked" — no bounds checks anywhere in the hot path.

**Wall → edges cleared** (transcribed from `BoardGraph.isVerticalTransitionBlocked` / `isHorizontalTransitionBlocked`, which I read):
- `HORIZONTAL(r,c)`: for `x in {c, c+1}` — clear `DOWN` of `cell(r,x)` and `UP` of `cell(r+1,x)`.
- `VERTICAL(r,c)`: for `y in {r, r+1}` — clear `RIGHT` of `cell(y,c)` and `LEFT` of `cell(y,c+1)`.

**Wall → posts:**
- `HORIZONTAL(r,c)`: `endA = post(r+1, c)`, `centre = post(r+1, c+1)`, `endB = post(r+1, c+2)`
- `VERTICAL(r,c)`: `endA = post(r, c+1)`, `centre = post(r+1, c+1)`, `endB = post(r+2, c+1)`

Both orientations at the same `(row, column)` share the centre post — which is exactly why `WallValidator.isStructurallyValid` rejects that pair. Use it as a self-check when writing the table.

**`conflictMask[slot]: LongArray(2)`** — a literal transcription of `WallValidator.isStructurallyValid` (`WallValidator.kt:39-50`): the slot itself, the opposite-orientation slot at the same `(row, column)`, and for `HORIZONTAL` the same-orientation slots at `(row, column±1)`, for `VERTICAL` at `(row±1, column)`. Structural legality is then `(conflictMask[s][0] and occupied[0]) == 0L && (conflictMask[s][1] and occupied[1]) == 0L`.

**`slotNeighbours[slot]: IntArray`** — every other slot sharing at least one post. Used for extension-wall generation.

### 4.2 `FastBoard`

```kotlin
internal class FastBoard(private val zobrist: Zobrist) {
    val open = IntArray(81)
    val occupied = LongArray(2)
    val postTouch = IntArray(100)
    val pawn = IntArray(2)
    val reserve = IntArray(2)
    var side = 0
    var hash = 0L

    fun loadFrom(state: BoardState)                       // the only bridge from BoardState
    fun makePawn(cell: Int); fun unmakePawn(cell: Int, from: Int)
    fun makeWall(slot: Int);  fun unmakeWall(slot: Int)
    fun pawnMoves(out: IntArray): Int                     // returns the count, ≤ 5
    fun isWallLegal(slot: Int, useAnchorFilter: Boolean): Boolean
    fun goalField(player: Int, out: IntArray)             // multi-source BFS, fills 81 distances
    fun winnerOrNull(): Int                               // -1, 0 or 1
}
```

**`loadFrom` is the only place `BoardState` is read.** It clears `open` to the interior-edge pattern, replays `state.walls` through `makeWall`'s edge-clearing (without touching `reserve`, `side` or `hash`), copies positions and `wallsRemaining`, sets `side = state.currentPlayer.ordinal`, and computes `hash` from scratch. `history` and `turnNumber` are not represented — proven irrelevant in Part 1, defect 8.

**`unmakeWall` restores the four edge bits unconditionally.** This is safe, and the proof is short enough to put in the KDoc: a horizontal wall only ever clears vertical-transition bits and a vertical wall only horizontal ones, so the orientations never overlap; two horizontal walls block the same edge only if they share a row and their column spans `{c,c+1}` intersect, which forces `|c1-c2| ≤ 1` — exactly what `isStructurallyValid` rejects. Symmetric for vertical. Therefore no two legal walls ever block the same edge and there is nothing to reference-count.

**`pawnMoves` is a literal transcription of `MoveValidator.validMoves` (`MoveValidator.kt:13-41`).** Per direction with the edge open: if the adjacent cell is not the opponent, emit it. Otherwise, if the same-direction bit is set in `open[adjacent]`, emit the cell behind; **and only otherwise** try the two perpendicular side-steps, each gated on the corresponding bit in `open[adjacent]` — traversability is checked **from the opponent's cell**, not the mover's. Emit into an `IntArray(5)` with a linear duplicate check (matching the `LinkedHashSet`). Because `open[]` already encodes the board edge, "behind is off-board" and "behind is walled" collapse into the same test, which is what `offsetOrNull` returning null does in the original. The two "only otherwise" and "from the opponent's cell" details are where a transcription realistically goes wrong; the differential test in §5.2 is the gate.

**`isWallLegal(slot, useAnchorFilter)`:**
1. `reserve[side] > 0` (matches `WallValidator.kt:16`)
2. structural: `conflictMask` against `occupied`
3. cut-off: with the anchor filter on, count how many of `endA`, `centre`, `endB` are **anchored** — post row or column equal to 0 or 9, or `postTouch[post] > 0`. If fewer than two, return `true` without any BFS. Otherwise apply the wall's edge-clears, run a reachability BFS for each player to its goal row, restore, and return the conjunction.

The soundness argument and the `Wall(7,0,VERTICAL)` + `Wall(6,0,HORIZONTAL)` counterexample from Part 0 both belong in the KDoc, and the counterexample gets its own test (§5.2).

**`goalField(player, out)`** is one multi-source BFS seeded with all nine cells of `goalRow[player]`, expanding over `open`, into a preallocated `IntArray(81)`. It yields the whole distance field, which the evaluation, the ordering and the path-walking candidate generator all reuse. Pawns do not block — matching `AStarPathFinder` and `BFSValidator`, both of which consult only `walls`.

**Scratch buffers** are shared `IntArray(81)` fields with a monotonic stamp array so nothing is ever cleared. This is safe only because **no BFS ever spans a recursive call** — leaf evaluation, wall-legality and candidate scoring each run to completion before any `negamax` recursion. Say so in a comment; it is the one place a later edit can introduce a baffling bug. Per-ply buffers (`Array(SEARCH_MAX_PLY) { IntArray(64) }` for moves and keys) are allocated once.

### 4.3 Transposition table

Two parallel `LongArray(1 shl sizeLog2)`. The **full 64-bit key is stored and compared**, so a collision needs a genuine hash collision, not an index collision.

```
ttData layout:  score  bits  0..31   (Int, two's complement)
                depth  bits 32..39
                flag   bits 40..41   (0 EXACT, 1 LOWER, 2 UPPER)
                move   bits 42..50
                gen    bits 51..58
```

Zobrist components, drawn once from `Random(config.zobristSeed)`: `pawn[2][81]`, `wall[128]`, `reserve[2][11]`, `side`. **`reserve` must be hashed** — identical geometry with different wall reserves is a different position. `history` and `turnNumber` are deliberately not hashed; there is no repetition rule in this implementation, so `(pawns, walls, reserves, side)` fully determines the position. That alone turns the table from "never hits" into a real node saving.

Store flag from the window **as it entered the node**, never the raised alpha:

```kotlin
val flag = when {
    best <= alphaIn -> TT_UPPER
    best >= beta    -> TT_LOWER
    else            -> TT_EXACT
}
```

Mate-score normalisation on the way in and out, because the stored ply differs from the probing ply:

```kotlin
private fun toTt(s: Int, ply: Int)   = if (s > MATE_THRESHOLD) s + ply else if (s < -MATE_THRESHOLD) s - ply else s
private fun fromTt(s: Int, ply: Int) = if (s > MATE_THRESHOLD) s - ply else if (s < -MATE_THRESHOLD) s + ply else s
```

Replacement: overwrite if `gen != currentGeneration || depth >= storedDepth`. `currentGeneration` increments per `chooseAction`; entries stay valid across turns because the key is complete.

### 4.4 Search

Single negamax path, score always from the side to move. This is what makes the bound flags meaningful — the current split max/min branches sharing one alpha/beta pair are precisely where the audited bound bug lives.

```kotlin
private fun negamax(depth: Int, ply: Int, alphaIn: Int, betaIn: Int): Int {
    if (aborted) return 0
    nodes++
    if (nodes >= config.maxNodes) { aborted = true; return 0 }
    if ((nodes and SEARCH_NODE_POLL_MASK.toLong()) == 0L && clock.nanoTime() >= deadline) { aborted = true; return 0 }

    board.winnerOrNull().let { w -> if (w >= 0) return if (w == board.side) WIN - ply else -(WIN - ply) }

    var alpha = maxOf(alphaIn, -WIN + ply)          // mate-distance pruning
    val beta  = minOf(betaIn,   WIN - ply - 1)
    if (alpha >= beta) return alpha
    if (depth <= 0 || ply >= SEARCH_MAX_PLY - 1) return evaluate(ply)
    ...
}
```

The node counter is checked every node (a local comparison, free) so the harness's node budget is exact; the clock is polled every 1024 nodes, which at ~9 µs/node bounds the overshoot at ~9 ms against a 1200 ms ceiling.

TT probe: read the move at **any** stored depth for ordering; cut only when `ply > 0 && storedDepth >= depth`. The root never takes a cut, so it always produces a move.

**PVS.** First move full window; the rest a null window with the reduction undone on fail-high and a full re-search inside the window:

```kotlin
score = if (moveIndex == 0) -negamax(depth - 1, ply + 1, -beta, -alpha)
        else {
            var s = -negamax(depth - 1 - r, ply + 1, -alpha - 1, -alpha)
            if (s > alpha && r > 0)    s = -negamax(depth - 1, ply + 1, -alpha - 1, -alpha)
            if (s > alpha && s < beta) s = -negamax(depth - 1, ply + 1, -beta, -alpha)
            s
        }
```

**LMR, walls only** (`config.useLateMoveReductions`, on for EXPERT, off for HARD):

```kotlin
val r = if (config.useLateMoveReductions &&
            depth >= LMR_MIN_DEPTH && moveIndex >= LMR_MIN_MOVE_INDEX && move >= 128 &&
            move != ttMove && move != killers[ply][0] && move != killers[ply][1] &&
            opponentDistanceAtNode > LMR_SAFE_OPPONENT_DISTANCE) 1 else 0
```

Never reduce a pawn move — in a tempo race the straight advance is never quiet. Every fail-high triggers the un-reduced re-search, so LMR can delay finding a tactic but cannot lose one.

**No null move.** There is no pass in Quoridor and the game is a tempo race, so handing the opponent a free step is not "doing nothing badly" — in a race it *is* the whole evaluation. Unsound here.

**No quiescence.** There are no captures, so there is nothing to extend on. Three things bound the horizon risk instead: the evaluation is a shortest-path difference and is already quiet; the race term makes odd- and even-ply leaves commensurable so iterative deepening does not oscillate; and only completed iterations (or fully-searched root children) are accepted.

**Root** (`chooseAction`):

```kotlin
@Synchronized
override fun chooseAction(state: BoardState, playerId: PlayerId): GameAction {
    require(state.currentPlayer == playerId)
    ensureTablesAllocated()
    board.loadFrom(state)
    generation++; ageHistory(); clearKillers()
    aborted = false; nodes = 0
    val start = clock.nanoTime()
    deadline = start + config.hardBudgetMillis * 1_000_000L
    val softDeadline = start + config.softBudgetMillis * 1_000_000L

    val count = generateRoot()                                   // pawn moves + capped walls
    firstWinningPawnMove()?.let { return decode(it) }            // no search needed

    var best = rootMoves[0]; var previous = 0; var stable = 0
    for (depth in 1..config.maxDepth) {
        val score = searchRoot(depth)
        if (rootCompleted > 0) best = rootBest                   // keep partial progress
        if (aborted) break
        if (rootBest == best && kotlin.math.abs(score - previous) < STABLE_SCORE_WINDOW) stable++ else stable = 0
        previous = score
        if (kotlin.math.abs(score) >= MATE_THRESHOLD) break
        if (stable >= STABLE_ITERATIONS_TO_STOP &&
            clock.nanoTime() - start >= MIN_THINK_MILLIS * 1_000_000L) break
        if (clock.nanoTime() >= softDeadline) break
    }
    val action = decode(best)
    return if (gameEngine.perform(state, action) is ActionResult.Success) action else safeFallback(state, playerId)
}
```

Four things this fixes that the current code gets wrong:

- **Partial iterations are not thrown away.** `searchRoot` writes `rootBest` after each *fully searched* root child; on abort, a child that completed and beat the previous iteration's score is kept. Today's `catch (_: SearchTimedOut) { break }` (lines 43-45) discards everything, so the whole budget can be spent for nothing.
- **Root ordering across iterations** is the previous iteration's root scores, best first — a single sort of ≤20 entries per iteration. This is what makes iterative deepening pay for itself.
- **The soft deadline** stops a new iteration that cannot finish. With effective branching ~4.5 each iteration costs roughly 4× the last, so past ~58% of the budget the next one certainly overshoots.
- **`safeFallback`** is `actionGenerator.pawnActions(state).minByOrNull { pathFinder.distance(it.target, playerId.goalRow, state.walls) }`. One `perform` per turn, microseconds, and it makes an illegal action structurally impossible even if `FastBoard` has a bug — a divergence can cost strength, never legality.

`@Synchronized` is mandatory, not decorative: `GameViewModel.runAiTurn` (line 356-364) cancels `aiJob` and immediately relaunches, and cancelling a coroutine does not interrupt a non-suspending CPU loop, so two `Dispatchers.Default` threads can enter the engine at once. Without the lock the mutable board corrupts. The stale call retires within its own deadline.

### 4.5 Evaluation

Everything from the side to move. Both distance fields come from `goalField`, one BFS per player per leaf.

```
dMe, dYou   = goalField[me][pawn[me]], goalField[you][pawn[you]]
race        = 2 * dYou - 2 * dMe + 1        // always odd; race >= 1  ⟺  I win the pure foot race
```

`race` is exact arithmetic, not a heuristic: with me to move I finish on ply `2*dMe - 1` and you on ply `2*dYou`. That asymmetry *is* the tempo, and putting it inside the number is why the rest falls out. Work the one-step wall: before, `race = 2*dYou - 2*dMe + 1`; after a wall costing you one step, it is your move, so `race = 2*(dYou+1) - 2*dMe - 1` — **identical**. Tempo-neutral, and I am a wall poorer. A two-step wall gains exactly `+2` plies. *A wall must cost the opponent two steps to be worth playing* is not a rule in this engine; it is what the score says.

```kotlin
private fun evaluate(ply: Int): Int {
    val me = board.side; val you = 1 - me
    board.goalField(me, fieldMe); board.goalField(you, fieldYou)
    val dMe = fieldMe[board.pawn[me]]; val dYou = fieldYou[board.pawn[you]]
    val race = 2 * dYou - 2 * dMe + 1
    val rMe = board.reserve[me]; val rYou = board.reserve[you]

    // Proven results. Sound because BoardGraph consults only walls: with the opponent holding
    // no walls, my distance can never grow. The margin of 5 plies (two full steps of slack)
    // absorbs up to two tempi lost to pawn contact at the meeting point.
    if (rYou == 0 && race >=  PROVEN_RACE_MARGIN) return  PROVEN_WIN - 2 * dMe  - ply
    if (rMe  == 0 && race <= -PROVEN_RACE_MARGIN) return -(PROVEN_WIN - 2 * dYou - ply)

    var s = raceTerm(race)
    if (rMe == 0 && rYou == 0) return s      // frozen wall set: the race is the whole story
    s += wallValue(rMe, rYou, dYou) - wallValue(rYou, rMe, dMe)
    s += centreValue(you, dYou) - centreValue(me, dMe)
    s += (progressDirections(board.pawn[me], fieldMe, dMe) -
          progressDirections(board.pawn[you], fieldYou, dYou)) * FREEDOM_VALUE
    return s.coerceIn(-EVAL_CLAMP, EVAL_CLAMP)
}
```

**`raceTerm` — dominant, saturating.**
```kotlin
private fun raceTerm(race: Int): Int {
    val a = abs(race)
    val v = if (a <= RACE_LINEAR_PLIES) a * RACE_PLY_VALUE
            else RACE_LINEAR_PLIES * RACE_PLY_VALUE + (a - RACE_LINEAR_PLIES) * RACE_PLY_VALUE / RACE_TAPER_DIVISOR
    return if (race >= 0) v else -v
}
```
Saturation past four full steps of margin stops the engine falling in love with building a twenty-step labyrinth while its own position rots, and keeps the score range compact.

**`wallValue` — a wall in hand is an option, not progress.**
```kotlin
private fun wallValue(walls: Int, opponentWalls: Int, opponentDistance: Int): Int {
    if (walls == 0) return 0
    val raw = walls * WALL_BASE_VALUE +
        (walls - opponentWalls).coerceIn(-WALL_SURPLUS_CAP, WALL_SURPLUS_CAP) * WALL_SURPLUS_VALUE +
        if (opponentWalls == 0) WALL_LAST_VALUE else 0
    return raw * minOf(opponentDistance, WALL_RELEVANCE_DISTANCE) / WALL_RELEVANCE_DISTANCE
}
```
`WALL_BASE_VALUE = 70` must sit strictly between 0 and 200 (one step): above 200 the bot hoards and never spends, at 0 it dumps walls on neutral placements. Check the thresholds it produces — a 2-step wall scores `+200 - 70 = +130` (played), a 1-step wall `0 - 70 = -70` (refused), a 3-step wall `+330` (played eagerly). Those are the decisions a strong player makes. Today's `WALL_COUNT_WEIGHT = 3` against `OWN_DISTANCE_WEIGHT = 18` prices a wall at one sixth of a step, which is why the bot dumps its walls. The surplus term is above the base because in the middlegame wall *difference* beats wall *count* — holding two more means you get the last word — clipped at ±3 because after three you have all the answers you need. The relevance taper says the true maxim: walls are worth less **against a pawn that is nearly home**, not merely "later".

**`centreValue`** — `CENTRE_VALUE * abs(column - 4) * min(d, CENTRE_RELEVANCE_DISTANCE) / CENTRE_RELEVANCE_DISTANCE`, subtracted. Maximum ~48, sub-step by construction, so it only bites when the race is dead level — which is exactly the first six moves, where something has to break the tie and today's engine breaks it in favour of a wall.

**`progressDirections`** — the count of open neighbours whose goal-distance is strictly less than the pawn's. On an open board this is always 1 for both players, so the term is dormant in the opening; it fires only when walls make one player's route fragile (a single-file corridor, where one wall costs many steps) versus redundant. Free, since the field is already in hand. Weight 14.

**Deleted outright: `IMMEDIATE_THREAT_WEIGHT`.** A turn-blind ±2000 cliff inside an evaluation whose other terms span ~±200. It fires when either player is one step from goal regardless of whose move it is, and the search then spends its whole budget chasing the cliff over the horizon — visible in play as panic-walling. Distance-1 positions are one ply from terminal; `WIN - ply` and the race term handle them correctly and continuously.

**Score ladder** — `WIN (1_000_000) - ply` for a real terminal, `PROVEN_WIN (900_000) - 2*d - ply` for a proven race, `MATE_THRESHOLD = 800_000` as the normalisation boundary, heuristic evaluation clamped to `EVAL_CLAMP = 60_000`. A heuristic score can never be mistaken for a proven one, and a proven one never for a real mate.

### 4.6 Move generation and ordering

**Hard generation rules — Quoridor knowledge as code, not as weights:**
- `reserve[side] == 0` → pawn moves only.
- `reserve[opponent] == 0 && race >= RUN_RACE_MARGIN (3)` → pawn moves only, at **every** node. I am winning the foot race and they cannot lengthen my path, so every wall I place hands over a free tempo. This is "why chasing with walls loses to somebody who just runs", turned into a rule. The proven-win branch usually fires first; this covers `race == 3`, inside the proven margin.
- Never generate a wall that increases my own distance unless it increases the opponent's by strictly more (applied at ply ≤ `WALL_SCORED_MAX_PLY`, where the deltas are known).

**Wall candidate sources**, unioned into a `LongArray(2)` seen-mask so dedup is free:

1. **Opponent path blockers** — walk the opponent's shortest path downhill through `fieldYou`, first `WALL_PATH_EDGES_OPPONENT (5)` edges; for each edge the ≤2 slots that block it. (Truncated at 5 because a wall six squares down a path is routed around long before it is reached, and the path is recomputed every ply.)
2. **Own path blockers** — the same over `fieldMe`, first `WALL_PATH_EDGES_OWN (3)` edges. These are the **slot-denial / prophylactic** moves: occupying a slot the opponent wanted, which by the adjacency rule kills the two collinear slots beside it as well. Today's generator inserts the opponent's path first and then does `.filter{isValid}.take(10)`, so the own-path candidates it appears to generate never actually reach the search — the bot has no defensive wall vocabulary at all.
3. **Extension walls** — for every occupied slot, every entry of `slotNeighbours[slot]` (slots sharing a post). This is where the two-wall staircase lives, and the second wall of a trap frequently is *not* on the current shortest path, because the point of the first wall was to move the path onto the square the second attacks.
4. **Shoulder walls** — slots with `row in oppRow-2 .. oppRow+1` and `column in oppCol-1 .. oppCol`, both orientations, clipped to `0..7`.

Then: not occupied → structurally legal → cut-off legal (§4.2) → scored → capped.

**Scoring and caps:**
- **ply ≤ `WALL_SCORED_MAX_PLY` (2):** true `gain = 2*(dYouAfter - dYouBefore) - 2*(dMeAfter - dMeBefore)`, in plies, via make/2 BFS/unmake. Drop any candidate with `gain <= 0` unless fewer than four survive. Cap `rootWallCandidates` at ply 0, `shallowWallCandidates` at plies 1-2.
- **ply ≥ 3:** no BFS scoring. Static key = `+STATIC_KEY_OPPONENT_PATH (400)` if the slot blocks an edge of the opponent's current path, `+STATIC_KEY_TOUCHES_WALL (200)` if any of its posts has `postTouch > 0`, `+STATIC_KEY_NEAR_OPPONENT (100)` if within two rows of the opponent pawn, `-STATIC_KEY_OWN_PATH (150)` if it blocks an edge of my own path, plus the history score. Cap `deepWallCandidates`.

Two BFS per candidate at ~40 candidates is ~120 µs — affordable once at the root and across the ~100 nodes of ply 2, ruinous across the ~1000 nodes of ply 3. That is exactly where the line is drawn.

**Ordering inside a node** uses precomputed integer keys in a parallel `IntArray` with **lazy selection** (scan for the maximum remaining, swap it forward), not `sortedWith`. After a cutoff you typically search one to three moves; sorting all of them is waste, and a comparator that calls A* — which is what `HardAI.kt:102` does today — is catastrophic.

| Rank | Key |
|---|---|
| Winning pawn move | `ORDER_WINNING_MOVE` (8_000_000) |
| TT move (any stored depth) | `ORDER_TT_MOVE` (4_000_000) |
| Killer 0 / killer 1 | 3_000_000 / 2_900_000 |
| Pawn move | `ORDER_PAWN_BASE - ORDER_PAWN_DISTANCE_STEP * field[target]`, `+ORDER_JUMP_BONUS` if `field[target] <= d - 2` |
| Wall move | `ORDER_WALL_BASE + gain*1000` (ply ≤ 2) or `ORDER_WALL_BASE + staticKey` (ply ≥ 3) |
| all | `+ min(history[side][move], ORDER_HISTORY_CAP)` |

`field[target]` is a single array read out of the distance field the node already computed — one lookup, not an A* per comparison.

**Pawn moves rank above wall moves.** This inverts today's behaviour, and on its own it is worth more than a ply of depth: the straight advance is the best move in the large majority of Quoridor positions, and searching it first is what makes the cutoff arrive on move 1 instead of move 11. The jump bonus matters because every game has a moment around row 4 where the pawns meet head-on and whoever is to move steals a full step by jumping — the parity moment most club games turn on, and one today's engine has no representation of.

**Killers**: two slots per ply, updated on a beta cutoff, cleared per `chooseAction`. **History**: `IntArray(2 * 256)`, `+= depth * depth` on a cutoff, `-= depth` for quiet moves tried and failed, halved at the start of each `chooseAction` so it carries signal between moves without ossifying.

### 4.7 Configuration and wiring

```kotlin
data class SearchConfig(
    val softBudgetMillis: Long,
    val hardBudgetMillis: Long,
    val maxDepth: Int,
    val maxNodes: Long,
    val rootWallCandidates: Int,
    val shallowWallCandidates: Int,
    val deepWallCandidates: Int,
    val useLateMoveReductions: Boolean,
    val ttSizeLog2: Int,
    val zobristSeed: Long = Constants.Ai.SEARCH_ZOBRIST_SEED,
    val useAnchorFilter: Boolean = true,
) {
    companion object {
        val EXPERT = SearchConfig(
            softBudgetMillis = Constants.Ai.EXPERT_SOFT_BUDGET_MILLIS,
            hardBudgetMillis = Constants.Ai.EXPERT_HARD_BUDGET_MILLIS,
            maxDepth = Constants.Ai.EXPERT_MAX_DEPTH,
            maxNodes = Long.MAX_VALUE,
            rootWallCandidates = Constants.Ai.EXPERT_ROOT_WALL_CANDIDATES,
            shallowWallCandidates = Constants.Ai.EXPERT_SHALLOW_WALL_CANDIDATES,
            deepWallCandidates = Constants.Ai.EXPERT_DEEP_WALL_CANDIDATES,
            useLateMoveReductions = true,
            ttSizeLog2 = Constants.Ai.EXPERT_TT_SIZE_LOG2,
        )
        val HARD = SearchConfig(/* HARD_* constants, useLateMoveReductions = false */)
    }
}

fun interface SearchClock {
    fun nanoTime(): Long
    companion object { val SYSTEM = SearchClock { System.nanoTime() } }
}
```

```kotlin
class SearchAI(
    private val actionGenerator: AIActionGenerator,   // root fallback only
    private val gameEngine: GameEngine,               // root re-validation only
    private val pathFinder: AStarPathFinder,          // root fallback only
    private val config: SearchConfig,
    private val clock: SearchClock,
) : AIEngine
```

No `@Inject` on `SearchAI` — Dagger ignores Kotlin default parameter values and cannot disambiguate two `SearchConfig`s. Add to the existing `AiModule` in `di/AppModule.kt`, following the house `@param:` qualifier style already used at `BillingManager.kt:107`:

```kotlin
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class ExpertEngine
@Qualifier @Retention(AnnotationRetention.BINARY) annotation class HardEngine

// inside object AiModule
@Provides fun provideSearchClock(): SearchClock = SearchClock.SYSTEM

@Provides @ExpertEngine
fun provideExpertAI(g: AIActionGenerator, e: GameEngine, p: AStarPathFinder, c: SearchClock): SearchAI =
    SearchAI(g, e, p, SearchConfig.EXPERT, c)

@Provides @HardEngine
fun provideHardAI(g: AIActionGenerator, e: GameEngine, p: AStarPathFinder, c: SearchClock): SearchAI =
    SearchAI(g, e, p, SearchConfig.HARD, c)
```

Unscoped is deliberate: `AIEngineFactory` is constructor-injected into `GameViewModel`, so this already yields one instance per screen and a fresh one per restart. The 1 MB table is allocated lazily on the first `chooseAction`, so a player who never picks EXPERT never pays for it.

```kotlin
class AIEngineFactory @Inject constructor(
    private val easyAI: EasyAI,
    private val mediumAI: MediumAI,
    @param:HardEngine private val hardAI: SearchAI,
    @param:ExpertEngine private val expertAI: SearchAI,
) {
    fun forDifficulty(difficulty: Difficulty): AIEngine = when (difficulty) {
        Difficulty.EASY -> easyAI
        Difficulty.MEDIUM -> mediumAI
        Difficulty.HARD -> hardAI
        Difficulty.EXPERT -> expertAI
    }
}
```

### 4.8 `Constants.Ai` in full

Replace the whole object. House style: a KDoc line on anything whose value is a judgement.

```kotlin
object Ai {
    // ---- EASY / MEDIUM (unchanged behaviour) ----
    const val EASY_WALL_PROBABILITY = 0.22
    const val MEDIUM_MAX_WALL_CANDIDATES = 20
    const val MEDIUM_WALL_THRESHOLD = 2
    const val OWN_DISTANCE_WEIGHT = 18
    const val OPPONENT_DISTANCE_WEIGHT = 20

    // ---- shared search engine (HARD and EXPERT) ----
    const val SEARCH_WIN_SCORE = 1_000_000
    /** A race the opponent provably cannot interfere with. Below a real mate, above any heuristic. */
    const val SEARCH_PROVEN_WIN_SCORE = 900_000
    const val SEARCH_MATE_THRESHOLD = 800_000
    const val SEARCH_EVAL_CLAMP = 60_000
    const val SEARCH_MAX_PLY = 32
    /** Poll the clock every 1024 nodes: ~9 ms of overshoot against a 1.2 s ceiling, and no syscall in the inner loop. */
    const val SEARCH_NODE_POLL_MASK = 1_023
    const val SEARCH_ZOBRIST_SEED = 0x51D00DL

    // ---- evaluation, measured in plies-to-goal ----
    /** One ply of race advantage. One full step of shortest path is therefore 200. */
    const val RACE_PLY_VALUE = 100
    const val RACE_LINEAR_PLIES = 8
    const val RACE_TAPER_DIVISOR = 2
    /** A wall in hand, ~0.35 of a step: enough that a one-step wall is refused, not enough to hoard. */
    const val WALL_BASE_VALUE = 70
    /** Wall difference beats wall count in the middlegame — holding more means getting the last word. */
    const val WALL_SURPLUS_VALUE = 90
    const val WALL_SURPLUS_CAP = 3
    const val WALL_LAST_VALUE = 40
    /** Walls are worth less against a pawn that is nearly home, not merely "later". */
    const val WALL_RELEVANCE_DISTANCE = 8
    const val CENTRE_VALUE = 12
    const val CENTRE_RELEVANCE_DISTANCE = 6
    /** A pawn with one progress direction is in a corridor; one wall then costs it many steps. */
    const val FREEDOM_VALUE = 14
    /** Two full steps of slack, which absorbs up to two tempi lost to pawn contact at the meeting point. */
    const val PROVEN_RACE_MARGIN = 5
    const val RUN_RACE_MARGIN = 3

    // ---- ordering ----
    const val ORDER_WINNING_MOVE = 8_000_000
    const val ORDER_TT_MOVE = 4_000_000
    const val ORDER_KILLER_PRIMARY = 3_000_000
    const val ORDER_KILLER_SECONDARY = 2_900_000
    const val ORDER_PAWN_BASE = 1_000_000
    const val ORDER_PAWN_DISTANCE_STEP = 1_000
    const val ORDER_JUMP_BONUS = 5_000
    const val ORDER_WALL_BASE = 100_000
    const val ORDER_HISTORY_CAP = 900

    // ---- wall candidate generation ----
    const val WALL_PATH_EDGES_OPPONENT = 5
    const val WALL_PATH_EDGES_OWN = 3
    /** Above this ply a candidate is ordered by a static key: two BFS per candidate is unaffordable deeper. */
    const val WALL_SCORED_MAX_PLY = 2
    const val STATIC_KEY_OPPONENT_PATH = 400
    const val STATIC_KEY_TOUCHES_WALL = 200
    const val STATIC_KEY_NEAR_OPPONENT = 100
    const val STATIC_KEY_OWN_PATH = 150

    // ---- late move reductions ----
    const val LMR_MIN_DEPTH = 3
    const val LMR_MIN_MOVE_INDEX = 4
    const val LMR_SAFE_OPPONENT_DISTANCE = 2

    // ---- EXPERT budget ----
    /** Past this point a new iteration cannot finish; ~1 s of perceived latency keeps the opponent responsive. */
    const val EXPERT_SOFT_BUDGET_MILLIS = 700L
    /** The tail: cold JIT on the first move, and a low-end handset three times slower than a mid-range one. */
    const val EXPERT_HARD_BUDGET_MILLIS = 1_200L
    const val EXPERT_MAX_DEPTH = 20
    const val EXPERT_ROOT_WALL_CANDIDATES = 16
    const val EXPERT_SHALLOW_WALL_CANDIDATES = 10
    const val EXPERT_DEEP_WALL_CANDIDATES = 6
    const val EXPERT_TT_SIZE_LOG2 = 16

    // ---- HARD budget: same engine, shallower ----
    const val HARD_SOFT_BUDGET_MILLIS = 200L
    const val HARD_HARD_BUDGET_MILLIS = 350L
    const val HARD_MAX_DEPTH = 4
    const val HARD_ROOT_WALL_CANDIDATES = 8
    const val HARD_SHALLOW_WALL_CANDIDATES = 6
    const val HARD_DEEP_WALL_CANDIDATES = 4
    const val HARD_TT_SIZE_LOG2 = 14

    // ---- adaptive spend ----
    const val STABLE_ITERATIONS_TO_STOP = 3
    const val STABLE_SCORE_WINDOW = 50
    /** A reply that lands in 20 ms reads as careless from something labelled "Uzman". */
    const val MIN_THINK_MILLIS = 150L
}
```

Removed: `HARD_TIME_BUDGET_MILLIS`, `HARD_MAX_WALL_CANDIDATES`, `TERMINAL_SCORE`, `WALL_COUNT_WEIGHT`, `IMMEDIATE_THREAT_WEIGHT` (all `HardAI`-only). `OWN_/OPPONENT_DISTANCE_WEIGHT` stay because `MediumAI.kt:30-31` uses them.

---

## Part 5 — Tests

### 5.1 `AIEngineTest.kt` (edited)

Replace the two `HardAI(...)` constructions with `SearchAI(generator, TestFixtures.engine, TestFixtures.aStar, SearchConfig.HARD, SearchClock.SYSTEM)`. Add the same two tests for `SearchConfig.EXPERT` with `maxNodes = 3_000` and `SearchClock { 0L }` (a constant-zero clock never expires, so the node cap and `maxDepth` are the only stopping conditions and the result is bit-identical on every machine).

### 5.2 `FastBoardEquivalenceTest.kt` — the primary correctness gate

Random legal positions from `Random(20260808L)`: 0-20 walls placed one at a time through `WallValidator.isValid`, random distinct pawn cells, both seats to move. All comparisons against the real validators.

| Test | Sample | Assertion | Budget |
|---|---|---|---|
| `pawnMovesMatchMoveValidator` | 3000 positions | `FastBoard.pawnMoves()` decoded to `Set<Position>` equals `MoveValidator.validMoves(state)` | ~1 s |
| `wallStructuralLegalityMatchesValidator` | 300 positions × 128 slots | equals `WallValidator.isStructurallyValid(state.walls, wall)` | ~0.3 s |
| `wallLegalityMatchesWallValidator` | 400 positions × 128 slots, `useAnchorFilter = true` | equals `WallValidator.isValid(state, wall)` | ~2.5 s |
| `wallLegalityMatchesWithAnchorFilterDisabled` | 150 positions × 128 slots, `useAnchorFilter = false` | same — proves the filter is the only difference | ~1 s |
| `anchorFilterCatchesHalfWallBarrier` | one hand-built position | `walls = {Wall(7,0,VERTICAL)}`, `PLAYER_ONE` pawn at `(7,0)`; assert `isWallLegal(slot(Wall(6,0,HORIZONTAL)))` is `false` and equals `WallValidator.isValid` | instant |
| `goalFieldMatchesAStar` | 1000 positions | `goalField(p)[pawn[p]] == AStarPathFinder.distance(...)`, both players | ~0.5 s |
| `makeUnmakeIsExactlyReversible` | 500 random sequences of 12 make/unmake | `open`, `occupied`, `postTouch`, `pawn`, `reserve`, `side`, `hash` all bit-identical to the snapshot | ~0.2 s |

`anchorFilterCatchesHalfWallBarrier` is the direct regression test for the flaw in Design A's filter; without it, a plausible-looking rewrite of the anchor logic silently makes the bot search illegal moves.

### 5.3 `SearchAITacticsTest.kt` — hand-set positions, each naming one piece of knowledge

All run with `SearchClock { 0L }` and a fixed `maxDepth` (4-6) so they are deterministic and take milliseconds. Twelve positions:

1. `takesImmediateWin` — one step from goal, plays it.
2. `prefersWinInOneOverWinInThree` — regression test for ply-relative mate scores.
3. `refusesOneStepWall` — a wall that delays the opponent exactly one step is available; assert the engine advances instead. *This is the audited pathology, tested directly.*
4. `playsTwoStepWall` — a wall costing two steps available and clearly best; assert it plays it.
5. `runsWhenOpponentHasNoWalls` — opponent reserve 0, `race >= 5`; assert a pawn move, and assert `chooseAction` returns in under 5 ms (the proven branch fires at the root).
6. `doesNotOpenWithAWall` — initial position; assert a `MovePawn`.
7. `blocksForcedLoss` — exactly one wall stops an unanswerable run; assert that wall.
8. `jumpsAtTheMeetingPoint` — pawns head-on, jump clears two rows; assert the jump.
9. `playsSlotDenial` — the opponent threatens to seal my corridor and one zero-cost own-path wall denies it; assert that wall. (This is a class today's generator structurally cannot produce.)
10. `findsTheSecondWallOfAStaircase` — first wall down, opponent routed around; assert the extension wall.
11. `spendsLastWallWhenItWinsTheRace` — both at one wall, spending wins by one, keeping loses by one.
12. `neverReturnsAnIllegalAction` — 200 seeded positions, assert `gameEngine.perform` returns `Success` every time.

### 5.4 Strength harness — `StrengthHarness.kt`

The audit's design, adopted essentially intact. It is the best-argued part of the inputs and I am not going to relitigate it.

- **Drive engines by nodes, not milliseconds.** `SearchConfig.maxNodes` is the stopping condition in tests; `hardBudgetMillis` is set enormous and, if it ever fires, the test **fails and says so**. A wall-clock budget makes the same seed produce a different search on a loaded CI box.
- **Seeded opening book.** Both engines are deterministic, so "play ten games" plays one game ten times. From a single `Random(20260808L)`, play 2-6 random *legal* plies from `BoardState.initial()`, with a 25% chance per ply of a random legal wall rather than a step, so openings differ in wall structure and not merely in pawn column. Discard any opening already decided or leaving a player fewer than three steps from goal. Cache the book in a `companion object val`.
- **Paired colour swap.** Each opening is played twice, challenger as `PLAYER_ONE` then as `PLAYER_TWO`. Score 1 / 0.5 / 0.
- **`GameEngine` is the referee.** Every action goes through `GameEngine.perform`; an `ActionResult.Invalid` fails immediately, printing the position, action and reason.
- **200-ply cap**, adjudicated by `AStarPathFinder` remaining distance: strictly shorter wins, equal is a 0.5 draw. Assert fewer than 10% of games hit the cap — a harness where a third of games are adjudicated is measuring shuffling.
- **Failure output**: the seed, a per-opening table (index, colour, winner, plies, adjudicated y/n), and the full move list of the first loss.

`LegacyHardAI.kt` is the current `HardAI.kt` copied verbatim into test sources, with `Constants.Ai.HARD_MAX_DEPTH` and `HARD_TIME_BUDGET_MILLIS` replaced by constructor parameters defaulting to `3` and `850L`. It is frozen: the EXPERT gate is measured against the bot the owner is complaining about, not against whatever HARD becomes later.

### 5.5 `StrengthTest.kt` — thresholds and runtime

> **Superseded on the point of statistics.** The table below computes its tails over the game
> count, treating the two halves of a colour-swapped pair as independent trials. They are not:
> the same position, the same two deterministic engines, the colours exchanged — which is why
> the mirror match scores exactly half by construction. The unit of independence is the
> *opening*. The shipped `StrengthTest.kt` counts openings and applies a sign test with level
> pairs discarded, so its thresholds and sample sizes differ from this table; the file itself is
> the record. Everything else in §5.5 stands.
>
> The measured result the table does not predict: at an **equal** node budget EXPERT and HARD are
> not separable (57.8% over 32 openings, p = 0.133). They are one engine, and HARD's depth cap is
> most of what limits it. The ladder is real at the *shipped* budgets — 3.5:1 — where EXPERT
> scores 30 of 40. The claim this design supports is about the pair of budgets, not about the
> shape of the configuration alone.


Assert one-sided binomial tails against p = 0.5, so each threshold carries its own sample size. Do not assert a raw win rate.

| Test | Match | Threshold | p under H₀ | Est. runtime |
|---|---|---|---|---|
| `mirrorMatchIsExactlyLevel` | EXPERT (1000 nodes) vs an identically-configured EXPERT, 6 openings × 2 | **exactly** 6/12 | — | ~1 s |
| `expertBeatsMediumInSeededMatch` | EXPERT (1000 nodes) vs MEDIUM, 4 openings × 2 | ≥ 8/8 | 0.004 | ~2 s |
| `expertBeatsFrozenHard` | EXPERT (2000 nodes) vs `LegacyHardAI(depth = 3)`, 5 openings × 2 | ≥ 9/10 | 0.011 | ~9 s |
| `hardBeatsMedium` | HARD config (800 nodes) vs MEDIUM, 6 openings × 2 | ≥ 10/12 | 0.019 | ~3 s |
| `mediumBeatsEasy` | MEDIUM vs EASY(`Random(11)`), 6 openings × 2 | ≥ 11/12 | 0.003 | ~2 s |
| `expertNeverLosesToEasy` | EXPERT (800 nodes) vs EASY, 5 openings × 2 | 0 losses | — | ~1 s |
| `everyActionIsLegal`, `fewerThanTenPercentAdjudicated`, `noEngineHitsItsWallClockSafetyNet` | across all of the above | — | — | free |
| `strengthSuiteFinishesUnderBudget` | wraps the file in `System.nanoTime()` | < 45 s | — | — |

`mirrorMatchIsExactlyLevel` is the cheapest high-value test in the file: an engine against a behaviourally identical engine must score **exactly** 50%, not "about 50%". If it fails, the pairing is broken and every other number the harness prints is meaningless.

A heavier variant — 32 openings, 20 000 nodes, EXPERT vs `LegacyHardAI` — lives in the same file behind `Assume.assumeTrue(System.getProperty("strength") != null)`, for whoever is tuning the evaluation weights. It is not in the default run.

**Timing test** (real clock, separate from the strength file): `expertRespectsTimeBudget` — `SearchConfig.EXPERT.copy(softBudgetMillis = 60, hardBudgetMillis = 100)`, 20 positions, assert each `chooseAction` returns within 400 ms. This catches an abort check that does not actually fire.

Total default addition to `gradlew :app:test`: roughly **22-25 s**, of which 18 s is the strength file and 5 s the equivalence file.

---

## Part 6 — Time budget, and what it buys on a phone

**Chosen: `EXPERT_SOFT_BUDGET_MILLIS = 700`, `EXPERT_HARD_BUDGET_MILLIS = 1200`.**

**Why.** Under about one second a bot's reply reads as "it answered"; past about two seconds it reads as "the app has hung" and the user starts tapping. There is also a floor: a reply that lands in 20 ms reads as careless from a tier labelled *Uzman* — a beat of visible thinking is part of what makes an expert tier feel like one, which is what `MIN_THINK_MILLIS = 150` protects. And the search is not the whole latency: `Constants.Animation.PAWN_DURATION_MILLIS = 260` and `WALL_DURATION_MILLIS = 320` run *after* the decision, and `isAiThinking` shows during it, so a 700 ms search reads to the player as roughly a one-second turn. The 1200 ms ceiling exists purely for the tail — the first `chooseAction` of a session runs against cold ART, and a low-end handset can be three times slower than a mid-range one. Iterative deepening is the right insurance: depth 1-3 completes in single-digit milliseconds even interpreted, so a slow device degrades to a shallower but still **complete and sound** search rather than to no answer.

The existing 850 ms is not itself unreasonable; the problem is that it is paired with `HARD_MAX_DEPTH = 3`, so the engine finishes early and simply stops, leaving most of its budget unused.

**Per-move cost on a mid-range phone, derived.** Take a warm-JIT ART core at roughly 3×10⁸ simple integer/array operations per second — about a third of a desktop JVM core on this kind of code.

| Item | Ops | Cost |
|---|---|---|
| One goal-distance BFS (81 cells × ~12 ops) | ~1 000 | **~3 µs** |
| Leaf evaluation (2 BFS + ~40 ops of arithmetic) | | **~6.5 µs** |
| Pawn generation + make/unmake | ~80 | ~0.3 µs |
| Wall generation at ply ≥ 3 (≈300 table lookups, O(1) legality, anchor filter, plus 2 BFS on the ~30% of candidates the filter does not clear) | | **~11 µs** |
| Blended interior node | | ~13 µs |

Leaves outnumber interior nodes by roughly `(b-1)/b`, so the blended figure is **8-10 µs per node**. A 700 ms budget therefore buys **≈ 75 000-90 000 nodes**.

Raw branching is ~4.5 pawn moves plus 6-16 walls, call it 10.5 at depth. Alpha-beta with good ordering approaches √b ≈ 3.2; allowing for imperfect ordering and adding PVS and wall-only LMR, the effective figure is about **4.5**. So `depth ≈ ln(80 000) / ln(4.5) ≈ 7.4`.

**Expected: depth 6-7 in a typical middlegame, depth 5 in dense wall positions where the cut-off BFS dominates, depth 12-14 once both reserves are zero** (walls switched off by rule, branching ~3.5, leaves only 6.5 µs). Against today's ceiling of depth 3.

Depth 6 is not a luxury, it is the entry price: the defining expert tactic in Quoridor is wall → reply → second wall, which is five plies. At depth 3 you cannot set one and you cannot see one coming.

**Realised wall clock**: median 300-500 ms (the stability early-exit fires in quiet positions), p90 ~700 ms, absolute ceiling 1200 ms, near-zero when a win is available at the root or the proven-race branch fires. HARD at its config: 40-120 ms per move.

**This is a cost model, not a device measurement.** Both source designs claimed depth 6-8 on the same kind of arithmetic and neither measured it either. Before anyone repeats the number outside this document, run `chooseAction` twenty times on a real mid-range handset and log `nodes` and elapsed time. If ART handles the BFS worse than assumed, the honest figure is depth 5-6 — and the design degrades gracefully, because iterative deepening always returns the deepest completed result.

---

## Part 7 — Build order, and the residual risks

**Order.** (1) `AIEngineFactory` + `AiModule` + `Constants.Ai` so the project compiles again — nothing can be measured until it does. (2) `WallGeometry`, `Zobrist`, `FastBoard` plus `FastBoardEquivalenceTest` — **do not write a line of search until the equivalence tests are green**, because a board that diverges from the rules engine is worse than a weak bot and every later measurement built on it is a lie. (3) `TranspositionTable` and `SearchAI` with plain negamax + ordering, no PVS, no LMR, plus the tactics tests. (4) `LegacyHardAI` and the strength harness; record the baseline. (5) PVS, then LMR, then the stability early-exit, re-running the strength harness after each — if a feature does not move the number, delete it.

**Residual risks, in the order they should worry the implementer.**

1. **`FastBoard` diverging from the rules engine.** Three guards: the 400/3000-position differential tests as a CI gate, the make/unmake reversibility property test, and root re-validation through `GameEngine.perform` with a pawn-move fallback. The worst realistic outcome is lost strength, never an illegal move on the board. The two places it will actually go wrong are the jump rule's *"side-steps only when the straight jump is unavailable, and traversability checked from the opponent's cell"*, and an off-by-one in the wall-to-edge column/row span.
2. **The anchor filter.** The argument is sound with the corrected "two of three posts anchored" condition, but it depends on getting the 10×10 post lattice and the `postTouch` bookkeeping exactly right. It ships behind `useAnchorFilter` with the always-BFS path retained and tested against it. Turning it off costs roughly one ply of depth in the middlegame.
3. **Evaluation weights are informed guesses.** I am confident about the race arithmetic (it is arithmetic), the proven-race branch, and the ordering of magnitudes: race up to ~±1000 after taper, walls up to ~±1000 at full relevance, centre ±48, freedom ±42. If the wall term can outbid a full step, you have built a hoarder. The two least certain numbers are `FREEDOM_VALUE = 14` and `RACE_LINEAR_PLIES = 8`; both are isolated named constants and the gated 32-opening harness exists to tune them offline.
4. **`PROVEN_RACE_MARGIN = 5` is a reasoned bound, not a proof.** It assumes pawn contact at the meeting point cannot cost more than two tempi. Positions inside the margin fall through to the ordinary race term and are resolved by the search's own depth, so the exposure is narrow — but if pawn blocking is ever added to `BoardGraph`, or the jump rule changes, this branch silently starts returning wrong proven-win scores. Test 5 in §5.3 and a comment on `BoardGraph.canTraverse` are the guard.
5. **Wall candidate caps are forward pruning.** The brilliant wall is occasionally on neither path, adjacent to no existing wall, and near neither pawn. Sources 3 and 4 cover most of it; they do not cover all of it. A strong human playing unusual walls will sometimes find a move the engine never considered.
6. **LMR can delay a tactic.** Confined to walls, skipped for the TT move and killers, disabled when the opponent is within two of goal, and every fail-high triggers an un-reduced re-search — so it cannot lose a line permanently, only find it a ply later. Only visible in self-play, never in unit tests.
7. **Concurrency.** `@Synchronized` on `chooseAction` is mandatory, not a nicety. The better long-term fix is a cooperative cancellation token: add `val cancelled: () -> Boolean = { false }` to `SearchConfig`, poll it beside the deadline, and have `GameViewModel` flip an `AtomicBoolean` where it currently calls `aiJob?.cancel()`. Not required at a 700 ms budget; required if the budget is ever raised.
8. **Memory.** 1 MB of transposition table plus ~30 KB of scratch per EXPERT instance, allocated lazily and held for the life of the screen. Negligible on any modern handset, and `ttSizeLog2` drops to 14 (256 KB) if a low-memory profile ever matters.
9. **Not a risk, contrary to both source designs: the UI, resource and statistics wiring for `EXPERT` already exists** — all ten locales including `values-tr` → "Uzman", `GlyphKind.EXPERT`, `KEY_EXPERT_WINS`/`KEY_EXPERT_LOSSES`, and the `winKey`/`lossKey` branches. `Difficulty` is persisted by `.name`, so appending the value was migration-safe. The only compile error in the tree is `AIEngineFactory`.