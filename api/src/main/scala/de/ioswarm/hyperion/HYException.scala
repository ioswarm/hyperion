package de.ioswarm.hyperion

import de.ioswarm.hyperion.model.LogLevel
import de.ioswarm.time.DateTime


object HYException{

  def apply(message: String): HYException = new HYException(DateTime(), message, null)
  def apply(message: String, level: LogLevel): HYException = new HYException(DateTime(), message, null, level = level)
  def apply(message: String, errorCode: String): HYException = new HYException(DateTime(), message, null, errorCode = Some(errorCode))
  def apply(message: String, errorCode: String, level: LogLevel): HYException = new HYException(DateTime(), message, null, Some(errorCode), level)
  def apply(message: String, cause: Throwable): HYException = new HYException(DateTime(), message, cause)
  def apply(message: String, level: LogLevel, cause: Throwable): HYException = new HYException(DateTime(), message, cause, level = level)
  def apply(message: String, errorCode: String, level: LogLevel, cause: Throwable): HYException = new HYException(DateTime(), message, cause, Some(errorCode), level)

}
class HYException(val timestamp: DateTime, message: String, cause: Throwable, val errorCode: Option[String] = None, val level: LogLevel = LogLevel.ERROR) extends Exception(message, cause)
