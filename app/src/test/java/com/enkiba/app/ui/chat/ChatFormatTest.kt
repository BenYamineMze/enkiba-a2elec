package com.enkiba.app.ui.chat

import com.enkiba.app.data.repository.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date
import java.util.TimeZone

class ChatFormatTest {

    private val utc = TimeZone.getTimeZone("UTC")
    private val now = Date(1_793_000_000_000L) // 2026-10-26 (UTC)

    private fun msg(id: String, sender: String, at: String, mine: Boolean = false) =
        ChatMessage(id, sender, sender, "texte", at, mine, pending = false)

    @Test
    fun separateursDeJourEtNomDeLExpediteur() {
        val items = ChatFormat.items(
            listOf(
                msg("1", "awa", "2026-10-25T09:00:00Z"),
                msg("2", "awa", "2026-10-25T09:01:00Z"),
                msg("3", "moi", "2026-10-26T08:00:00Z", mine = true),
                msg("4", "awa", "2026-10-26T08:05:00Z"),
            ),
            now, utc,
        )
        assertEquals(listOf("day-Hier", "1", "2", "day-Aujourd'hui", "3", "4"), items.map { it.key })
        val messages = items.filterIsInstance<ChatItem.Message>()
        assertTrue(messages[0].showSender)
        assertFalse(messages[1].showSender)  // même expéditeur à la suite
        assertFalse(messages[2].showSender)  // mes messages : pas de nom
        assertTrue(messages[3].showSender)
        assertEquals("09:01", messages[1].time)
    }

    @Test
    fun jourEnToutesLettres() {
        assertEquals("vendredi 23 octobre", ChatFormat.dayLabel(Date(1_792_750_000_000L), now, utc))
    }
}
