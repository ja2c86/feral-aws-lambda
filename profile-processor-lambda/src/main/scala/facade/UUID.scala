package facade

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

@js.native
@JSImport("uuid", JSImport.Namespace)
object UUID extends js.Object {
  def v4(): js.UndefOr[String] = js.native
}
