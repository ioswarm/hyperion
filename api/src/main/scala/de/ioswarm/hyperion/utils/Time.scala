package de.ioswarm.hyperion.utils

import java.time.{Instant, LocalDateTime, ZoneId}
import java.time.temporal.{ChronoField, ChronoUnit}
import java.util.TimeZone

object Time {

  // Dates
  def utcMillis(implicit ldt: LocalDateTime = LocalDateTime.now()): Long = {
    val zdt = ldt.atZone(ZoneId.systemDefault())
    zdt.minus(zdt.getOffset.getTotalSeconds, ChronoUnit.SECONDS).toEpochSecond*1000l+zdt.getLong(ChronoField.MILLI_OF_SECOND)
  }

  def millis(implicit ldt: LocalDateTime = LocalDateTime.now()): Long = {
    val zdt = ldt.atZone(ZoneId.systemDefault())
    zdt.toEpochSecond*1000l+zdt.getLong(ChronoField.MILLI_OF_SECOND)
  }

  def utcSerial(implicit ldt: LocalDateTime = LocalDateTime.now()): Long = {
    val zdt = ldt.atZone(ZoneId.systemDefault())
    val utc = zdt.minus(zdt.getOffset.getTotalSeconds, ChronoUnit.SECONDS)

    utc.getYear*10000000000000l +
      utc.getMonthValue*100000000000l +
      utc.getDayOfMonth*1000000000l +
      utc.getHour*10000000l +
      utc.getMinute*100000l +
      utc.getSecond*1000l +
      utc.getLong(ChronoField.MILLI_OF_SECOND)
  }

  def serial(implicit ldt: LocalDateTime = LocalDateTime.now()): Long = {
    val zdt = ldt.atZone(ZoneId.systemDefault())

    zdt.getYear*10000000000000l +
      zdt.getMonthValue*100000000000l +
      zdt.getDayOfMonth*1000000000l +
      zdt.getHour*10000000l +
      zdt.getMinute*100000l +
      zdt.getSecond*1000l +
      zdt.getLong(ChronoField.MILLI_OF_SECOND)
  }

  implicit class dateLongExtender(l: Long) {

    def toLocalDateTime(implicit fromZone: ZoneId = ZoneId.systemDefault()): LocalDateTime = {
      val offset = TimeZone.getTimeZone(ZoneId.systemDefault()).getOffset(l)-TimeZone.getTimeZone(fromZone).getOffset(l)
      LocalDateTime.ofInstant(
        Instant.ofEpochMilli(l+offset)
        , ZoneId.systemDefault()
      )
    }

    def toUtc: LocalDateTime = toLocalDateTime.toUtc

  }

  implicit class dateLocalDateTimeExtender(ldt: LocalDateTime) {

    def toUtcMillis: Long = utcMillis(ldt)

    def toUtc: LocalDateTime = toUtcMillis.toLocalDateTime

  }

}
