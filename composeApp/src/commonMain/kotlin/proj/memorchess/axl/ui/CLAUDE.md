# UI layer (`ui/`)

Compose Multiplatform UI. `App.kt` is the entry point, `pages/` holds the screens (now including `Today`, the dashboard the bottom nav's Training tab opens onto), and `components/` holds the building blocks (`board`, `brand`, `buttons`, `controls`, `explore`, `loading`, `navigation`, `popup`, `settings`, `today`, `training`), with `layout/`, `theme/`, and `util/` alongside. DI via Koin, wired one level up in `axl/Koin.kt`.

## There is no coverage safety net here

**`ui/**` is excluded from Sonar coverage** because `@Composable` functions emit synthetic branches that JaCoCo can't filter, which made the uncovered-condition counts meaningless. Coverage gating, new-code coverage thresholds, and uncovered-line reports all skip this folder.

So when writing or modifying anything under `ui/`, deliberately think through every branch: empty/loading/error states, zero and boundary values, every `when` arm, every nullable, every conditional `Modifier`, every state transition. Picking a single happy-path sample (`white=10, draws=5, black=3`) hides the crashes that only fire on edge data like `Modifier.weight(0f)` — hence the edge-case rule in the root `CLAUDE.md`. The cost of a regression here is the same as anywhere else; the difference is that nothing will catch it for you.

Every non-trivial `@Composable` gets KDoc.
