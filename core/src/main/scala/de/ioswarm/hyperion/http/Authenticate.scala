package de.ioswarm.hyperion.http

object Authenticate {

  sealed trait AuthenticationMethod
  case object NONE extends  AuthenticationMethod
  case object BASIC extends AuthenticationMethod
  case object OAUTH2 extends AuthenticationMethod

}
