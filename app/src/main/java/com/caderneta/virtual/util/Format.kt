package com.caderneta.virtual.util

import android.location.Location
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val ptBR = Locale("pt", "BR")

object Fmt {
    private val timeFmt = SimpleDateFormat("HH:mm", ptBR)
    private val dayKeyFmt = SimpleDateFormat("yyyy-MM-dd", ptBR)
    private val shortDayFmt = SimpleDateFormat("dd/MM", ptBR)
    private val longDayFmt = SimpleDateFormat("EEEE, d 'de' MMMM", ptBR)

    fun time(millis: Long): String = timeFmt.format(Date(millis))
    fun dayKey(millis: Long): String = dayKeyFmt.format(Date(millis))
    fun shortDay(millis: Long): String = shortDayFmt.format(Date(millis))
    fun longDay(millis: Long): String =
        longDayFmt.format(Date(millis)).replaceFirstChar { it.uppercase(ptBR) }

    /** "12,4 km" */
    fun km(meters: Double): String =
        String.format(ptBR, "%.1f km", meters / 1000.0)

    /** Integer odometer with thousands separator, or em dash when null. */
    fun odometer(value: Int?): String =
        value?.let { String.format(ptBR, "%,d", it) } ?: "—"

    /** "01:23:45" or "12:04" depending on length. */
    fun elapsed(millis: Long): String {
        val s = millis / 1000
        val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
        return if (h > 0) String.format(ptBR, "%02d:%02d:%02d", h, m, sec)
        else String.format(ptBR, "%02d:%02d", m, sec)
    }
}

object Geo {
    /** Distance in meters between two lat/lng pairs (Android's WGS84 helper). */
    fun distance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val out = FloatArray(1)
        Location.distanceBetween(lat1, lng1, lat2, lng2, out)
        return out[0].toDouble()
    }
}

/** Whether two epoch millis fall on the same calendar day (local time). */
fun sameDay(a: Long, b: Long): Boolean {
    val ca = Calendar.getInstance().apply { timeInMillis = a }
    val cb = Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
        ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
}
