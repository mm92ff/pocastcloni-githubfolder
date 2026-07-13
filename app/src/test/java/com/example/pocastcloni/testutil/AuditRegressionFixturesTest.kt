package com.example.pocastcloni.testutil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuditRegressionFixturesTest {
    @Test
    fun `duplicate guid fixture keeps feeds distinct`() {
        val episodeA = AuditRegressionFixtures.episode(AuditRegressionFixtures.FEED_A_URL, "Episode A")
        val episodeB = AuditRegressionFixtures.episode(AuditRegressionFixtures.FEED_B_URL, "Episode B")

        assertEquals(episodeA.guid, episodeB.guid)
        assertNotEquals(episodeA.podcastRssUrl, episodeB.podcastRssUrl)
        assertNotEquals(episodeA.enclosureUrl, episodeB.enclosureUrl)
    }

    @Test
    fun `legacy backup fixture represents version one`() {
        val backup = AuditRegressionFixtures.resourceText("/audit/legacy-backup-v1.json")

        assertTrue(backup.contains("\"version\": 1"))
        assertTrue(backup.contains("\"favorites\""))
        assertTrue(backup.contains(AuditRegressionFixtures.DUPLICATE_GUID))
    }

    @Test
    fun `malicious rss fixture contains docdecl and nested entities`() {
        val rss = AuditRegressionFixtures.resourceText("/audit/rss-docdecl-entities.xml")

        assertTrue(rss.contains("<!DOCTYPE"))
        assertTrue(rss.contains("<!ENTITY"))
        assertTrue(rss.contains("&level3;"))
    }
}
