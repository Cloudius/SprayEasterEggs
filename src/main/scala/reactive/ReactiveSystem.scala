package reactive

import reactive.api.{ MainActors, ReactiveApi }
import akka.actor.ActorSystem
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp

object ReactiveSystem extends App with MainActors with ReactiveApi {
  implicit lazy val system = ActorSystem("reactive-system")
  sys.addShutdownHook(system.shutdown() )
  IO(UHttp) ! Http.Bind(wsService, Configuration.host, Configuration.portWs)
  // Since the UTttp extension extends from Http extension, it starts an actor whose name will later collide with the Http extension.
  //system.actorSelection("/user/IO-HTTP") ! PoisonPill

  // We could use IO(UHttp) here instead of killing the "/user/IO-HTTP" actor
  IO(UHttp) ! Http.Bind(rootService, Configuration.host, Configuration.portHttp)
}

object Configuration {
  import com.typesafe.config.ConfigFactory
 
  private val config = ConfigFactory.load
  config.checkValid(ConfigFactory.defaultReference)

  def host = config.getString("easter-eggs.host")
  def portHttp = config.getInt("easter-eggs.ports.http")
  def portWs = config.getInt("easter-eggs.ports.ws")
}
