package reactive.websocket

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import reactive.Configuration
import reactive.api.{MainActors, ReactiveApi, RootService}
import reactive.find.FindService
import reactive.hide.HideService
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame

import scala.concurrent.blocking
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class WebSocketTest extends FunSuite with MainActors with ReactiveApi {
  implicit lazy val system = ActorSystem("reactive-socket-WebSocketTest")
  sys.addShutdownHook(system.shutdown())
  test("WebSocket connection") {
    val wss = system.actorOf(Props(new RootService[WebSocketServer](new FindService(find).wsroute ~ new HideService(hide).wsroute)), "wswss")
    IO(UHttp) ! Http.Bind(wss, Configuration.host, Configuration.portWs)
    blocking(Thread.sleep((2 second).toMillis)) // wait for all servers to be cleanly started

    find ! reactive.find.Clear
    hide ! reactive.hide.Clear
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

    def harness(stimulus: (String) => Unit, longi: String, lat: String, responce: => String) = {
      val json =
        """\{"move":\{"id":"[-0-9a-f]{36}","idx":"(\d)","longitude":([-+]?\d+\.\d{4,7}),"latitude":([-+]?\d+\.\d{4,7})\}\}""".r

      stimulus(s"$longi $lat")
      blocking(Thread.sleep((1 second).toMillis))
      responce match {
        case json(idx, lon, lat) if lon == longi && lat == lat => true
        case msg                                               => sys.error("Responce was: " + msg); false
      }
    }

    assert(harness({ s => wsh ! WebSocket.Send(s) }, "2.1523721", "41.4140567", wsmsg))

    assert(harness({ s => wsh ! WebSocket.Send(s) }, "-38.4798", "-3.8093", wsmsg))

    wsh ! WebSocket.Release
    blocking(Thread.sleep((1 second).toMillis))
    val clear = """\{"clear":\{"id":"[-0-9a-f]{36}","idx":"\d"\}\}""".r
    assert(clear.findFirstIn(wsmsg).isDefined)

    wsf ! WebSocket.Release
    blocking(Thread.sleep((1 second).toMillis))
    IO(UHttp) ! Http.Unbind

    system.shutdown()
    system.awaitTermination()
  }
}
