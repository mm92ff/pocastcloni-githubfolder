package com.example.pocastcloni.security

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pocastcloni.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class BackupRulesAndroidTest {
    @Test
    fun api28To30CloudBackupRequiresClientSideEncryption() {
        val document = parseRules(R.xml.backup_rules)
        val includes = document.entries.filter { it.element == "include" }

        assertEquals(setOf(DATABASE, SETTINGS), includes.map { it.path }.toSet())
        assertTrue(includes.all { it.requireFlags == "clientSideEncryption" })
        assertNoDatabaseSidecars(document)
        assertFalse(document.entries.any { it.path == STATISTICS })
    }

    @Test
    fun modernCloudBackupRequiresEncryptionAndMinimizesData() {
        val document = parseRules(R.xml.data_extraction_rules)
        val cloud = document.entries.single { it.element == "cloud-backup" }
        val cloudIncludes = document.entries.filter { it.parent == "cloud-backup" }
        val transferIncludes = document.entries.filter { it.parent == "device-transfer" }

        assertEquals("true", cloud.disableWithoutEncryption)
        assertEquals(setOf(DATABASE, SETTINGS), cloudIncludes.map { it.path }.toSet())
        assertEquals(setOf(DATABASE, SETTINGS, STATISTICS), transferIncludes.map { it.path }.toSet())
        assertNoDatabaseSidecars(document)
    }

    private fun parseRules(resourceId: Int): RuleDocument {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val parser = context.resources.getXml(resourceId)
        val parentElements = ArrayDeque<String>()
        val entries = mutableListOf<RuleEntry>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val element = parser.name
                    entries += RuleEntry(
                        element = element,
                        parent = parentElements.lastOrNull(),
                        path = parser.getAttributeValue(null, "path"),
                        requireFlags = parser.getAttributeValue(null, "requireFlags"),
                        disableWithoutEncryption = parser.getAttributeValue(
                            null,
                            "disableIfNoEncryptionCapabilities"
                        )
                    )
                    parentElements.addLast(element)
                }

                XmlPullParser.END_TAG -> parentElements.removeLast()
            }
            event = parser.next()
        }
        parser.close()
        return RuleDocument(entries)
    }

    private fun assertNoDatabaseSidecars(document: RuleDocument) {
        assertFalse(document.entries.any { it.path?.endsWith("-wal") == true })
        assertFalse(document.entries.any { it.path?.endsWith("-shm") == true })
    }

    private data class RuleDocument(val entries: List<RuleEntry>)

    private data class RuleEntry(
        val element: String,
        val parent: String?,
        val path: String?,
        val requireFlags: String?,
        val disableWithoutEncryption: String?
    )

    private companion object {
        const val DATABASE = "pocast_cloni_db"
        const val SETTINGS = "datastore/settings.preferences_pb"
        const val STATISTICS = "datastore/statistics.preferences_pb"
    }
}
