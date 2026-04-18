package de.thomba.andropicsort.sort

import android.net.Uri
import de.thomba.andropicsort.core.ConflictPolicy
import de.thomba.andropicsort.core.DateSourceMode
import de.thomba.andropicsort.core.OperationMode
import de.thomba.andropicsort.core.RepairDateSourceMode
import de.thomba.andropicsort.core.TaskMode
import java.util.Locale

data class SortConfig(
    val sourceTreeUri: Uri,
    val targetTreeUri: Uri? = null,
    val taskMode: TaskMode = TaskMode.SORT,
    val mode: OperationMode,
    val conflictPolicy: ConflictPolicy,
    val dateSourceMode: DateSourceMode,
    val repairDateSourceMode: RepairDateSourceMode = RepairDateSourceMode.METADATA_THEN_FILENAME,
    val sortNonImages: Boolean,
    val locale: Locale,
    val dryRun: Boolean,
)

