package proj.memorchess.axl.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.test_util.TestWithKoin

class TestKineticMotion : TestWithKoin() {

  @kotlin.test.Test
  fun `celebratory springs match the mockup's exact damping and stiffness`() = test {
    // Act.
    val correctAnswer = KineticMotion.Celebratory.correctAnswer<Float>()
    val streakMilestone = KineticMotion.Celebratory.streakMilestone<Float>()

    // Assert.
    val correctAnswerSpring = correctAnswer.shouldBeInstanceOf<SpringSpec<Float>>()
    correctAnswerSpring.dampingRatio shouldBe 0.4f
    correctAnswerSpring.stiffness shouldBe 500f
    val streakMilestoneSpring = streakMilestone.shouldBeInstanceOf<SpringSpec<Float>>()
    streakMilestoneSpring.dampingRatio shouldBe 0.35f
    streakMilestoneSpring.stiffness shouldBe 400f
  }

  @kotlin.test.Test
  fun `routine springs match the mockup's exact damping and stiffness`() = test {
    // Act.
    val screenTransition = KineticMotion.Routine.screenTransition<Float>()
    val buttonPress = KineticMotion.Routine.buttonPress<Float>()
    val bottomSheet = KineticMotion.Routine.bottomSheet<Float>()
    val iconPop = KineticMotion.Routine.iconPop<Float>()

    // Assert.
    val screenTransitionSpring = screenTransition.shouldBeInstanceOf<SpringSpec<Float>>()
    screenTransitionSpring.dampingRatio shouldBe 0.8f
    screenTransitionSpring.stiffness shouldBe 600f
    val buttonPressSpring = buttonPress.shouldBeInstanceOf<SpringSpec<Float>>()
    buttonPressSpring.dampingRatio shouldBe 0.85f
    buttonPressSpring.stiffness shouldBe 700f
    val bottomSheetSpring = bottomSheet.shouldBeInstanceOf<SpringSpec<Float>>()
    bottomSheetSpring.dampingRatio shouldBe 0.78f
    bottomSheetSpring.stiffness shouldBe 560f
    // iconPop reuses screenTransition's exact spring: the mockup pins only the pop's amplitude.
    val iconPopSpring = iconPop.shouldBeInstanceOf<SpringSpec<Float>>()
    iconPopSpring.dampingRatio shouldBe 0.8f
    iconPopSpring.stiffness shouldBe 600f
  }

  @kotlin.test.Test
  fun `routine tweens land within the mockup's 90 to 300 millisecond window`() = test {
    // Act.
    val wrongAnswer = KineticMotion.Routine.wrongAnswer<Float>()
    val loadingSkeleton = KineticMotion.Routine.loadingSkeleton<Float>()

    // Assert.
    wrongAnswer.shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 120
    loadingSkeleton.shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 300
  }

  @kotlin.test.Test
  fun `reduce motion collapses the correct answer spring to the flat tween`() = test {
    // Arrange.
    REDUCE_MOTION_SETTING.setValue(true)

    // Act.
    val spec = KineticMotion.Celebratory.correctAnswer<Float>()

    // Assert.
    spec.shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 120
  }

  @kotlin.test.Test
  fun `reduce motion collapses the streak milestone spring to the flat tween`() = test {
    // Arrange.
    REDUCE_MOTION_SETTING.setValue(true)

    // Act.
    val spec = KineticMotion.Celebratory.streakMilestone<Float>()

    // Assert.
    spec.shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 120
  }

  @kotlin.test.Test
  fun `reduce motion collapses every routine spring to the flat tween`() = test {
    // Arrange.
    REDUCE_MOTION_SETTING.setValue(true)

    // Act.
    val specs =
      listOf(
        KineticMotion.Routine.screenTransition<Float>(),
        KineticMotion.Routine.buttonPress<Float>(),
        KineticMotion.Routine.bottomSheet<Float>(),
        KineticMotion.Routine.iconPop<Float>(),
      )

    // Assert.
    for (spec in specs) {
      spec.shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 120
    }
  }

  @kotlin.test.Test
  fun `routine tweens are unaffected by reduce motion because they are already flat`() = test {
    // Arrange.
    val before = KineticMotion.Routine.wrongAnswer<Float>().shouldBeInstanceOf<TweenSpec<Float>>()
    REDUCE_MOTION_SETTING.setValue(true)

    // Act.
    val after = KineticMotion.Routine.wrongAnswer<Float>().shouldBeInstanceOf<TweenSpec<Float>>()

    // Assert.
    after.durationMillis shouldBe before.durationMillis
  }

  @kotlin.test.Test
  fun `celebratory spring resolves reduce motion fresh on every call rather than caching`() = test {
    // Arrange.
    KineticMotion.Celebratory.correctAnswer<Float>().shouldBeInstanceOf<SpringSpec<Float>>()

    // Act.
    REDUCE_MOTION_SETTING.setValue(true)
    val afterEnabling = KineticMotion.Celebratory.correctAnswer<Float>()

    // Assert.
    afterEnabling.shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 120
  }

  @kotlin.test.Test
  fun `streak milestone overlay shows by default`() = test {
    // Act & Assert.
    KineticMotion.shouldShowStreakMilestone() shouldBe true
  }

  @kotlin.test.Test
  fun `reduce motion skips the streak milestone overlay entirely`() = test {
    // Arrange.
    REDUCE_MOTION_SETTING.setValue(true)

    // Act & Assert.
    KineticMotion.shouldShowStreakMilestone() shouldBe false
  }

  @kotlin.test.Test
  fun `iconPop plays by default`() = test {
    // Act & Assert.
    KineticMotion.shouldPlayIconPop() shouldBe true
  }

  @kotlin.test.Test
  fun `reduce motion skips the icon pop entirely`() = test {
    // Arrange.
    REDUCE_MOTION_SETTING.setValue(true)

    // Act & Assert.
    KineticMotion.shouldPlayIconPop() shouldBe false
  }

  @kotlin.test.Test
  fun `reduce motion collapses tabEnter to the same plain fade regardless of direction`() = test {
    // Arrange.
    REDUCE_MOTION_SETTING.setValue(true)
    val plainFade = fadeIn(animationSpec = KineticMotion.Routine.screenTransition())

    // Act.
    val fromRight = KineticMotion.tabEnter(fromRight = true)
    val fromLeft = KineticMotion.tabEnter(fromRight = false)

    // Assert.
    fromRight shouldBe plainFade
    fromLeft shouldBe plainFade
  }

  @kotlin.test.Test
  fun `full motion tabEnter also slides, so it differs from the plain fade`() = test {
    // Act.
    val enter = KineticMotion.tabEnter(fromRight = true)

    // Assert.
    enter shouldNotBe fadeIn(animationSpec = KineticMotion.Routine.screenTransition())
  }

  @kotlin.test.Test
  fun `tabExit is always a plain fade, with or without reduce motion`() = test {
    // Act & Assert.
    KineticMotion.tabExit() shouldBe
      fadeOut(animationSpec = KineticMotion.Routine.screenTransition())
    REDUCE_MOTION_SETTING.setValue(true)
    KineticMotion.tabExit() shouldBe
      fadeOut(animationSpec = KineticMotion.Routine.screenTransition())
  }
}
