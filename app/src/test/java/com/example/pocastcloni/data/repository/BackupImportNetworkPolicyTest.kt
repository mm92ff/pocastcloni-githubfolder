package com.example.pocastcloni.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportNetworkPolicyTest {
    @Test
    fun `new imported HTTP feed cannot authorize itself`() {
        assertFalse(
            maySyncImportedFeed(
                feedUrl = "http://example.com/feed.xml",
                hasExistingHttpApproval = false,
                hasExistingLocalApproval = false
            )
        )
    }

    @Test
    fun `HTTPS and pre-approved existing HTTP feeds may sync`() {
        assertTrue(
            maySyncImportedFeed(
                feedUrl = "https://example.com/feed.xml",
                hasExistingHttpApproval = false,
                hasExistingLocalApproval = false
            )
        )
        assertTrue(
            maySyncImportedFeed(
                feedUrl = "http://example.com/feed.xml",
                hasExistingHttpApproval = true,
                hasExistingLocalApproval = false
            )
        )
    }

    @Test
    fun `backup local flags cannot authorize a local feed`() {
        assertFalse(
            maySyncImportedFeed(
                feedUrl = "https://192.168.1.20/feed.xml",
                hasExistingHttpApproval = false,
                hasExistingLocalApproval = false
            )
        )
        assertFalse(
            maySyncImportedFeed(
                feedUrl = "http://192.168.1.20/feed.xml",
                hasExistingHttpApproval = true,
                hasExistingLocalApproval = false
            )
        )
        assertTrue(
            maySyncImportedFeed(
                feedUrl = "https://192.168.1.20/feed.xml",
                hasExistingHttpApproval = false,
                hasExistingLocalApproval = true
            )
        )
        assertTrue(
            maySyncImportedFeed(
                feedUrl = "http://192.168.1.20/feed.xml",
                hasExistingHttpApproval = true,
                hasExistingLocalApproval = true
            )
        )
    }
}
