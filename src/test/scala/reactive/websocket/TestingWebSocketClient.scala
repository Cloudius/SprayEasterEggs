package reactive.websocket

import akka.actor.{Actor, actorRef2Scala}
import akka.io.IO
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.WebSocketClientWorker
import spray.can.websocket.frame.{CloseFrame, StatusCode, TextFrame}
import spray.http.{HttpHeaders, HttpMethods, HttpRequest}

abstract class TestingWebSocketClient extends Actor with WebSocketClientWorker with WebSocket {
  override def receive = connect orElse handshaking orElse closeLogic

  private def connect(): Receive = {
    case WebSocket.Connect(host, port, resource, ssl) =>
      val headers = List(
        HttpHeaders.Host(host, port),
        HttpHeaders.Connection("Upgrade"),
        HttpHeaders.RawHeader("Upgrade", "websocket"),
        HttpHeaders.RawHeader("Sec-WebSocket-Version", "13"),
        HttpHeaders.RawHeader("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw=="))
      request = HttpRequest(HttpMethods.GET, resource, headers)
      IO(UHttp)(context.system) ! Http.Connect(host, port, ssl)
  }

  def send(message: String) = connection ! TextFrame(message)

  def close() = connection ! CloseFrame(StatusCode.NormalClose)

  def path() = uripath

  private var uripath = ""
  private var request: HttpRequest = _

  override def upgradeRequest = request
}
