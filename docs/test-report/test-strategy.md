# CampusPlan Test Strategy

## Test levels

| Level | Test classes | What they check |
|---|---|---|
| Unit | `StudentTest`, `RoomTest` | Validation and normalisation inside each domain record |
| Unit | `StudentServiceTest`, `RoomServiceTest` | Application rules: ID assignment, duplicate emails, edit, view, search, room listing and filtering. Tested against an in-memory student repository and a stub room repository. |
| Integration | `JsonRoomRepositoryTest` | Room catalogue JSON parsed with Jackson, and checks across the whole catalogue (duplicate IDs and names, malformed or empty JSON, missing file) |
| Integration | `RoomCatalogueIntegrationTest` | `RoomService` working over the real seed catalogue loaded by `JsonRoomRepository` |
| Acceptance | `CampusPlanCliAcceptanceTest` | End-user scenarios from UC-02 and UC-03, run by typing operator input into the CLI and checking the printed output |

Acceptance tests are fully automated. Every CLI scenario covered so far can be scripted, so there are no manual test steps.

## Test design techniques

- **Boundary values.** Tests use values on and just past each limit:
  - Student names of 1, 100 and 101 characters.
  - Room names of 1, 60 and 61 characters.
  - Room capacities of -1, 0, 1, 100 and 101.
  - A room filter capacity of 0.
  - A filter capacity of 41, one above the largest seed room.
- **Invalid-input partitions.** Each kind of bad input gets at least one case:
  - Names: null, empty and blank.
  - Emails: no `@`, two `@`, nothing before or after the `@`, and internal whitespace.
  - Unknown student IDs.
  - A non-numeric filter capacity and an unknown room feature.
  - Room catalogues that are malformed, empty, missing, or contain duplicates.
- **Normalisation cases.** Padded names, mixed-case emails, padded IDs and search queries, and features with mixed case, extra spaces or repeats.
- **Failure leaves state unchanged.** Rejected adds and edits are followed by a check that no student was saved or changed, and that no ID was used up.
- **Exact messages.** Acceptance tests check the exact `Error:` and result messages given in `Requirements.pdf`.

Each test has a Given/When/Then `@DisplayName` describing the scenario it checks.

## Test data

- **Seed catalogue.** `src/main/resources/seed/rooms.json` (7 rooms) is used by the integration and acceptance tests. Expected results come from the examples in `Requirements.pdf`, for example capacity 6 with a whiteboard gives R01, R02, R04 and R06.
- **Inline data.** Unit and catalogue-validation tests build their rooms and JSON inside the test, so each case shows exactly the input it checks.
- **Fresh state.** Every test starts with no students, so tests don't depend on each other or on the order they run in.

