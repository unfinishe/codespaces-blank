# 10 Quality Requirements

## Quality Tree
- Reliability
- Usability
- Performance
- Maintainability
- Testability

## Quality Scenarios
### Reliability
- **Scenario:** Metadata is unavailable for a subset of files.
- **Expected:** App falls back to file timestamp and continues processing.

### Usability
- **Scenario:** First-time user starts app without prior setup.
- **Expected:** User can complete one sorting run with source/target selection and mode choice in a short guided flow.

- **Scenario:** User starts a sort or repair operation and wants to understand what the app is doing.
- **Expected:** The app shows the current task and progress during execution and provides a final report with processed count, success/failure counts, skipped items, and duration.

### Performance
- **Scenario:** Sorting a large camera folder.
- **Expected:** Continuous progress updates and no app freeze; measured with macrobenchmarks.

### Maintainability
- **Scenario:** Add a new folder schema later.
- **Expected:** Introduce new schema strategy without changing core sorting orchestration.

### Testability
- **Scenario:** Validate fallback and conflict rules.
- **Expected:** Domain tests run in JVM CI without device dependencies.

