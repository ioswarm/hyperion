package de.ioswarm

import java.io.{PrintWriter, StringWriter}

package object hyperion {

  implicit class _throwableExtender(t: Throwable) {

    def stackTrace: String = {
      val sw = new StringWriter()
      val pw = new PrintWriter(sw)
      try {
        t.printStackTrace(pw)
        sw.toString
      } finally {
        pw.close()
        sw.close()
      }
    }

  }

}
