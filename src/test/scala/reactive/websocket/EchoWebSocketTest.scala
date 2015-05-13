package reactive
package websocket

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import reactive.Configuration
import reactive.api.{MainActors, ReactiveApi, RootService}
import reactive.find.FindActor
import reactive.hide.HideActor
import spray.can.Http
import spray.can.server.UHttp
import spray.can.websocket.frame.TextFrame
import spray.http.StatusCodes

import scala.concurrent.blocking
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

@RunWith(classOf[JUnitRunner])
class EchoWebSocketTest extends FunSuite with MainActors with ReactiveApi {
  implicit lazy val system = ActorSystem("EchoWebSocketTest")
  sys.addShutdownHook(system.shutdown())
  test("WebSocket echo") {
    val wss = system.actorOf(Props(new RootService[WebSocketServer](complete(StatusCodes.NotFound))), "ewss")
    IO(UHttp) ! Http.Bind(wss, Configuration.host, Configuration.portWs)
    blocking(Thread.sleep((2 second).toMillis)) // wait for all servers to be cleanly started
    find ! reactive.find.Clear
    hide ! reactive.hide.Clear
    blocking(Thread.sleep((1 second).toMillis))
    var wsmsg = ""
    val wse = system.actorOf(Props(new TestingWebSocketClient {
      override def businessLogic = {
        case reactive.websocket.Release       => close()
        case TextFrame(msg)          => wsmsg = msg.utf8String
        case reactive.websocket.Send(message) => send(message)
        case whatever                => // ignore
      }
    }))
    wse ! reactive.websocket.Connect("echo.websocket.org", 443, "/echo", withSsl = true)
    blocking(Thread.sleep((2 second).toMillis)) // wait for all servers to be cleanly started
    val rock = "Rock it with HTML5 WebSocket"
    wse ! reactive.websocket.Send(rock)
    blocking(Thread.sleep((2 second).toMillis))
    assert(rock == wsmsg)
    wse ! reactive.websocket.Release
    blocking(Thread.sleep((1 second).toMillis))
    IO(UHttp) ! Http.Unbind
    system.shutdown()
    system.awaitTermination()
  }
}
