package com.enkiba.app.ui.chat

import com.enkiba.app.data.repository.ChatMessage
import com.enkiba.app.ui.components.localDateString
import com.enkiba.app.ui.components.parseIsoDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Éléments de la liste du chat : séparateur de jour ou message. */
sealed interface ChatItem {
    val key: String

    data class Day(val label: String) : ChatItem {
        override val key get() = "day-$label"
    }

    /** [showSender] : premier message d'une suite du même expéditeur (on affiche son nom). */
    data class Message(val message: ChatMessage, val time: String, val showSender: Boolean) : ChatItem {
        override val key get() = message.id
    }
}

/** Mise en forme du fil (sans Android : testée en unitaire). */
object ChatFormat {

    fun items(messages: List<ChatMessage>, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): List<ChatItem> {
        val out = mutableListOf<ChatItem>()
        var lastDay: String? = null
        var lastSender: String? = null
        for (m in messages) {
            val date = parseIsoDate(m.createdAt)
            val day = date?.let { localDateString(it, zone) }
            if (day != null && day != lastDay) {
                out += ChatItem.Day(dayLabel(date, now, zone))
                lastDay = day
                lastSender = null
            }
            val time = date?.let { SimpleDateFormat("HH:mm", Locale.FRENCH).apply { timeZone = zone }.format(it) }.orEmpty()
            out += ChatItem.Message(m, time, showSender = !m.mine && m.senderId != lastSender)
            lastSender = m.senderId
        }
        return out
    }

    /** « Aujourd'hui », « Hier », sinon « lundi 2 novembre ». */
    fun dayLabel(date: Date, now: Date = Date(), zone: TimeZone = TimeZone.getDefault()): String = when (localDateString(date, zone)) {
        localDateString(now, zone) -> "Aujourd'hui"
        localDateString(Date(now.time - 24 * 3600 * 1000L), zone) -> "Hier"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.FRENCH).apply { timeZone = zone }.format(date)
    }
}
