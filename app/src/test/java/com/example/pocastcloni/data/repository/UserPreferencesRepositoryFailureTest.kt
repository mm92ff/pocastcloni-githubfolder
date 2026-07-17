package com.example.pocastcloni.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.example.pocastcloni.domain.model.AppTheme
import com.example.pocastcloni.domain.repository.IndicatorSettings
import com.example.pocastcloni.domain.repository.UserSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class UserPreferencesRepositoryFailureTest {
    @Test
    fun `setter propagates IOException`() = runTest {
        val failure = IOException("write failed")
        val repository = repository(TestDataStore { throw failure })

        try {
            repository.updateTheme(AppTheme.DARK)
            fail("Expected IOException")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
    }

    @Test
    fun `setter propagates non IO failure`() = runTest {
        val failure = IllegalStateException("store unavailable")
        val repository = repository(TestDataStore { throw failure })

        try {
            repository.updateGridSize(140)
            fail("Expected IllegalStateException")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
    }

    @Test
    fun `setter preserves cancellation`() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = repository(TestDataStore { throw cancellation })

        try {
            repository.updateGridSize(140)
            fail("Expected CancellationException")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }

    @Test
    fun `setter completes only after DataStore update`() = runTest {
        val updateStarted = CompletableDeferred<Unit>()
        val allowUpdate = CompletableDeferred<Unit>()
        val repository =
            repository(
                TestDataStore {
                    updateStarted.complete(Unit)
                    allowUpdate.await()
                }
            )

        val update = async { repository.updateTheme(AppTheme.DARK) }
        updateStarted.await()
        assertFalse(update.isCompleted)

        allowUpdate.complete(Unit)
        update.await()
        assertTrue(update.isCompleted)
    }

    @Test
    fun `restoreSettings keeps strict failure contract`() = runTest {
        val failure = IOException("restore failed")
        val repository = repository(TestDataStore { throw failure })

        try {
            repository.restoreSettings(UserSettings())
            fail("Expected IOException")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
    }

    @Test
    fun `restoreSettings persists horizontal and vertical offsets`() = runTest {
        val dataStore = TestDataStore()
        val repository = repository(dataStore)

        repository.restoreSettings(
            UserSettings(
                indicator = IndicatorSettings(
                    xOffset = -13,
                    yOffset = 8
                )
            )
        )

        assertEquals(-13, dataStore.current[UserPreferenceKeys.INDICATOR_X_OFFSET])
        assertEquals(8, dataStore.current[UserPreferenceKeys.INDICATOR_Y_OFFSET])
    }

    @Test
    fun `smart stream item limit persists and rejects values outside zero to twenty`() = runTest {
        val dataStore = TestDataStore()
        val repository = repository(dataStore)

        repository.updateSmartStreamItemLimit(10)

        assertEquals(10, dataStore.current[UserPreferenceKeys.SMART_STREAM_ITEM_LIMIT])
        try {
            repository.updateSmartStreamItemLimit(21)
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
        assertEquals(10, dataStore.current[UserPreferenceKeys.SMART_STREAM_ITEM_LIMIT])
    }

    private fun repository(dataStore: DataStore<Preferences>): UserPreferencesRepositoryImpl =
        UserPreferencesRepositoryImpl.createForTest(
            dataStore = dataStore,
            installationStateProvider = InstallationStateProvider { InstallationState.FRESH }
        )

    private class TestDataStore(
        private val beforeUpdate: suspend () -> Unit = {}
    ) : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())

        override val data: Flow<Preferences> = state

        val current: Preferences
            get() = state.value

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences
        ): Preferences {
            beforeUpdate()
            return transform(state.value).also { state.value = it }
        }
    }
}
