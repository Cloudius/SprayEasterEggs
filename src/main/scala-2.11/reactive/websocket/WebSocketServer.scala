package reactive
package websocket

import akka.actor.{ActorRef, Cancellable}
import reactive.api.RouteActor
import spray.can.Http
import spray.can.websocket.frame.{CloseFrame, PingFrame, PongFrame, StatusCode, TextFrame}
import spray.can.websocket.{UpgradedToWebSocket, WebSocketServerWorker}
import spray.http.HttpRequest
import spray.routing.{Rejected, RequestContext, Route}

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

class WebSocketServer(val serverConnection: ActorRef, val route: Route) extends RouteActor with WebSocketServerWorker with WebSocket {

  import context.dispatcher

  override lazy val connection = serverConnection

  override def receive = matchRoute(route) orElse handshaking orElse closeLogic

  private def matchRoute(route: Route): Receive = {
    case request: HttpRequest                     =>
      val ctx = RequestContext(request, self, request.uri.path)
      log.debug("HTTP request for uri {}", request.uri.path)
      route(ctx.withResponder(self))
      handshaking(request)
    case reactive.websocket.Register(request, actor, ping) =>
      if (ping) pinger = Some(context.system.scheduler.scheduleOnce(110 seconds, self, reactive.websocket.Ping))
      handler = actor
      uripath = request.uri.path.toString()
      handler ! reactive.websocket.Open(this)
    case Rejected(rejections)                     =>
      log.info("Rejecting with {}", rejections)
      context stop self
  }

  // this is the actor's behavior after the WebSocket handshaking resulted in an upgraded request
  override def businessLogic = {
    case TextFrame(message)  =>
      ping()
      handler ! reactive.websocket.Message(this, message.utf8String)
    case UpgradedToWebSocket =>
    // nothing to do
    case reactive.websocket.Ping             =>
      send(PingFrame())
    case PongFrame(payload)         =>
      ping()
    case Http.Aborted               =>
      handler ! reactive.websocket.Error(this, "aborted")
    case Http.ErrorClosed(cause)    =>
      handler ! reactive.websocket.Error(this, cause)
    case CloseFrame(status, reason) =>
      handler ! reactive.websocket.Close(this, status.code, reason)
    case Http.Closed                =>
      handler ! reactive.websocket.Close(this, StatusCode.NormalClose.code, "")
    case Http.ConfirmedClosed       =>
      handler ! reactive.websocket.Close(this, StatusCode.GoingAway.code, "")
    case Http.PeerClosed            =>
      handler ! reactive.websocket.Close(this, StatusCode.GoingAway.code, "")
    case reactive.websocket.Release          =>
      handler ! reactive.websocket.Close(this, StatusCode.NormalClose.code, "")
    case whatever                   =>
      log.debug("WebSocket received '{}'", whatever)
  }

  def send(message: String) = send(TextFrame(message))

  def close() = send(CloseFrame(StatusCode.NormalClose))

  def path() = uripath

  private def ping(): Unit = pinger match {
    case None        => // nothing to do
    case Some(timer) =>
      if (!timer.isCancelled) timer.cancel()
      pinger = Some(context.system.scheduler.scheduleOnce(110 seconds, self, reactive.websocket.Ping))
  }

  private var uripath = "/"
  private var pinger: Option[Cancellable] = None
  private var handler = self
}
