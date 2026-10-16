package com.enkiba.app.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Comparaison des horodatages serveur utilisés comme curseurs de pull.
 * PostgreSQL supprime les zéros finaux des microsecondes et peut écrire "Z" ou "+00:00" :
 * les horodatages sont normalisés avant d'être comparés.
 */
public class SyncTimestampTest {

    @Test
    public void fractionsDeLongueursDifferentes() {
        assertTrue(SynchronizationManager.compareTimestamps(
                "2026-10-16T10:09:06.1+00:00", "2026-10-16T10:09:06.12+00:00") < 0);
        assertEquals(0, SynchronizationManager.compareTimestamps(
                "2026-10-16T10:09:06.1+00:00", "2026-10-16T10:09:06.100000+00:00"));
        assertTrue(SynchronizationManager.compareTimestamps(
                "2026-10-16T10:09:06.099+00:00", "2026-10-16T10:09:06.1+00:00") < 0);
    }

    @Test
    public void sansFraction() {
        assertEquals(0, SynchronizationManager.compareTimestamps(
                "2026-10-16T10:09:06+00:00", "2026-10-16T10:09:06.000000+00:00"));
        assertTrue(SynchronizationManager.compareTimestamps(
                "2026-10-16T10:09:06+00:00", "2026-10-16T10:09:06.000001+00:00") < 0);
    }

    @Test
    public void curseurInitial() {
        assertTrue(SynchronizationManager.compareTimestamps(
                "2026-10-16T10:09:06.5+00:00", "1970-01-01T00:00:00+00:00") > 0);
    }

    @Test
    public void suffixeZ() {
        assertEquals(0, SynchronizationManager.compareTimestamps(
                "2026-10-16T10:09:06.5Z", "2026-10-16T10:09:06.500000+00:00"));
    }
}
