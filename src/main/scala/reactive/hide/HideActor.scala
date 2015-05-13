package reactive.hide

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import reactive.websocket.WebSocket

import scala.collection.mutable

sealed trait HideMessage

case object Clear extends HideMessage

case class Unregister(ws: WebSocket) extends HideMessage

class HideActor extends Actor with ActorLogging {
  val bunny = context.actorOf(Props[MarkerActor])
  bunny ! reactive.hide.Start(null, "B")
  val markers = mutable.Map[WebSocket, ActorRef]()

  override def receive = {
    case WebSocket.Open(ws)                =>
      val idx = (markers.size % 10).toString
      val marker = context.actorOf(Props(classOf[MarkerActor]))
      markers += ((ws, marker))
      log.debug("registered marker {}", idx)
      marker ! reactive.hide.Start(ws, idx)
    case WebSocket.Close(ws, code, reason) =>
      self ! Unregister(ws)
    case WebSocket.Error(ws, ex)           =>
      self ! Unregister(ws)
    case WebSocket.Message(ws, msg)        =>
      val coords = msg.split(" ")
      val lng = coords(0)
      val lat = coords(1)
      log.debug("move marker to ({},{})", lng, lat)
      markers(ws) ! reactive.hide.Move(lng, lat)
    case Clear                             =>
      for (marker <- markers) {
        marker._2 ! reactive.hide.Stop
      }
      markers.clear()
    case Unregister(ws)                    =>
      if (null != ws) {
        log.debug("unregister marker")
        val marker = markers(ws)
        markers remove ws
        marker ! reactive.hide.Stop
      }
    case move@reactive.hide.Move(lng, lat)   =>
      log.debug("move bunny to ({},{})", lng, lat)
      bunny ! move
    case reactive.hide.Stop                  =>
      log.debug("marker {} stopped", sender())
    case whatever                          =>
      log.warning("Hiding '{}'", whatever)
  }
}
