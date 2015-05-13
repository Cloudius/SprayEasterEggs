package reactive.hide

import java.util.UUID

import akka.actor.{Actor, ActorLogging}
import reactive.find.FindActor
import reactive.websocket.WebSocket



  sealed trait MarkerMessage
 case object Stop extends MarkerMessage
 case class Start(ws: WebSocket, marker: String) extends MarkerMessage

  case class Move(longitude: String, latitude: String) extends MarkerMessage



class MarkerActor extends Actor with ActorLogging {

  var marker: reactive.find.Marker = _
  var client: WebSocket = _

  override def receive = {
    case Stop           =>
      context.actorSelection("/user/find") ! reactive.find.Clear(marker)
      sender ! Stop
      context stop self
    case Start(ws, idx) =>
      client = ws
      marker = reactive.find.Marker(UUID.randomUUID.toString, idx)
      log.debug("registered marker {} {} websocket under id {}", idx, if (null == client) "without" else "with", marker.id)
      context.actorSelection("/user/find") ! marker
      if (null != client) client.send("OK")
    case Move(lng, lat) =>
      log.debug("move marker {} {} websocket to ({},{})", marker.id, if (null == client) "without" else "with", lng, lat)
      context.actorSelection("/user/find") ! reactive.find.Move(marker, lng, lat)
      if (null != client) client.send("OK")
  }
}
