package com.example.pocastcloni.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupImportNetworkPolicyTest {
    @Test
    fun `new imported HTTP feed cannot authorize itself`() {
        assertFalse(
            maySyncImportedFeed(
                feedIsHttps = false,
                hasExistingLocalApproval = false
            )
        )
    }

    @Test
    fun `HTTPS and pre-approved existing HTTP feeds may sync`() {
        assertTrue(maySyncImportedFeed(feedIsHttps = true, hasExistingLocalApproval = false))
        assertTrue(maySyncImportedFeed(feedIsHttps = false, hasExistingLocalApproval = true))
    }
}
