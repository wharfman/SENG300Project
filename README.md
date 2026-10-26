# CampusPlan SENG 300 Project

CampusPlan is a menu-driven command-line application for managing students and browsing a campus room catalogue.

## Prerequisites

- **JDK 21 or newer** on your `PATH` (check with `java -version`).
- **No Maven install needed.** The Maven wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.10.0 the first time it runs. It also downloads dependencies (Jackson, JUnit 5), so the first build needs internet access.

## Commands

Run these from the repository root.

| Task | Command |
|---|---|
| Build | `./mvnw compile` |
| Run all tests | `./mvnw test` |
| Run one test class | `./mvnw test -Dtest=RoomServiceTest` |
| Run one test method | `./mvnw test -Dtest=RoomServiceTest#filtersByCapacity` |
| Run the application | `./mvnw -q compile exec:java -Dexec.mainClass=com.campusplan.Main` |
| Package a jar into `target/` | `./mvnw package` |
| Remove build output | `./mvnw clean` |

## Data files

| Data | Location | Persistence |
|---|---|---|
| Room catalogue | [src/main/resources/seed/rooms.json](src/main/resources/seed/rooms.json) | Read-only. Loaded once at startup from the classpath. |
| Students | In memory | Not saved. Lost when the application exits. |

## Seed data

The room catalogue in `src/main/resources/seed/rooms.json` is the seed data. It loads automatically on every start, so there is no separate seeding step.

The file is a JSON array of rooms:

```json
[
  { "id": "R01", "name": "Maple Hall 101", "capacity": 6, "features": ["WHITEBOARD"] }
]
```

Every room must satisfy these rules:

- `id`: required, and unique within the catalogue.
- `name`: 1-60 characters after trimming, and unique ignoring case.
- `capacity`: a number from 1 to 100. A fractional value is truncated, so `6.7` loads as `6`.
- `features`: zero or more of `WHITEBOARD`, `PROJECTOR`, `ACCESSIBLE`.

To change the seed rooms, edit the file and run the app again. The file is bundled onto the classpath, so `exec:java` picks up the change through its `compile` step. If the catalogue is missing or breaks a rule, the app prints an `Error: Room catalogue ...` message and exits with status 1.

Students have no seed data. Add them through menu option 1. IDs are assigned automatically as `S001`, `S002`, and so on.

## Reset and backup

To be implemented.

## Known limitations

- Student data is not saved between runs.
- The room catalogue is read-only from the app. Rooms can only be changed by editing the JSON file.
- The room catalogue path is fixed. You can't point the app at an external data file.
- `./mvnw package` builds a plain jar with no main-class manifest and no bundled dependencies, so `java -jar` doesn't work. Run the app with the `exec:java` command above.

## Component layout

CampusPlan uses a layered architecture. Each layer is a package under `com.campusplan` in `src/main/java`, and `Main` wires them together at startup.

| Layer | Package | Responsibility | Key classes |
|---|---|---|---|
| UI | `ui` | Menu-driven command-line interface | `CampusPlanCli` |
| Application | `application` | Use cases and rules that span many records, such as ID assignment and unique emails | `StudentService`, `RoomService` |
| Domain | `domain` | Entities and the rules each one enforces on itself | `Student`, `Room`, `RoomFeature` |
| Persistence | `persistence` | Storage interfaces and their implementations | `StudentRepository`, `RoomRepository`, `JsonRoomRepository` |

Tests in `src/test/java` use the same package structure as the code they test.

Dependencies between layers:

- `ui` uses `application` and `domain`.
- `application` uses `persistence` and `domain`.
- `persistence` uses `domain`.
- `domain` depends on no other layer.
- Only `Main` references every layer.

Repository layout:

```
src/main/java/       Application source, by layer
src/main/resources/  Seed data (seed/rooms.json)
src/test/java/       Tests, same package structure as src/main/java
docs/                Requirements, test-report/ 
sample-data/         Sample input data 
pom.xml              Maven build file
```
