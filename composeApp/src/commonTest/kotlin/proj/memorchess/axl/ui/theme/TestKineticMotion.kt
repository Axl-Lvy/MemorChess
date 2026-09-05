package proj.memorchess.axl.ui.theme

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import io.kotest.matchers.floats.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import proj.memorchess.axl.core.config.REDUCE_MOTION_SETTING
import proj.memorchess.axl.test_util.TestWithKoin

class TestKineticMotion : TestWithKoin() {

  @kotlin.test.Test
  fun `celebratory springs bounce within the mockup's damping and stiffness ranges`() = test {
    // Act.
    val correctAnswer = KineticMotion.Celebratory.correctAnswer<Float>()
    val streakMilestone = KineticMotion.Celebratory.streakMilestone<Float>()

    // Assert.
    val correctAnswerSpring = correctAnswer.shouldBeInstanceOf<SpringSpec<Float>>()
    correctAnswerSpring.dampingRatio.shouldBeBetween(0.35f, 0.45f, 0f)
    correctAnswerSpring.stiffness.shouldBeBetween(380f, 560f, 0f)
    val streakMilestoneSpring = streakMilestone.shouldBeInstanceOf<SpringSpec<Float>>()
    streakMilestoneSpring.dampingRatio.shouldBeBetween(0.35f, 0.45f, 0f)
    streakMilestoneSpring.stiffness.shouldBeBetween(380f, 560f, 0f)
  }

  @kotlin.test.Test
  fun `routine springs settle with no overshoot within the mockup's ranges`() = test {
    // Act.
    val screenTransition = KineticMotion.Routine.screenTransition<Float>()
    val buttonPress = KineticMotion.Routine.buttonPress<Float>()
    val bottomSheet = KineticMotion.Routine.bottomSheet<Float>()

    // Assert.
    for (spec in listOf(screenTransition, buttonPress, bottomSheet)) {
      val spring = spec.shouldBeInstanceOf<SpringSpec<Float>>()
      spring.dampingRatio.shouldBeBetween(0.78f, 0.85f, 0f)
      spring.stiffness.shouldBeBetween(560f, 700f, 0f)
    }
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
      )

    // Assert.
    for (spec in specs) {
      spec.shouldBeInstanceOf<TweenSpec<Float>>().durationMillis shouldBe 120
    }
  }

  @kotlin.test.Test
  fun `routine tweens are unaffected by reduce motion, they are already flat`() = test {
    // Arrange.
    val before = KineticMotion.Routine.wrongAnswer<Float>().shouldBeInstanceOf<TweenSpec<Float>>()
    REDUCE_MOTION_SETTING.setValue(true)

    // Act.
    val after = KineticMotion.Routine.wrongAnswer<Float>().shouldBeInstanceOf<TweenSpec<Float>>()

    // Assert.
    after.durationMillis shouldBe before.durationMillis
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
}
