package de.ioswarm.hyperion.util

object FiniteQueue {

  import scala.collection.mutable

  implicit class FiniteQueue[A](q: mutable.Queue[A]) {
    def enqueueFinite(elem: A, maxSize: Int): Unit = {
      q.enqueue(elem)
      while (q.size > maxSize) {
        println("dequeue")
        q.dequeue()
      }
    }
  }

}
