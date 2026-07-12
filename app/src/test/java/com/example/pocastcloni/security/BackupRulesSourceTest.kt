package com.example.pocastcloni.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class BackupRulesSourceTest {
    @Test
    fun api26And27ExcludeEveryBackupDomain() {
        val document = parse("src/main/res/xml/backup_rules.xml")
        val excludedDomains = document.getElementsByTagName("exclude")
            .let { nodes -> (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("domain").nodeValue } }

        assertEquals(
            setOf(
                "root",
                "file",
                "database",
                "sharedpref",
                "external",
                "device_root",
                "device_file",
                "device_database",
                "device_sharedpref"
            ),
            excludedDomains.toSet()
        )
        assertEquals(0, document.getElementsByTagName("include").length)
    }

    @Test
    fun api28To30OnlyIncludeEncryptedDatabaseAndSettings() {
        val document = parse("src/main/res/xml-v28/backup_rules.xml")
        val includes = document.getElementsByTagName("include")
        val paths = (0 until includes.length).map { index ->
            val attributes = includes.item(index).attributes
            assertEquals("clientSideEncryption", attributes.getNamedItem("requireFlags").nodeValue)
            attributes.getNamedItem("path").nodeValue
        }

        assertEquals(setOf(DATABASE, SETTINGS), paths.toSet())
        assertTrue(paths.none { it.endsWith("-wal") || it.endsWith("-shm") })
    }

    private fun parse(relativePath: String) = DocumentBuilderFactory.newInstance().run {
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        isExpandEntityReferences = false
        newDocumentBuilder().parse(File(relativePath))
    }

    private companion object {
        const val DATABASE = "pocast_cloni_db"
        const val SETTINGS = "datastore/settings.preferences_pb"
    }
}
