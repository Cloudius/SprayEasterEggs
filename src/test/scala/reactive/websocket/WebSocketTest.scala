package reactive.websocket

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import reactive.Configuration
import reactive.api.{MainActors, ReactiveApi, RootService}
import reactive.find.{FindActor, FindService}
import reactive.hide.{HideActor, HideService}
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame

import scala.concurrent.blocking
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class WebSocketTest extends FunSuite with MainActors with ReactiveApi {
  implicit lazy val system = ActorSystem("reactive-socket-WebSocketTest")
  sys.addShutdownHook({
    system.shutdown()
  })
  test("websocket connection") {
    val wss = system.actorOf(Props(new RootService[WebSocketServer](new FindService(find).wsroute ~ new HideService(hide).wsroute)), "wswss")
    IO(UHttp) ! Http.Bind(wss, Configuration.host, Configuration.portWs)
    blocking(Thread.sleep((2 second).toMillis)) // wait for all servers to be cleanly started
    find ! FindActor.Clear
    hide ! HideActor.Clear
    blocking(Thread.sleep((1 second).toMillis))
    var wsmsg = ""
    val wsf = system.actorOf(Props(new TestingWebSocketClient {
      override def businessLogic = {
        case WebSocket.Release => close()
        case TextFrame(msg)    => wsmsg = msg.utf8String
        case whatever          => // ignore
      }
    }))
    wsf ! WebSocket.Connect(Configuration.host, Configuration.portWs, "/find/ws")
    val wsh = system.actorOf(Props(new TestingWebSocketClient {
      override def businessLogic = {
        case WebSocket.Send(message) =>
          log.info("Client sending message {}", message)
          send(message)
        case WebSocket.Release       => close()
        case whatever                => // ignore
      }
    }))
    wsh ! WebSocket.Connect(Configuration.host, Configuration.portWs, "/hide/ws")
    blocking(Thread.sleep((2 second).toMillis)) // wait for all servers to be cleanly started
    wsh ! WebSocket.Send("2.1523721 41.4140567")
    blocking(Thread.sleep((1 second).toMillis))
    val first = """\{"move":\{"id":"[-0-9a-f]+","idx":"0","longitude":2\.1523721,"latitude":41\.4140567\}\}""".r
    assert(None != first.findFirstIn(wsmsg))
    blocking(Thread.sleep((1 second).toMillis))
    wsh ! WebSocket.Send("-38.4798 -3.8093")
    blocking(Thread.sleep((1 second).toMillis))
    val second = """\{"move":\{"id":"[-0-9a-f]+","idx":"0","longitude":-38\.4798,"latitude":-3\.8093\}\}""".r
    assert(None != second.findFirstIn(wsmsg))
    blocking(Thread.sleep((1 second).toMillis))
    wsh ! WebSocket.Release
    blocking(Thread.sleep((1 second).toMillis))
    val clear = """\{"clear":\{"id":"[-0-9a-f]+","idx":"0"\}\}""".r
    assert(None != clear.findFirstIn(wsmsg))
    wsf ! WebSocket.Release
    blocking(Thread.sleep((1 second).toMillis))
    IO(UHttp) ! Http.Unbind
    system.shutdown()
    blocking(Thread.sleep((1 second).toMillis))
  }
}
