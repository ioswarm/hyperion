package de.ioswarm.hyperion

import akka.NotUsed

object Implicits {

  implicit class StringExtender(s: String) {

    def withReceive(serviceReceive: Service.ServiceReceive): Service[NotUsed] = Service.default(s, receive = serviceReceive)
    def withRoute(serviceRoute: Service.ServiceRoute): Service[NotUsed] = Service.default(s, route = serviceRoute)

  }

}
