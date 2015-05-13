package reactive.api

import akka.actor.Props
import reactive.find.FindActor
import reactive.hide.HideActor

trait MainActors {
  this: AbstractSystem =>

  lazy val find = system.actorOf(Props[FindActor], "find")
  lazy val hide = system.actorOf(Props[HideActor], "hide")
}
