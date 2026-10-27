# CampusPlan Test Strategy

## Test levels

| Level | Test classes | What they check |
|---|---|---|
| Unit | `StudentTest`, `RoomTest` | Validation and normalisation inside each domain record |
| Unit | `AvailabilityIntervalTest`, `AvailabilityTest` | 30-minute boundaries, opening hours, start before end, half-open overlap, merging overlapping and touching intervals, removing and splitting, full coverage of an event |
| Unit | `EventDetailsTest`, `EventTest` | Every draft limit on both sides (title, invitees, window, duration, minimum attendance), PENDING responses, responses kept on edit, only DRAFT editable |
| Unit | `StudentServiceTest`, `RoomServiceTest`, `AvailabilityServiceTest`, `EventServiceTest` | Application rules: ID assignment, duplicate emails, unknown students and events, sorting, and that every rejected operation leaves storage unchanged. Tested against the in-memory repositories. |
| Integration | `JsonRoomRepositoryTest` | Room catalogue JSON parsed with Jackson, and checks across the whole catalogue (duplicate IDs and names, malformed or empty JSON, missing file) |
| Integration | `RoomCatalogueIntegrationTest` | `RoomService` working over the real seed catalogue loaded by `JsonRoomRepository` |
| Integration | `JsonDataStoreTest` | Real JSON round trips in JUnit temporary folders: IDs kept, UTF-8, corrupt and invalid files rejected without change, line and column in errors, failed and interrupted saves, unchanged saves not written, setting a corrupt file aside, seed loading |
| Integration | `SampleDataTest` | Both files in `sample-data/` load, meet the size rules, and have (or lack) a valid suggestion as documented |
| Acceptance | `CampusPlanCliAcceptanceTest` | End-user scenarios from UC-02 and UC-03, run by typing operator input into the CLI and checking the printed output |
| Acceptance | `CampusPlanCliPlanningAcceptanceTest` | UC-01 and UC-04 to UC-07 through the CLI over a real data file, including the P1 demo: add students, duplicate rejected, availability, room filter, draft, restart |
| Acceptance | `CorruptDataPromptTest` | The startup choice after a corrupt data file: quit by default, set aside only after YES |

Acceptance tests are fully automated. Every CLI scenario covered so far can be scripted, so there are no manual test steps. A restart is tested by running two CLI sessions against the same data file.

`docs/traceability.csv` maps every acceptance criterion in `Requirements.pdf` to the tests that check it.

## Test design techniques

- **Boundary values.** Tests use values on and just past each limit:
  - Student names of 1, 100 and 101 characters.
  - Room names of 1, 60 and 61 characters.
  - Room capacities of -1, 0, 1, 100 and 101.
  - A room filter capacity of 0.
  - A filter capacity of 41, one above the largest seed room.
  - Availability of exactly 08:00-22:00, a single 30-minute slot, 07:30 and 22:30.
  - Adjacent intervals 13:00-14:00 and 14:00-15:00 (half-open, no overlap).
  - Event titles of 1, 100 and 101 characters; 0, 1, 2, 20 and 21 invitees; windows of 1, 7 and 8 days.
  - Durations of -30, 0, 15, 30, 45, 90, 179, 180 and 210 minutes; minimum attendance of 0, 1, 2, n and n+1.
- **Invalid-input partitions.** Each kind of bad input gets at least one case:
  - Names: null, empty and blank.
  - Emails: no `@`, two `@`, nothing before or after the `@`, and internal whitespace.
  - Unknown student IDs and event IDs.
  - A non-numeric filter capacity and an unknown room feature.
  - Room catalogues that are malformed, empty, missing, or contain duplicates.
  - Times off a 30-minute boundary, with seconds, or in the wrong format; impossible dates.
  - Data files that are cut off, empty, `null`, an array, a newer format version, or that hold an invalid record, a duplicate, or a reference to a missing student.
- **Normalisation cases.** Padded names, mixed-case emails, padded IDs and search queries, and features with mixed case, extra spaces or repeats.
- **Failure leaves state unchanged.** Rejected adds and edits are followed by a check that no record was saved or changed, and that no ID was used up. Failed saves are forced by putting a folder where the temporary file goes, and the test checks the data file is byte-for-byte unchanged.
- **Exact messages.** Acceptance tests check the exact `Error:` and result messages given in `Requirements.pdf`.
- **Regression tests.** Every corrected defect gets a test, for example `CampusPlanCliAcceptanceTest#ignoresByteOrderMark`.

Each test has a Given/When/Then `@DisplayName` describing the scenario it checks.

## Test data

- **Seed catalogue.** `src/main/resources/seed/rooms.json` (7 rooms) is used by the integration and acceptance tests. Expected results come from the examples in `Requirements.pdf`, for example capacity 6 with a whiteboard gives R01, R02, R04 and R06.
- **Sample datasets.** `sample-data/campusplan-sample.json` and `sample-data/campusplan-no-suggestion.json`, also bundled under `src/main/resources/seed/`.
- **Inline data.** Unit and catalogue-validation tests build their rooms and JSON inside the test, so each case shows exactly the input it checks.
- **Fresh state.** Every test starts with no students and, for file tests, an empty JUnit temporary folder, so tests don't depend on each other or on the order they run in.

## Coverage

`./mvnw test` writes a JaCoCo report to `target/site/jacoco/index.html`. The copy for this release is in `coverage/`.

| Package | Line coverage | Branch coverage |
|---|---|---|
| `domain` | 98.2% | 92.4% |
| `application` | 96.8% | 83.3% |
| `persistence` | 90.8% | 83.9% |
| `ui` | 97.0% | 89.1% |
| `com.campusplan` (`Main`) | 0% | 0% |

Uncovered code and why:

- **`Main`** only wires the layers together and reads `System.in`. Each piece it calls is tested directly (`JsonDataStore`, `CorruptDataPrompt`, `CampusPlanCli`, the sample files). The full start-up path is checked by the demo run.
- **I/O failure branches** in `JsonDataStore` and `JsonRoomRepository` (an `IOException` while reading a file that exists, or while renaming a file being set aside) need a failing disk to reach. The failed-save path that matters for AC-07.6 is tested.
- **Null-argument guards** in domain constructors, such as a null response map or a null availability list, are defensive; the CLI never passes null.
- **`SeedLoader.NONE.load`** throws if called; the CLI never calls it, because `canLoad()` is false.
