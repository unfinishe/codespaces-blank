package de.thomba.andropicsort.sort

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import de.thomba.andropicsort.core.ConflictPolicy
import de.thomba.andropicsort.core.ConflictResolver
import de.thomba.andropicsort.core.DateSourceMode
import de.thomba.andropicsort.core.InputFilePolicy
import de.thomba.andropicsort.core.NativeTransferPolicy
import de.thomba.andropicsort.core.OperationMode
import de.thomba.andropicsort.core.SortReport
import de.thomba.andropicsort.core.TimestampPreservationPolicy
import de.thomba.andropicsort.core.YearMonthFolderSchema
import kotlinx.coroutines.ensureActive
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.coroutineContext

class AndroidSortUseCase(
    private val context: Context,
    private val contentResolver: ContentResolver,
) : SortUseCase {

    // ── Internal helper types ───────────────────────────────────────────

    private data class SortCandidate(
        val file: DocumentFile,
        val parent: DocumentFile,
        val dateSourceMode: DateSourceMode,
    )

    private sealed interface TargetPlanResult {
        data class Success(val plan: TargetPlan) : TargetPlanResult
        data class Failure(val deleteConflictFailed: Boolean = false) : TargetPlanResult
    }

    private data class TargetPlan(
        val targetFile: DocumentFile,
        val wasRenamed: Boolean,
    )

    private data class TargetFolderState(
        val folder: DocumentFile,
        val existingNames: MutableSet<String>,
    )

    /** P7 – Mutable accumulator replacing 10 loose counter variables. */
    private class Counters {
        var processed = 0
        var copied = 0
        var moved = 0
        var failed = 0
        var skipped = 0
        var planned = 0
        var renamed = 0
        var createFailed = 0
        var copyFailed = 0
        var deleteFailed = 0
        var timestampPreserved = 0
        var timestampNotPreserved = 0

        fun toReport(dryRun: Boolean, durationMillis: Long, mode: OperationMode) = SortReport(
            processed = processed,
            copied = copied,
            moved = moved,
            failed = failed,
            skipped = skipped,
            planned = planned,
            renamed = renamed,
            createFailed = createFailed,
            copyFailed = copyFailed,
            deleteFailed = deleteFailed,
            timestampPreserved = timestampPreserved,
            timestampNotPreserved = timestampNotPreserved,
            mode = mode,
            dryRun = dryRun,
            durationMillis = durationMillis,
        )
    }

    private val exifDateFormatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

    // ── Public API ──────────────────────────────────────────────────────

    override suspend fun run(config: SortConfig, onProgress: (SortProgress) -> Unit): SortReport {
        val startTime = System.nanoTime()

        // P1 — Source ≠ Target validation
        if (config.sourceTreeUri == config.targetTreeUri) {
            return errorReport(config.dryRun, config.mode)
        }

        val sourceRoot = DocumentFile.fromTreeUri(context, config.sourceTreeUri)
        val targetRoot = DocumentFile.fromTreeUri(context, config.targetTreeUri)

        if (sourceRoot == null || !sourceRoot.isDirectory || targetRoot == null || !targetRoot.isDirectory) {
            return SortReport(
                processed = 0,
                copied = 0,
                moved = 0,
                failed = 1,
                skipped = 0,
                mode = config.mode,
                dryRun = config.dryRun,
            )
        }

        val files = collectCandidateFiles(sourceRoot, config)
        val yearDirCache = mutableMapOf<String, DocumentFile?>()
        val monthStateCache = mutableMapOf<String, TargetFolderState?>()
        val c = Counters()

        // P6 — Progress throttling state
        var lastProgressTime = System.nanoTime()
        val progressIntervalNanos = 100_000_000L // 100 ms

        onProgress(SortProgress(processed = 0, total = files.size))

        for (candidate in files) {
            // P2 — Cancellation support
            coroutineContext.ensureActive()

            val sourceFile = candidate.file
            c.processed += 1
            try {
                val fileName = sourceFile.name
                if (fileName.isNullOrBlank()) {
                    c.skipped += 1
                    lastProgressTime = emitThrottled(c, files.size, onProgress, lastProgressTime, progressIntervalNanos)
                    continue
                }

                val sourceDate = resolveDate(sourceFile, candidate.dateSourceMode)
                val (yearFolder, monthFolder) = YearMonthFolderSchema.pathFor(sourceDate, config.locale)
                val monthState = resolveMonthState(
                    targetRoot, yearFolder, monthFolder,
                    createIfMissing = !config.dryRun,
                    yearDirCache, monthStateCache,
                )

                // P7 — Separated dry-run path
                if (config.dryRun) {
                    processDryRun(fileName, monthState, config.conflictPolicy, c)
                    lastProgressTime = emitThrottled(c, files.size, onProgress, lastProgressTime, progressIntervalNanos)
                    continue
                }

                if (monthState == null) {
                    c.failed += 1; c.createFailed += 1
                    lastProgressTime = emitThrottled(c, files.size, onProgress, lastProgressTime, progressIntervalNanos)
                    continue
                }

                // P7 — Separated real-file path
                processRealFile(sourceFile, candidate.parent, fileName, monthState, config, c)
            } catch (_: Exception) {
                c.failed += 1
            }

            lastProgressTime = emitThrottled(c, files.size, onProgress, lastProgressTime, progressIntervalNanos)
        }

        // Always emit final progress
        onProgress(SortProgress(processed = c.processed, total = files.size))

        // P8 — Duration measurement
        val durationMillis = (System.nanoTime() - startTime) / 1_000_000
        return c.toReport(config.dryRun, durationMillis, config.mode)
    }

    // ── Per-file processing (P7) ────────────────────────────────────────

    private fun processDryRun(
        fileName: String,
        monthState: TargetFolderState?,
        conflictPolicy: ConflictPolicy,
        c: Counters,
    ) {
        val existingNames = monthState?.existingNames ?: emptySet<String>()
        val plannedName = when (conflictPolicy) {
            ConflictPolicy.RENAME -> ConflictResolver.resolveUniqueName(fileName, existingNames)
            ConflictPolicy.OVERWRITE -> fileName
        }
        if (plannedName != fileName) c.renamed += 1
        // Update cache so subsequent dry-run entries see realistic names (P3 fix)
        monthState?.existingNames?.add(plannedName)
        c.planned += 1
    }

    private fun processRealFile(
        sourceFile: DocumentFile,
        sourceParent: DocumentFile,
        fileName: String,
        monthState: TargetFolderState,
        config: SortConfig,
        c: Counters,
    ) {
        val sourceTimestamp = TimestampPreservationPolicy.sourceTimestampToPreserve(sourceFile.lastModified())
        val plannedName = when (config.conflictPolicy) {
            ConflictPolicy.RENAME -> ConflictResolver.resolveUniqueName(fileName, monthState.existingNames)
            ConflictPolicy.OVERWRITE -> fileName
        }
        val plannedWasRenamed = plannedName != fileName
        if (plannedWasRenamed) c.renamed += 1

        val nativeTargetUri = tryNativeTransfer(
            sourceFile = sourceFile,
            sourceParent = sourceParent,
            targetState = monthState,
            targetName = plannedName,
            mode = config.mode,
            conflictPolicy = config.conflictPolicy,
        )
        if (nativeTargetUri != null) {
            when (config.mode) {
                OperationMode.COPY -> c.copied += 1
                OperationMode.MOVE -> c.moved += 1
            }
            recordTimestampStatus(sourceTimestamp, nativeTargetUri, c)
            return
        }

        val mime = sourceFile.type ?: "application/octet-stream"
        val targetPlanResult = createTargetFileWithPolicy(monthState, fileName, mime, config.conflictPolicy)

        if (targetPlanResult is TargetPlanResult.Failure) {
            c.failed += 1
            if (targetPlanResult.deleteConflictFailed) c.deleteFailed += 1 else c.createFailed += 1
            return
        }
        val targetPlan = (targetPlanResult as TargetPlanResult.Success).plan

        if (targetPlan.wasRenamed && !plannedWasRenamed) c.renamed += 1

        val copiedOk = copyContent(sourceFile.uri, targetPlan.targetFile.uri)
        if (!copiedOk) {
            cleanupTarget(targetPlan.targetFile, monthState)
            c.failed += 1; c.copyFailed += 1
            return
        }

        when (config.mode) {
            OperationMode.COPY -> {
                c.copied += 1
                recordTimestampStatus(sourceTimestamp, targetPlan.targetFile.uri, c)
            }
            OperationMode.MOVE -> {
                if (sourceFile.delete()) {
                    c.moved += 1
                    recordTimestampStatus(sourceTimestamp, targetPlan.targetFile.uri, c)
                } else {
                    cleanupTarget(targetPlan.targetFile, monthState)
                    c.failed += 1; c.deleteFailed += 1
                }
            }
        }
    }

    private fun recordTimestampStatus(sourceTimestamp: Long?, targetUri: Uri, c: Counters) {
        if (preserveAndVerifyTimestamp(sourceTimestamp, targetUri)) {
            c.timestampPreserved += 1
        } else {
            c.timestampNotPreserved += 1
        }
    }

    private fun preserveAndVerifyTimestamp(sourceMillis: Long?, targetUri: Uri): Boolean {
        val timestampToPreserve = TimestampPreservationPolicy.sourceTimestampToPreserve(sourceMillis) ?: return false

        if (isTimestampVerified(timestampToPreserve, targetUri)) return true

        runCatching {
            val values = ContentValues().apply {
                put(DocumentsContract.Document.COLUMN_LAST_MODIFIED, timestampToPreserve)
            }
            contentResolver.update(targetUri, values, null, null)
        }

        return isTimestampVerified(timestampToPreserve, targetUri)
    }

    private fun isTimestampVerified(sourceMillis: Long, targetUri: Uri): Boolean {
        val targetMillis = DocumentFile.fromSingleUri(context, targetUri)?.lastModified()
        return TimestampPreservationPolicy.isPreserved(sourceMillis = sourceMillis, targetMillis = targetMillis)
    }

    private fun tryNativeTransfer(
        sourceFile: DocumentFile,
        sourceParent: DocumentFile,
        targetState: TargetFolderState,
        targetName: String,
        mode: OperationMode,
        conflictPolicy: ConflictPolicy,
    ): Uri? {
        val sourceName = sourceFile.name ?: return null
        if (!NativeTransferPolicy.canUseFastPath(sourceName, targetName)) return null

        if (conflictPolicy == ConflictPolicy.OVERWRITE && targetState.existingNames.contains(targetName)) {
            val existing = targetState.folder.findFile(targetName)
            if (existing != null) {
                if (!existing.delete()) return null
                targetState.existingNames.remove(targetName)
            }
        }

        return try {
            val resultUri = when (mode) {
                OperationMode.COPY -> DocumentsContract.copyDocument(contentResolver, sourceFile.uri, targetState.folder.uri)
                OperationMode.MOVE -> DocumentsContract.moveDocument(
                    contentResolver,
                    sourceFile.uri,
                    sourceParent.uri,
                    targetState.folder.uri,
                )
            } ?: return null

            val actualName = DocumentFile.fromSingleUri(context, resultUri)?.name ?: targetName
            targetState.existingNames.add(actualName)
            resultUri
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanupTarget(targetFile: DocumentFile, monthState: TargetFolderState) {
        if (targetFile.delete()) {
            targetFile.name?.let { monthState.existingNames.remove(it) }
        }
    }

    // ── Progress throttling (P6) ────────────────────────────────────────

    private fun emitThrottled(
        c: Counters,
        total: Int,
        onProgress: (SortProgress) -> Unit,
        lastTime: Long,
        intervalNanos: Long,
    ): Long {
        val now = System.nanoTime()
        if (now - lastTime >= intervalNanos || c.processed == total) {
            onProgress(SortProgress(processed = c.processed, total = total))
            return now
        }
        return lastTime
    }

    // ── Directory and file helpers ──────────────────────────────────────

    private fun errorReport(dryRun: Boolean, mode: OperationMode) = SortReport(
        processed = 0,
        copied = 0,
        moved = 0,
        failed = 1,
        skipped = 0,
        mode = mode,
        dryRun = dryRun,
    )

    private fun ensureDirectory(parent: DocumentFile, name: String, createIfMissing: Boolean): DocumentFile? {
        val existing = parent.listFiles().firstOrNull { it.isDirectory && it.name == name }
        return existing ?: if (createIfMissing) parent.createDirectory(name) else null
    }

    private fun createTargetFileWithPolicy(
        targetState: TargetFolderState,
        desiredName: String,
        mimeType: String,
        conflictPolicy: ConflictPolicy,
    ): TargetPlanResult = when (conflictPolicy) {
        ConflictPolicy.RENAME -> createRenamedTargetFile(targetState, desiredName, mimeType)
        ConflictPolicy.OVERWRITE -> createOverwrittenTargetFile(targetState, desiredName, mimeType)
    }

    private fun createRenamedTargetFile(
        targetState: TargetFolderState,
        desiredName: String,
        mimeType: String,
    ): TargetPlanResult {
        repeat(50) {
            val resolvedName = ConflictResolver.resolveUniqueName(desiredName, targetState.existingNames)
            val created = targetState.folder.createFile(mimeType, resolvedName)
            if (created != null) {
                targetState.existingNames.add(resolvedName)
                return TargetPlanResult.Success(
                    TargetPlan(targetFile = created, wasRenamed = resolvedName != desiredName)
                )
            }
            targetState.existingNames.add(resolvedName)
        }
        return TargetPlanResult.Failure(deleteConflictFailed = false)
    }

    private fun createOverwrittenTargetFile(
        targetState: TargetFolderState,
        desiredName: String,
        mimeType: String,
    ): TargetPlanResult {
        if (targetState.existingNames.contains(desiredName)) {
            val existing = targetState.folder.findFile(desiredName)
            if (existing != null && !existing.delete()) {
                return TargetPlanResult.Failure(deleteConflictFailed = true)
            }
            targetState.existingNames.remove(desiredName) // R3 fix
        }

        val created = targetState.folder.createFile(mimeType, desiredName)
            ?: return TargetPlanResult.Failure(deleteConflictFailed = false)

        targetState.existingNames.add(desiredName)
        return TargetPlanResult.Success(TargetPlan(targetFile = created, wasRenamed = false))
    }

    private fun copyContent(sourceUri: Uri, targetUri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(sourceUri)?.use { src ->
                contentResolver.openOutputStream(targetUri, "w")?.use { dst ->
                    src.copyTo(dst)
                } ?: return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Folder cache ────────────────────────────────────────────────────

    private fun resolveMonthState(
        targetRoot: DocumentFile,
        yearFolder: String,
        monthFolder: String,
        createIfMissing: Boolean,
        yearDirCache: MutableMap<String, DocumentFile?>,
        monthStateCache: MutableMap<String, TargetFolderState?>,
    ): TargetFolderState? {
        val monthKey = "$yearFolder/$monthFolder"
        if (monthStateCache.containsKey(monthKey)) return monthStateCache[monthKey]

        val yearDir = yearDirCache.getOrPut(yearFolder) {
            ensureDirectory(targetRoot, yearFolder, createIfMissing)
        }

        if (yearDir == null) {
            monthStateCache[monthKey] = null
            return null
        }

        val monthDir = ensureDirectory(yearDir, monthFolder, createIfMissing)
        val state = monthDir?.let {
            TargetFolderState(
                folder = it,
                existingNames = it.listFiles().mapNotNull { file -> file.name }.toMutableSet(),
            )
        }
        monthStateCache[monthKey] = state
        return state
    }

    // ── File collection ─────────────────────────────────────────────────

    private fun collectCandidateFiles(root: DocumentFile, config: SortConfig): List<SortCandidate> {
        val files = mutableListOf<SortCandidate>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val node = stack.removeLast()
            node.listFiles().forEach { child ->
                when {
                    child.isDirectory -> stack.add(child)
                    child.isFile && InputFilePolicy.shouldInclude(child.name, config.sortNonImages) -> {
                        files.add(
                            SortCandidate(
                                file = child,
                                parent = node,
                                dateSourceMode = InputFilePolicy.effectiveDateSourceMode(
                                    fileName = child.name,
                                    configuredMode = config.dateSourceMode,
                                    includeNonImages = config.sortNonImages,
                                ),
                            )
                        )
                    }
                }
            }
        }
        return files
    }

    // ── Date resolution ─────────────────────────────────────────────────

    private fun resolveDate(file: DocumentFile, mode: DateSourceMode): LocalDateTime = when (mode) {
        DateSourceMode.METADATA_THEN_FILE -> tryReadExifDate(file.uri) ?: fileDate(file)
        DateSourceMode.FILE_ONLY -> fileDate(file)
    }

    private fun fileDate(file: DocumentFile): LocalDateTime {
        val millis = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    }

    private fun tryReadExifDate(uri: Uri): LocalDateTime? {
        return try {
            contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
                    ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                if (date.isNullOrBlank()) null else LocalDateTime.parse(date, exifDateFormatter)
            }
        } catch (_: Exception) {
            null
        }
    }
}
