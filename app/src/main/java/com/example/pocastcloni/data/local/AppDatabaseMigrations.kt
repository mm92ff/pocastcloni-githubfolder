package com.example.pocastcloni.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AppDatabaseMigrations {

    /** Oldest schema backed by an authentic tracked release schema (v3.51-beta). */
    internal const val SUPPORTED_SCHEMA_FLOOR = 10

    private const val SCHEMA_VERSION_12 = 12
    private const val SCHEMA_VERSION_13 = 13
    private const val SCHEMA_VERSION_14 = 14
    private const val SCHEMA_VERSION_15 = 15
    private const val SCHEMA_VERSION_16 = 16
    private const val SCHEMA_VERSION_17 = 17
    private const val SCHEMA_VERSION_18 = 18

    private val createEpisodesV15Sql =
        """
        CREATE TABLE IF NOT EXISTS `episodes_new` (
            `guid` TEXT NOT NULL,
            `podcastRssUrl` TEXT NOT NULL,
            `title` TEXT NOT NULL,
            `description` TEXT NOT NULL,
            `pubDate` INTEGER,
            `link` TEXT NOT NULL,
            `enclosureUrl` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `fileSize` INTEGER NOT NULL,
            `isPlayed` INTEGER NOT NULL,
            `playbackPositionMs` INTEGER NOT NULL,
            `downloadStatus` TEXT NOT NULL,
            `downloadPath` TEXT,
            `isFavorite` INTEGER NOT NULL,
            `datePlayed` INTEGER,
            `favoriteTimestamp` INTEGER,
            `favoriteAddedAt` INTEGER,
            `duration` INTEGER NOT NULL,
            `episodeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            FOREIGN KEY(`podcastRssUrl`) REFERENCES `podcasts`(`rssUrl`)
                ON UPDATE NO ACTION ON DELETE CASCADE
        )
        """.trimIndent()

    private val copyEpisodesToV15Sql =
        """
        INSERT INTO `episodes_new` (
            `guid`, `podcastRssUrl`, `title`, `description`, `pubDate`, `link`,
            `enclosureUrl`, `type`, `fileSize`, `isPlayed`, `playbackPositionMs`,
            `downloadStatus`, `downloadPath`, `isFavorite`, `datePlayed`,
            `favoriteTimestamp`, `favoriteAddedAt`, `duration`, `episodeId`
        )
        SELECT
            `guid`, `podcastRssUrl`, `title`, `description`, `pubDate`, `link`,
            `enclosureUrl`, `type`, `fileSize`, `isPlayed`, `playbackPositionMs`,
            `downloadStatus`, `downloadPath`, `isFavorite`, `datePlayed`,
            `favoriteTimestamp`, `favoriteAddedAt`, `duration`, `rowid`
        FROM `episodes`
        """.trimIndent()

    private val episodeIndexesV15Sql =
        listOf(
            "CREATE INDEX IF NOT EXISTS `index_episodes_podcastRssUrl` " +
                "ON `episodes` (`podcastRssUrl`)",
            "CREATE INDEX IF NOT EXISTS `index_episodes_podcastRssUrl_pubDate` " +
                "ON `episodes` (`podcastRssUrl`, `pubDate`)",
            "CREATE INDEX IF NOT EXISTS `index_episodes_downloadStatus_pubDate` " +
                "ON `episodes` (`downloadStatus`, `pubDate`)",
            "CREATE INDEX IF NOT EXISTS `index_episodes_isFavorite_favoriteTimestamp` " +
                "ON `episodes` (`isFavorite`, `favoriteTimestamp`)",
            "CREATE INDEX IF NOT EXISTS `index_episodes_isFavorite_favoriteAddedAt` " +
                "ON `episodes` (`isFavorite`, `favoriteAddedAt`)",
            "CREATE INDEX IF NOT EXISTS `index_episodes_isPlayed_datePlayed` " +
                "ON `episodes` (`isPlayed`, `datePlayed`)"
        )

    internal val MIGRATION_12_13 = object : Migration(SCHEMA_VERSION_12, SCHEMA_VERSION_13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `backup_import_journal` (
                    `id` INTEGER NOT NULL,
                    `previousSettingsJson` TEXT NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
        }
    }

    internal val MIGRATION_13_14 = object : Migration(SCHEMA_VERSION_13, SCHEMA_VERSION_14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `podcasts` ADD COLUMN `allowLocalNetwork` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    internal val MIGRATION_14_15 = object : Migration(SCHEMA_VERSION_14, SCHEMA_VERSION_15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `episodes_fts`")
            db.execSQL(createEpisodesV15Sql)
            db.execSQL(copyEpisodesToV15Sql)
            db.execSQL("DROP TABLE `episodes`")
            db.execSQL("ALTER TABLE `episodes_new` RENAME TO `episodes`")
            episodeIndexesV15Sql.forEach { sql -> db.execSQL(sql) }
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_episodes_podcastRssUrl_guid` " +
                    "ON `episodes` (`podcastRssUrl`, `guid`)"
            )
            db.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `episodes_fts` USING FTS4(" +
                    "`title` TEXT NOT NULL, `description` TEXT NOT NULL, content=`episodes`)"
            )
            db.execSQL("INSERT INTO `episodes_fts`(`episodes_fts`) VALUES ('rebuild')")
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_episodes_fts_BEFORE_UPDATE " +
                    "BEFORE UPDATE ON `episodes` BEGIN DELETE FROM `episodes_fts` " +
                    "WHERE `docid`=OLD.`rowid`; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_episodes_fts_BEFORE_DELETE " +
                    "BEFORE DELETE ON `episodes` BEGIN DELETE FROM `episodes_fts` " +
                    "WHERE `docid`=OLD.`rowid`; END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_episodes_fts_AFTER_UPDATE " +
                    "AFTER UPDATE ON `episodes` BEGIN INSERT INTO `episodes_fts`" +
                    "(`docid`, `title`, `description`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`description`); END"
            )
            db.execSQL(
                "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_episodes_fts_AFTER_INSERT " +
                    "AFTER INSERT ON `episodes` BEGIN INSERT INTO `episodes_fts`" +
                    "(`docid`, `title`, `description`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`description`); END"
            )
        }
    }

    internal val MIGRATION_15_16 = object : Migration(SCHEMA_VERSION_15, SCHEMA_VERSION_16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "UPDATE `podcasts` SET `lastModifiedHeader` = NULL, `eTagHeader` = NULL"
            )
        }
    }

    internal val MIGRATION_16_17 = object : Migration(SCHEMA_VERSION_16, SCHEMA_VERSION_17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `podcast_cover_state` (
                    `podcastRssUrl` TEXT NOT NULL,
                    `activeSourceUrl` TEXT,
                    `pendingSourceUrl` TEXT,
                    `pendingFirstSeenAt` INTEGER,
                    `thumbnailFileName` TEXT,
                    `thumbnailRevision` INTEGER NOT NULL,
                    `lastSuccessfulCheckAt` INTEGER,
                    `contentSha256` TEXT,
                    `eTag` TEXT,
                    `lastModified` TEXT,
                    `failureCount` INTEGER NOT NULL,
                    `nextRetryAt` INTEGER,
                    PRIMARY KEY(`podcastRssUrl`),
                    FOREIGN KEY(`podcastRssUrl`) REFERENCES `podcasts`(`rssUrl`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO `podcast_cover_state` (
                    `podcastRssUrl`, `activeSourceUrl`, `pendingSourceUrl`,
                    `pendingFirstSeenAt`, `thumbnailFileName`, `thumbnailRevision`,
                    `lastSuccessfulCheckAt`, `contentSha256`, `eTag`, `lastModified`,
                    `failureCount`, `nextRetryAt`
                )
                SELECT `rssUrl`, NULL, NULLIF(trim(`imageUrl`), ''),
                    CAST(strftime('%s', 'now') AS INTEGER) * 1000,
                    NULL, 0, NULL, NULL, NULL, NULL, 0, NULL
                FROM `podcasts`
                """.trimIndent()
            )
        }
    }

    /**
     * Adds an explicit last-seen episode baseline and repairs legacy badge state.
     *
     * Older schemas cannot distinguish an acknowledged unplayed episode from a badge that was
     * accidentally absent. The safe upgrade choice is to acknowledge played latest episodes and
     * surface unplayed latest episodes once; subsequent acknowledgements are persisted explicitly.
     */
    internal val MIGRATION_17_18 = object : Migration(SCHEMA_VERSION_17, SCHEMA_VERSION_18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `lastSeenEpisodeGuid` TEXT")
            db.execSQL(
                """
                UPDATE `podcasts`
                SET `lastSeenEpisodeGuid` = CASE
                        WHEN (
                            SELECT e.`isPlayed`
                            FROM `episodes` e
                            WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
                            ORDER BY e.`pubDate` DESC, e.`episodeId` DESC
                            LIMIT 1
                        ) = 1 THEN (
                            SELECT e.`guid`
                            FROM `episodes` e
                            WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
                            ORDER BY e.`pubDate` DESC, e.`episodeId` DESC
                            LIMIT 1
                        )
                        ELSE NULL
                    END,
                    `hasNewEpisodes` = CASE
                        WHEN (
                            SELECT e.`isPlayed`
                            FROM `episodes` e
                            WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
                            ORDER BY e.`pubDate` DESC, e.`episodeId` DESC
                            LIMIT 1
                        ) = 0 THEN 1
                        ELSE 0
                    END,
                    `isLatestEpisodePlayed` = (
                        SELECT e.`isPlayed`
                        FROM `episodes` e
                        WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
                        ORDER BY e.`pubDate` DESC, e.`episodeId` DESC
                        LIMIT 1
                    )
                """.trimIndent()
            )
        }
    }

    // -------------------------------------------------------------------------
    // Best-effort legacy migrations (v1–v9 → v10).
    // Authentic schemas/DB fixtures for these versions are not tracked, so they are not
    // part of the guaranteed release upgrade floor. Keep the paths for legacy recovery.
    // Do NOT modify these — they are the committed migration history.
    // -------------------------------------------------------------------------
    private val legacyMigrations: Array<Migration> = (1..9).map { from ->
        object : Migration(from, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                migrateToV10(db)
            }
        }
    }.toTypedArray()

    // -------------------------------------------------------------------------
    // Incremental migrations (v10 → v11, v11 → v12, …)
    // Add one Migration(n, n+1) object here for every future DB version bump.
    // Also update DATABASE_VERSION in Constants and add the schema JSON to VCS.
    // Keep entries in ascending order.
    // -------------------------------------------------------------------------
    private val incrementalMigrations: Array<Migration> = arrayOf(
        object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `episodes` ADD COLUMN `favoriteAddedAt` INTEGER")
                db.execSQL(
                    """
                    UPDATE `episodes`
                    SET `favoriteAddedAt` = `favoriteTimestamp`
                    WHERE `isFavorite` = 1
                      AND `favoriteAddedAt` IS NULL
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_episodes_isFavorite_favoriteAddedAt`
                    ON `episodes` (`isFavorite`, `favoriteAddedAt`)
                    """.trimIndent()
                )
            }
        },
        object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE `podcasts` ADD COLUMN `allowInsecureHttp` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    """
                    UPDATE `podcasts`
                    SET `allowInsecureHttp` = 1
                    WHERE lower(`rssUrl`) LIKE 'http://%'
                       OR lower(`imageUrl`) LIKE 'http://%'
                       OR EXISTS (
                           SELECT 1 FROM `episodes`
                           WHERE `episodes`.`podcastRssUrl` = `podcasts`.`rssUrl`
                             AND lower(`episodes`.`enclosureUrl`) LIKE 'http://%'
                       )
                    """.trimIndent()
                )
            }
        },
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18
    )

    val ALL_MIGRATIONS: Array<Migration> = legacyMigrations + incrementalMigrations

    // =========================================================================
    // v10 full-rebuild helpers (used by legacyMigrations only)
    // =========================================================================

    private fun migrateToV10(db: SupportSQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys=OFF")
        try {
            renameLegacyTableIfPresent(db, "podcasts")
            renameLegacyTableIfPresent(db, "episodes")
            db.execSQL("DROP TABLE IF EXISTS `episodes_fts`")

            createV10Tables(db)
            copyPodcasts(db)
            copyEpisodes(db)
            rebuildPodcastDenormalizedColumns(db)
            db.execSQL("INSERT INTO `episodes_fts`(`episodes_fts`) VALUES ('rebuild')")

            db.execSQL("DROP TABLE IF EXISTS `podcasts_legacy`")
            db.execSQL("DROP TABLE IF EXISTS `episodes_legacy`")
        } finally {
            db.execSQL("PRAGMA foreign_keys=ON")
        }
    }

    private fun renameLegacyTableIfPresent(
        db: SupportSQLiteDatabase,
        table: String
    ) {
        if (!tableExists(db, table)) return
        db.execSQL("ALTER TABLE `$table` RENAME TO `${table}_legacy`")
    }

    private fun createV10Tables(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `podcasts` (" +
                "`rssUrl` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`imageUrl` TEXT NOT NULL, " +
                "`lastRefreshed` INTEGER NOT NULL, " +
                "`autoDownloadEnabled` INTEGER NOT NULL, " +
                "`sortOrder` INTEGER NOT NULL, " +
                "`hasNewEpisodes` INTEGER NOT NULL, " +
                "`latestEpisodeGuid` TEXT, " +
                "`latestEpisodePubDate` INTEGER, " +
                "`isLatestEpisodePlayed` INTEGER, " +
                "`lastModifiedHeader` TEXT, " +
                "`eTagHeader` TEXT, " +
                "PRIMARY KEY(`rssUrl`))"
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `episodes` (" +
                "`guid` TEXT NOT NULL, " +
                "`podcastRssUrl` TEXT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`description` TEXT NOT NULL, " +
                "`pubDate` INTEGER, " +
                "`link` TEXT NOT NULL, " +
                "`enclosureUrl` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, " +
                "`fileSize` INTEGER NOT NULL, " +
                "`isPlayed` INTEGER NOT NULL, " +
                "`playbackPositionMs` INTEGER NOT NULL, " +
                "`downloadStatus` TEXT NOT NULL, " +
                "`downloadPath` TEXT, " +
                "`isFavorite` INTEGER NOT NULL, " +
                "`datePlayed` INTEGER, " +
                "`favoriteTimestamp` INTEGER, " +
                "`duration` INTEGER NOT NULL, " +
                "PRIMARY KEY(`guid`), " +
                "FOREIGN KEY(`podcastRssUrl`) REFERENCES `podcasts`(`rssUrl`) ON UPDATE NO ACTION ON DELETE CASCADE)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_podcastRssUrl` ON `episodes` (`podcastRssUrl`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_podcastRssUrl_pubDate` ON `episodes` (`podcastRssUrl`, `pubDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_downloadStatus_pubDate` ON `episodes` (`downloadStatus`, `pubDate`)")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_episodes_isFavorite_favoriteTimestamp` ON `episodes` (`isFavorite`, `favoriteTimestamp`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_episodes_isPlayed_datePlayed` ON `episodes` (`isPlayed`, `datePlayed`)")
        db.execSQL(
            "CREATE VIRTUAL TABLE IF NOT EXISTS `episodes_fts` USING FTS4(`title` TEXT NOT NULL, `description` TEXT NOT NULL, content=`episodes`)"
        )
    }

    private fun copyPodcasts(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "podcasts_legacy")) return

        val columns = getColumns(db, "podcasts_legacy")
        if ("rssUrl" !in columns) return

        db.execSQL(
            """
            INSERT INTO `podcasts` (
                `rssUrl`,
                `title`,
                `description`,
                `imageUrl`,
                `lastRefreshed`,
                `autoDownloadEnabled`,
                `sortOrder`,
                `hasNewEpisodes`,
                `latestEpisodeGuid`,
                `latestEpisodePubDate`,
                `isLatestEpisodePlayed`,
                `lastModifiedHeader`,
                `eTagHeader`
            )
            SELECT
                `rssUrl`,
                ${requiredText(columns, "title")},
                ${requiredText(columns, "description")},
                ${requiredText(columns, "imageUrl")},
                ${requiredLong(columns, "lastRefreshed", nowExpression())},
                ${requiredBoolean(columns, "autoDownloadEnabled")},
                ${requiredLong(columns, "sortOrder")},
                ${requiredBoolean(columns, "hasNewEpisodes")},
                ${nullableColumn(columns, "latestEpisodeGuid")},
                ${nullableColumn(columns, "latestEpisodePubDate")},
                ${nullableColumn(columns, "isLatestEpisodePlayed")},
                ${nullableColumn(columns, "lastModifiedHeader")},
                ${nullableColumn(columns, "eTagHeader")}
            FROM `podcasts_legacy`
            WHERE `rssUrl` IS NOT NULL
            """.trimIndent()
        )
    }

    private fun copyEpisodes(db: SupportSQLiteDatabase) {
        if (!tableExists(db, "episodes_legacy")) return

        val columns = getColumns(db, "episodes_legacy")
        if ("guid" !in columns || "podcastRssUrl" !in columns) return

        db.execSQL(
            """
            INSERT INTO `episodes` (
                `guid`,
                `podcastRssUrl`,
                `title`,
                `description`,
                `pubDate`,
                `link`,
                `enclosureUrl`,
                `type`,
                `fileSize`,
                `isPlayed`,
                `playbackPositionMs`,
                `downloadStatus`,
                `downloadPath`,
                `isFavorite`,
                `datePlayed`,
                `favoriteTimestamp`,
                `duration`
            )
            SELECT
                e.`guid`,
                e.`podcastRssUrl`,
                ${requiredText(columns, "title", tableAlias = "e")},
                ${requiredText(columns, "description", tableAlias = "e")},
                ${nullableColumn(columns, "pubDate", "e")},
                ${requiredText(columns, "link", tableAlias = "e")},
                ${requiredText(columns, "enclosureUrl", tableAlias = "e")},
                ${requiredText(columns, "type", "'audio/mpeg'", "e")},
                ${requiredLong(columns, "fileSize", tableAlias = "e")},
                ${requiredBoolean(columns, "isPlayed", tableAlias = "e")},
                ${requiredLong(columns, "playbackPositionMs", tableAlias = "e")},
                ${requiredText(columns, "downloadStatus", "'NOT_DOWNLOADED'", "e")},
                ${nullableColumn(columns, "downloadPath", "e")},
                ${requiredBoolean(columns, "isFavorite", tableAlias = "e")},
                ${nullableColumn(columns, "datePlayed", "e")},
                ${nullableColumn(columns, "favoriteTimestamp", "e")},
                ${requiredLong(columns, "duration", tableAlias = "e")}
            FROM `episodes_legacy` e
            WHERE e.`guid` IS NOT NULL
              AND e.`podcastRssUrl` IS NOT NULL
              AND EXISTS (
                  SELECT 1
                  FROM `podcasts` p
                  WHERE p.`rssUrl` = e.`podcastRssUrl`
              )
            """.trimIndent()
        )
    }

    private fun rebuildPodcastDenormalizedColumns(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            UPDATE `podcasts`
            SET
                `latestEpisodeGuid` = (
                    SELECT e.`guid`
                    FROM `episodes` e
                    WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
                    ORDER BY COALESCE(e.`pubDate`, 0) DESC, e.`guid` DESC
                    LIMIT 1
                ),
                `latestEpisodePubDate` = (
                    SELECT e.`pubDate`
                    FROM `episodes` e
                    WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
                    ORDER BY COALESCE(e.`pubDate`, 0) DESC, e.`guid` DESC
                    LIMIT 1
                ),
                `isLatestEpisodePlayed` = (
                    SELECT e.`isPlayed`
                    FROM `episodes` e
                    WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
                    ORDER BY COALESCE(e.`pubDate`, 0) DESC, e.`guid` DESC
                    LIMIT 1
                )
            WHERE EXISTS (
                SELECT 1
                FROM `episodes` e
                WHERE e.`podcastRssUrl` = `podcasts`.`rssUrl`
            )
            """.trimIndent()
        )
    }

    private fun tableExists(
        db: SupportSQLiteDatabase,
        table: String
    ): Boolean {
        val cursor = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='$table'")
        return try {
            cursor.moveToFirst()
        } finally {
            cursor.close()
        }
    }

    private fun getColumns(
        db: SupportSQLiteDatabase,
        table: String
    ): Set<String> {
        val cursor = db.query("PRAGMA table_info(`$table`)")
        return try {
            val nameIndex = cursor.getColumnIndex("name")
            buildSet {
                while (cursor.moveToNext()) {
                    add(cursor.getString(nameIndex))
                }
            }
        } finally {
            cursor.close()
        }
    }

    private fun requiredText(
        columns: Set<String>,
        name: String,
        defaultSql: String = "''",
        tableAlias: String? = null
    ): String {
        val columnRef = columnReference(name, tableAlias)
        return if (name in columns) "COALESCE($columnRef, $defaultSql)" else defaultSql
    }

    private fun requiredLong(
        columns: Set<String>,
        name: String,
        defaultSql: String = "0",
        tableAlias: String? = null
    ): String {
        val columnRef = columnReference(name, tableAlias)
        return if (name in columns) "COALESCE($columnRef, $defaultSql)" else defaultSql
    }

    private fun requiredBoolean(
        columns: Set<String>,
        name: String,
        default: Boolean = false,
        tableAlias: String? = null
    ): String {
        val defaultSql = if (default) "1" else "0"
        val columnRef = columnReference(name, tableAlias)
        return if (name in columns) "COALESCE($columnRef, $defaultSql)" else defaultSql
    }

    private fun nullableColumn(
        columns: Set<String>,
        name: String,
        tableAlias: String? = null
    ): String {
        return if (name in columns) columnReference(name, tableAlias) else "NULL"
    }

    private fun columnReference(
        name: String,
        tableAlias: String? = null
    ): String {
        return if (tableAlias.isNullOrBlank()) {
            "`$name`"
        } else {
            "$tableAlias.`$name`"
        }
    }

    private fun nowExpression(): String = "(CAST(strftime('%s','now') AS INTEGER) * 1000)"
}
