package proj.memorchess.axl.core.graph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import proj.memorchess.axl.core.data.DataMove
import proj.memorchess.axl.core.data.PositionKey

class TestNavigationHistory {

  private val startPos = PositionKey("start w K")
  private val posA = PositionKey("posA b K")
  private val posB = PositionKey("posB w K")
  private val moveToA = DataMove(startPos, posA, "e4")
  private val moveToB = DataMove(posA, posB, "e5")

  @Test
  fun pushThenBackReturnsOriginalPosition() {
    val nav = NavigationHistory(startPos)
    nav.push(moveToA, posA)
    val result = nav.back()
    assertNotNull(result)
    assertEquals(startPos, nav.current)
  }

  @Test
  fun backOnEmptyReturnsNull() {
    val nav = NavigationHistory(startPos)
    assertNull(nav.back())
    assertEquals(startPos, nav.current)
  }

  @Test
  fun pushThenBackThenForwardReturnsPushedPosition() {
    val nav = NavigationHistory(startPos)
    nav.push(moveToA, posA)
    nav.back()
    val result = nav.forward()
    assertNotNull(result)
    assertEquals(moveToA, result.first)
    assertEquals(posA, result.second)
    assertEquals(posA, nav.current)
  }

  @Test
  fun forwardOnEmptyReturnsNull() {
    val nav = NavigationHistory(startPos)
    assertNull(nav.forward())
  }

  @Test
  fun pushClearsForwardStack() {
    val nav = NavigationHistory(startPos)
    nav.push(moveToA, posA)
    nav.back()
    // Push a different move — should clear forward stack
    val posC = PositionKey("posC b K")
    val moveToC = DataMove(startPos, posC, "d4")
    nav.push(moveToC, posC)
    assertNull(nav.forward())
  }

  @Test
  fun resetClearsBothStacks() {
    val nav = NavigationHistory(startPos)
    nav.push(moveToA, posA)
    nav.push(moveToB, posB)
    nav.back()
    val newStart = PositionKey("newStart w K")
    nav.reset(newStart)
    assertEquals(newStart, nav.current)
    assertNull(nav.back())
    assertNull(nav.forward())
    assertNull(nav.arrivedVia)
  }

  @Test
  fun depthTracksBackStackSize() {
    val nav = NavigationHistory(startPos)
    assertEquals(0, nav.depth)
    nav.push(moveToA, posA)
    assertEquals(1, nav.depth)
    nav.push(moveToB, posB)
    assertEquals(2, nav.depth)
    nav.back()
    assertEquals(1, nav.depth)
  }

  @Test
  fun arrivedViaUpdatedCorrectly() {
    val nav = NavigationHistory(startPos)
    assertNull(nav.arrivedVia)
    nav.push(moveToA, posA)
    assertEquals(moveToA, nav.arrivedVia)
    nav.push(moveToB, posB)
    assertEquals(moveToB, nav.arrivedVia)
    nav.back()
    assertEquals(moveToA, nav.arrivedVia)
    nav.back()
    assertNull(nav.arrivedVia)
  }

  @Test
  fun getBackPathReturnsCorrectPath() {
    val nav = NavigationHistory(startPos)
    nav.push(moveToA, posA)
    nav.push(moveToB, posB)
    val path = nav.getBackPath()
    assertEquals(2, path.size)
    assertEquals(startPos to moveToA, path[0])
    assertEquals(posA to moveToB, path[1])
  }
}
