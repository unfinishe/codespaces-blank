package de.thomba.andropicsort.settings

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import de.thomba.andropicsort.core.ConflictPolicy
import de.thomba.andropicsort.core.DateSourceMode
import de.thomba.andropicsort.core.OperationMode
import de.thomba.andropicsort.core.RepairDateSourceMode
import de.thomba.andropicsort.core.TaskMode
import kotlinx.coroutines.flow.first

private const val UI_SETTINGS_DATASTORE = "ui_settings"

private val Context.uiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = UI_SETTINGS_DATASTORE)

data class StoredUiSettings(
    val sourceUri: Uri? = null,
    val targetUri: Uri? = null,
    val taskMode: TaskMode = TaskMode.SORT,
    val mode: OperationMode = OperationMode.COPY,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.RENAME,
    val dateSourceMode: DateSourceMode = DateSourceMode.METADATA_THEN_FILE,
    val repairDateSourceMode: RepairDateSourceMode = RepairDateSourceMode.METADATA_THEN_FILENAME,
    val sortNonImages: Boolean = false,
    val dryRun: Boolean = false,
)

class UiSettingsStorage(private val context: Context) : SettingsStorage {

    private object Keys {
        val sourceUri = stringPreferencesKey("source_uri")
        val targetUri = stringPreferencesKey("target_uri")
        val taskMode = stringPreferencesKey("task_mode")
        val mode = stringPreferencesKey("mode")
        val conflictPolicy = stringPreferencesKey("conflict_policy")
        val dateSourceMode = stringPreferencesKey("date_source_mode")
        val repairDateSourceMode = stringPreferencesKey("repair_date_source_mode")
        val sortNonImages = booleanPreferencesKey("sort_non_images")
        val dryRun = booleanPreferencesKey("dry_run")
    }

    override suspend fun load(): StoredUiSettings {
        val prefs = context.uiSettingsDataStore.data.first()
        return StoredUiSettings(
            sourceUri = prefs[Keys.sourceUri]?.let { Uri.parse(it) },
            targetUri = prefs[Keys.targetUri]?.let { Uri.parse(it) },
            taskMode = prefs[Keys.taskMode].toEnumOrDefault(TaskMode.SORT),
            mode = prefs[Keys.mode].toEnumOrDefault(OperationMode.COPY),
            conflictPolicy = prefs[Keys.conflictPolicy].toEnumOrDefault(ConflictPolicy.RENAME),
            dateSourceMode = prefs[Keys.dateSourceMode].toEnumOrDefault(DateSourceMode.METADATA_THEN_FILE),
            repairDateSourceMode = prefs[Keys.repairDateSourceMode].toEnumOrDefault(RepairDateSourceMode.METADATA_THEN_FILENAME),
            sortNonImages = prefs[Keys.sortNonImages] ?: false,
            dryRun = prefs[Keys.dryRun] ?: false,
        )
    }

    override suspend fun save(settings: StoredUiSettings) {
        context.uiSettingsDataStore.edit { prefs ->
            val source = settings.sourceUri?.toString()
            if (source == null) prefs.remove(Keys.sourceUri) else prefs[Keys.sourceUri] = source

            val target = settings.targetUri?.toString()
            if (target == null) prefs.remove(Keys.targetUri) else prefs[Keys.targetUri] = target

            prefs[Keys.taskMode] = settings.taskMode.name
            prefs[Keys.mode] = settings.mode.name
            prefs[Keys.conflictPolicy] = settings.conflictPolicy.name
            prefs[Keys.dateSourceMode] = settings.dateSourceMode.name
            prefs[Keys.repairDateSourceMode] = settings.repairDateSourceMode.name
            prefs[Keys.sortNonImages] = settings.sortNonImages
            prefs[Keys.dryRun] = settings.dryRun
        }
    }
}

private inline fun <reified T : Enum<T>> String?.toEnumOrDefault(defaultValue: T): T {
    return try {
        this?.let { enumValueOf<T>(it) } ?: defaultValue
    } catch (_: IllegalArgumentException) {
        defaultValue
    }
}



