package pars

package object internal {

  /**
   * Dark side casting.
   *
   * This is necessary because the evaluation side don't really know the type information,
   * as this is a runtime representation.
   */
  private[pars] type UnsafePars[F[_]] = Pars[F, Any, Any]

  /**
   * Dark side casting.
   *
   * This is necessary because the evaluation side don't really know the type information,
   * as this is a runtime representation.
   */
  private[pars] type UnsafeChannel = Channel[_]

  private[pars] implicit class UnsafeParsOps[F[_], I, O](pars: Pars[F, I, O]) {
    def toUnsafe: UnsafePars[F] = pars.asInstanceOf[UnsafePars[F]]
  }
}
