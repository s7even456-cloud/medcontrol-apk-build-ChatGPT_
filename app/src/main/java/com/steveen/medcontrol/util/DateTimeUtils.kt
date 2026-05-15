package com.steveen.medcontrol.util

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val displayDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d 'de' MMMM yyyy", Locale("es", "ES"))
    private val displayDateShortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale("es", "ES"))
    private val displayDateTimeFormatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "ES"))

    fun todayString(): String = LocalDate.now().format(dateFormatter)
    fun tomorrowString(): String = LocalDate.now().plusDays(1).format(dateFormatter)
    fun dateToString(date: LocalDate): String = date.format(dateFormatter)
    fun parseDate(value: String): LocalDate = LocalDate.parse(value, dateFormatter)
    fun parseTime(value: String): LocalTime = LocalTime.parse(value, timeFormatter)
    fun formatTime(time: LocalTime): String = time.format(timeFormatter)
    fun nowMillis(): Long = System.currentTimeMillis()
    fun displayDate(value: String): String = parseDate(value).format(displayDateFormatter)
    fun displayDateShort(value: String): String = parseDate(value).format(displayDateShortFormatter)
    fun displayMillis(value: Long?): String = value?.let { displayDateTimeFormatter.format(Date(it)) } ?: "—"

    fun nextOccurrenceMillis(time: String, fromMillis: Long = nowMillis()): Pair<String, Long> {
        val zone = ZoneId.systemDefault()
        val from = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(fromMillis), zone)
        val localTime = parseTime(time)
        var candidate = LocalDateTime.of(from.toLocalDate(), localTime)
        if (!candidate.isAfter(from)) candidate = candidate.plusDays(1)
        val millis = candidate.atZone(zone).toInstant().toEpochMilli()
        return candidate.toLocalDate().format(dateFormatter) to millis
    }

    fun millisFor(date: String, time: String): Long {
        val zone = ZoneId.systemDefault()
        return LocalDateTime.of(parseDate(date), parseTime(time)).atZone(zone).toInstant().toEpochMilli()
    }

    fun validTime(value: String): Boolean = runCatching { parseTime(value); true }.getOrDefault(false)
}
