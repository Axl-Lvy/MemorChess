# UI layer (`ui/`)

Compose Multiplatform UI. `App.kt` is the entry point; `pages/` holds screens, `components/` holds building blocks. DI via Koin, wired in `axl/Koin.kt`.

## There is no coverage safety net here

**`ui/**` is excluded from Sonar coverage** — `@Composable` functions emit synthetic branches JaCoCo can't filter, making uncovered-condition counts meaningless. Coverage gating and uncovered-line reports all skip this folder.

So think through every branch by hand when touching `ui/`: empty/loading/error states, boundary values, every `when` arm, every nullable, every conditional `Modifier`. A happy-path-only sample hides crashes that only fire on edge data (e.g. `Modifier.weight(0f)`). A regression here costs the same as anywhere else — the difference is that nothing catches it for you.
