# UI layer

`ui/**` is excluded from Sonar coverage, because `@Composable` functions emit synthetic branches JaCoCo cannot filter. Nothing catches a regression here, so check every branch by hand: empty/loading/error states, boundary values, every `when` arm, every nullable, every conditional `Modifier`. Edge data crashes that a happy-path sample hides (e.g. `Modifier.weight(0f)`).
