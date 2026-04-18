# 06 Runtime View

## Runtime Scenario: Start Sort Run
1. User selects task `Sort files`.
2. User selects source and target folders.
3. User selects mode (`Copy` or `Move`).
4. User starts run.
5. `SortUseCase` requests recursive listing from storage adapter.
6. For each supported file:
   - Resolve date via metadata; fallback to file timestamp.
   - Build destination path from schema and locale month names.
   - Resolve conflicts (`_1`, `_2`, ...).
   - Execute copy or move.
   - Emit progress update.
7. On completion, app displays summary report.

## Runtime Scenario: Repair File Dates
1. User selects task `Repair file dates`.
2. User selects the folder to repair.
3. User selects repair date priority (metadata and/or filename).
4. `SortUseCase` scans supported files recursively.
5. For each supported file:
   - Try to derive the intended date from metadata and/or common filename patterns.
   - Update the file date in place via SAF provider APIs.
   - Verify whether the provider accepted the change.
6. On completion, app displays confirmed, skipped, and not-confirmed repairs.

## Runtime Scenario: Metadata Failure
1. Metadata read fails or has no valid date.
2. `DateResolver` logs/marks fallback path.
3. File timestamp is used.
4. Processing continues.

## Runtime Scenario: Partial Errors
1. Individual file operation fails (permission/path/corruption).
2. Error is counted in report and processing continues for next file.
3. Final report includes failure count.

