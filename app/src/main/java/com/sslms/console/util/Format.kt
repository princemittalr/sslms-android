package com.sslms.console.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IN = Locale.forLanguageTag("en-IN")

fun num(v: Number?, digits: Int = 1): String {
    if (v == null) return "—"
    val d = v.toDouble()
    if (d.isNaN()) return "—"
    return NumberFormat.getNumberInstance(IN).apply { maximumFractionDigits = digits; minimumFractionDigits = 0 }.format(d)
}

/** Parses backend timestamps: LocalDateTime, with fraction, or with offset/Z. */
fun parseTime(ts: String?): LocalDateTime? {
    if (ts.isNullOrBlank()) return null
    return runCatching { LocalDateTime.parse(ts) }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(ts).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(ts.replace(' ', 'T')) }.getOrNull()
}

private val DATE_TIME = DateTimeFormatter.ofPattern("dd MMM, HH:mm", Locale.ENGLISH)
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)
private val DAY = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)

fun fmtTime(ts: String?): String = parseTime(ts)?.format(DATE_TIME) ?: (ts ?: "—")
fun fmtClock(t: LocalDateTime): String = t.format(TIME)
fun fmtDay(t: LocalDateTime): String = t.format(DAY)

fun ago(ts: String?): String {
    val t = parseTime(ts) ?: return ""
    val s = Duration.between(t, LocalDateTime.now()).seconds
    return when {
        s < 60 -> "just now"
        s < 3600 -> "${s / 60} min ago"
        s < 86400 -> "${s / 3600} h ago"
        else -> "${s / 86400} d ago"
    }
}

/** "2026-07-01T00:00:00", the LocalDateTime format the backend expects. */
fun toIso(t: LocalDateTime): String = "%04d-%02d-%02dT%02d:%02d:00".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)

fun prettyStatus(s: String?): String = s?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Unknown"

/** Writes a CSV to the cache and opens the share sheet. */
fun shareCsv(context: Context, name: String, header: List<String>, rows: List<List<Any?>>) {
    fun esc(v: Any?): String {
        val s = v?.toString() ?: ""
        return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
    val dir = File(context.cacheDir, "exports").apply { mkdirs() }
    val file = File(dir, name)
    file.writeText((listOf(header) + rows).joinToString("\n") { r -> r.joinToString(",") { esc(it) } })
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
