package reactive.find

import akka.actor.{Actor, ActorLogging}
import reactive.websocket.WebSocket

import scala.collection.mutable

sealed trait FindMessage
case object Clear extends FindMessage
case class Unregister(ws: WebSocket) extends FindMessage
case class Marker(id: String, idx: String) extends FindMessage
case class Clear(marker: Marker) extends FindMessage
case class Move(marker: Marker, longitude: String, latitude: String) extends FindMessage

class FindActor extends Actor with ActorLogging {
  val clients = mutable.ListBuffer[WebSocket]()
  val markers = mutable.Map[Marker, Option[Move]]()

  override def receive = {
    case WebSocket.Open(ws) if ws != null  =>
      clients += ws
      for (markerEntry <- markers if None != markerEntry._2)
        ws.send(message(markerEntry._2.get))
      log.debug("registered monitor for url {}", ws.path())
    case WebSocket.Close(ws, code, reason) =>
      self ! Unregister(ws)
    case WebSocket.Error(ws, ex)           =>
      self ! Unregister(ws)
    case WebSocket.Message(ws, msg)        =>
      if (null != ws)
        log.debug("url {} received msg '{}'", ws.path(), msg)
    case Clear                             =>
      for (markerEntry <- markers if None != markerEntry._2) {
        val msg = message(markerEntry._1)
        for (client <- clients) client.send(msg)
      }
      markers.clear()
    case Unregister(ws)                    =>
      if (null != ws) {
        clients -= ws
        log.debug("unregister monitor")
      }
    case Clear(marker)                     =>
      log.debug("clear marker {} '{}'", marker.idx, marker.id)
      val msg = message(marker)
      markers remove marker
      for (client <- clients) client.send(msg)
      log.debug("sent to {} clients to clear marker '{}'", clients.size, msg)
    case marker@Marker(id, idx)            =>
      markers += ((marker, None))
      log.debug("create new marker {} '{}'", idx, id)
    case move@Move(marker, lng, lat)       =>
      markers += ((marker, Some(move)))
      val msg = message(move)
      for (client <- clients) client.send(msg)
      log.debug("sent to {} clients the new move '{}'", clients.size, msg)
    case whatever                          =>
      log.warning("Finding '{}'", whatever)
  }

  private def message(move: Move) = s"""{"move":{"id":"${move.marker.id}","idx":"${move.marker.idx}","longitude":${move.longitude},"latitude":${move.latitude}}}"""

  private def message(marker: Marker) = s"""{"clear":{"id":"${marker.id}","idx":"${marker.idx}"}}"""
}
