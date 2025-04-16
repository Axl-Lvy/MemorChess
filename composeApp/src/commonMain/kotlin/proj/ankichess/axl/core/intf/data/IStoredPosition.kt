package proj.ankichess.axl.core.intf.data

interface IStoredPosition {
  val fenRepresentation: String

  fun getAvailableMoveList(): List<String>
}
