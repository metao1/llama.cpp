# Implementation Plan - Add UI and Unit Testing Infrastructure

This plan outlines the steps to add testing capabilities to the LlamaAndroid project, focusing on UI tests for the file categorization flow and unit tests for the core business logic.

## User Review Required

> [!IMPORTANT]
> This plan will modify `app/build.gradle.kts` to add several testing dependencies. It will also create new directories under `app/src/`.

## Proposed Changes

### Build Configuration

#### [MODIFY] [build.gradle.kts](file:///Users/mehrdad/projects/llm/llama/examples/llama.android/app/build.gradle.kts)
- Add testing dependencies:
    - JUnit 4
    - Mockk
    - Koin Test
    - Compose UI Test
    - AndroidX Test (Runner, Rules)
    - Kotlinx Coroutines Test

### Testing Infrastructure

#### [NEW] [FakeFileRepository.kt](file:///Users/mehrdad/projects/llm/llama/examples/llama.android/app/src/androidTest/java/com/metao/ai/fakes/FakeFileRepository.kt)
- A hermetic implementation of `FileRepository` to use in UI tests. It will avoid actual file system IO and native model calls.

#### [NEW] [TestAppModule.kt](file:///Users/mehrdad/projects/llm/llama/examples/llama.android/app/src/androidTest/java/com/metao/ai/di/TestAppModule.kt)
- A Koin module that provides fakes for testing.

### UI Tests

#### [NEW] [FileCategorizeScreenTest.kt](file:///Users/mehrdad/projects/llm/llama/examples/llama.android/app/src/androidTest/java/com/metao/ai/presentation/categorize/FileCategorizeScreenTest.kt)
- Verify that the categorization screen displays correctly.
- Test the "Scan" button interaction using the `FakeFileRepository`.

### Unit Tests

#### [NEW] [FileRepositoryImplTest.kt](file:///Users/mehrdad/projects/llm/llama/examples/llama.android/app/src/test/java/com/metao/ai/data/repository/FileRepositoryImplTest.kt)
- Verify `scanDirectory` and `createCategoryFolders` logic using a temporary directory.

## Verification Plan

### Automated Tests
- Run unit tests: `./gradlew :app:test`
- Run instrumented UI tests: `./gradlew :app:connectedAndroidTest` (requires an emulator or device)

### Manual Verification
- None required beyond running the newly created tests.
