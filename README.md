# CampusPlan SENG 300 Project

CampusPlan is a menu-driven command-line application that helps a student society organizer plan study sessions, meetings and workshops. In release P1 the organizer can manage students, browse and filter the room catalogue, record each student's availability, and create and edit draft events. So far in P2, the organizer can also get the top five rooms and times for a draft event, with who can and can't attend, schedule the event in one of them, and cancel a scheduled event. The organizer can also filter events by date and status, view event details, and export event rosters as CSV files. All data is saved to a local JSON file after every change.

## Prerequisites

- **JDK 21 or newer.** Check with `java -version`. Maven uses `JAVA_HOME`, so it must point at the JDK 21 folder too (`echo $JAVA_HOME`, or `echo %JAVA_HOME%` in Windows Command Prompt). If you have several JDKs, set `JAVA_HOME` to the 21 one and open a new terminal.
- **No Maven install needed.** The Maven wrapper (`mvnw` / `mvnw.cmd`) downloads Maven 3.10.0 the first time it runs. It also downloads the dependencies (Jackson, JUnit 5, JaCoCo, and the exec plugin), so the first build needs internet access. After that everything works offline.

## Commands

Run these from the repository root. On Windows use `mvnw.cmd` instead of `./mvnw`.

| Task | Command |
|---|---|
| Build | `./mvnw compile` |
| Run all tests | `./mvnw test` |
| Run one test class | `./mvnw test -Dtest=RoomServiceTest` |
| Run one test method | `./mvnw test -Dtest=RoomServiceTest#filtersByCapacity` |
| Run the application | `./mvnw -q compile exec:java` |
| Run with a different data file | `./mvnw -q compile exec:java -Dexec.args="path/to/campusplan.json"` |
| HTML test report (`target/reports/surefire.html`) | `./mvnw clean test surefire-report:report-only` |
| Coverage report (`target/site/jacoco/index.html`) | `./mvnw test` (written automatically) |
| Package a jar into `target/` | `./mvnw package` |
| Remove build output | `./mvnw clean` |

The test and coverage reports for each release are copied to `docs/test-report/`.

## Using the application

The main menu:

| Option | Does |
|---|---|
| 1-4 | Add, edit, view and search students. Viewing a student also shows their availability by date. |
| 5-6 | List rooms, or filter by minimum capacity and required features. |
| 7-9 | Add, remove and view a student's availability. Dates are `YYYY-MM-DD`, times are `HH:MM` on the hour or half hour, between 08:00 and 22:00. |
| 10-12 | Create, edit and view events. Only drafts can be edited. When editing, leave an answer blank to keep the current value, or type `NONE` to clear the required features. Viewing a scheduled event also shows its room and time. |
| 13 | List events with optional date and status filters. Leave either filter blank to include all values. Scheduled events are filtered by their scheduled date; draft and cancelled events are filtered by their date window. |
| 14 | Suggest times for a draft event. Shows the top five room and time options (most people able to attend first, then earliest, then the closest-fitting room), with who can attend and why the others can't. If nothing works, it shows how many options failed because of the room and how many because too few people could come. Nothing is booked. |
| 15 | Schedule a draft event. Shows the same list as option 14, then you pick one by number (blank keeps it as a draft). The choice is checked again before it is saved, so if the room has been booked or too few people can come any more, you get an `Error:` and nothing changes. Scheduling books the room; nobody's response changes. |
| 16 | Cancel a scheduled event. It shows the event and asks you to type `YES`; anything else leaves it as it was. A cancelled event keeps its responses, frees its room, and can't be scheduled again. |
| 18 | Export an event roster as a UTF-8 CSV file. Enter the event ID and destination filename. Includes student IDs, names, emails and attendance responses. Existing files are not overwritten. |
| 0 | Quit. Everything is already saved. |

Errors are printed as one `Error:` line and the menu is shown again; a rejected action never changes saved data.

### Exporting an event roster

1. Select option 18.
2. Enter the event ID, for example `E002`.
3. Enter a destination filename, for example `roster.csv`.

The CSV contains the columns `student_id,name,email,response` and
includes all invited students and their current attendance responses.
Commas, quotation marks and line breaks are escaped correctly.

Relative paths are resolved from the application's working directory.
The destination directory must already exist. Exporting a roster does
not modify saved application data. Existing files are not overwritten.

## Data files

| Data | Location | Persistence |
|---|---|---|
| Students, availability, events and rooms | `data/campusplan.json` (relative to the folder you run from), or the path given as the first program argument | Saved as UTF-8 JSON after every change. The full path is printed at startup. |
| Built-in room catalogue | [src/main/resources/seed/rooms.json](src/main/resources/seed/rooms.json) | Read-only. Copied into the data file the first time something is saved. |
| Sample datasets | [sample-data/](sample-data/) (also bundled in `src/main/resources/seed/`) | Read-only. Offered on first start. |

The `data/` folder is in `.gitignore`, so personal data is never committed.

The data file holds a `formatVersion` number (currently `2`) and one list each of rooms, students, availability and events. A scheduled event also has a `roomId` and a `slot` (date, start and end). Version 1 files from P1 still load and are saved as version 2 after the next change. Dates are stored as `YYYY-MM-DD` and times as `HH:MM`, with no time zone. Saving writes a temporary file and then replaces the data file, so the file is never left half-written.

## Seed data

When CampusPlan starts and there is no data file, it says so and offers:

1. **Sample data where a meeting can be scheduled.** `sample-data/campusplan-sample.json`: the 7 catalogue rooms, 12 fictional students, availability on every day from 2 to 8 November 2026, and 3 draft events. In E001 "Capstone project meeting", five of the six invitees are free on Monday 2 November 13:00-16:00 and four rooms seat six with a whiteboard, so the minimum attendance of 4 can be met.
2. **Sample data where no meeting can be scheduled.** `sample-data/campusplan-no-suggestion.json`: identical, except E001 needs all six invitees. S006 is never free in its window, so no room and time qualifies.
3. **Start with no data.** Nothing is written until the first change. Pressing Enter also chooses this.

Seed data can only be loaded into an empty application. It is never offered again once data exists. To load it again, reset as described below.

Every room in a catalogue or data file must satisfy these rules:

- `id`: required, and unique.
- `name`: 1-60 characters after trimming, and unique ignoring case.
- `capacity`: a number from 1 to 100.
- `features`: zero or more of `WHITEBOARD`, `PROJECTOR`, `ACCESSIBLE`.

If the built-in catalogue is missing or breaks a rule, the app prints an `Error: Room catalogue ...` message and exits with status 1.

## Reset and backup

CampusPlan stores its application data in one JSON file, so backing up and resetting is done with ordinary file operations while the app is closed.

- **Back up:** copy `data/campusplan.json` somewhere else, for example `cp data/campusplan.json backup-2026-11-02.json` (`copy` in Windows Command Prompt).
- **Restore a backup:** close CampusPlan, copy the backup over `data/campusplan.json`, and start again. The file is fully checked when it loads.
- **Reset to empty or to sample data:** close CampusPlan, then delete or rename `data/campusplan.json`. The next start offers the sample datasets or an empty start.
- **Use a separate file**, for example for a demo, without touching your data: pass its path, as in `./mvnw -q compile exec:java -Dexec.args="demo/campusplan.json"`.

If the data file is damaged (not valid JSON, a broken rule, or a newer format version), CampusPlan shows an `Error:` line naming the file and, for broken JSON, the line and column. It then offers:

1. **Quit and leave the file unchanged** (the default).
2. **Set the file aside and start with no data.** After you type `YES`, the file is renamed to `campusplan.json.corrupt-<date>-<time>` with its contents unchanged, so it can be repaired and restored later.

If a save fails, for example because the folder is read-only, the change is not applied and the previous file is kept.

## Known limitations

- **P2 in progress.** Suggestions (FR-04) and scheduling and cancelling (FR-05) are complete. Event filtering and roster export (FR-07) are implemented and tested on the feature branch. Attendance responses (FR-06) and undo/redo (FR-08) are pending integration.
- **A cancelled event forgets its room and time.** Only scheduled events keep them, so viewing a cancelled event doesn't show where it was going to be.
- **Rooms are read-only** from the app. They can only be changed by editing the JSON files.
- **No way to delete students or events.** Physical deletion is out of scope in the brief.
- **Non-ASCII text on Windows.** Data is always stored correctly as UTF-8, but the Windows console may show characters such as `李` as `?`, and typing them may not work, unless the console uses UTF-8 (run `chcp 65001` first).
- **One user at a time.** Running two copies on the same data file at once is not supported; the last save wins.
- **`./mvnw package` builds a plain jar** with no bundled dependencies, so `java -jar` doesn't work. Use the `exec:java` command above.

## Component layout

CampusPlan uses a layered architecture. Each layer is a package under `com.campusplan` in `src/main/java`, and `Main` wires them together at startup. See `docs/design.md` for the class diagram, interface contracts and decisions.

| Layer | Package | Responsibility | Key classes |
|---|---|---|---|
| UI | `ui` | Menu-driven command-line interface | `CampusPlanCli`, `CorruptDataPrompt` |
| Application | `application` | Use cases and rules that span many records, such as ID assignment, unique emails and invitees existing | `StudentService`, `RoomService`, `AvailabilityService`, `EventService`, `SeedLoader`, `RosterExportService` |
| Application | `application.scheduling` | Scheduling suggestions: the room and invitee rules (Strategy pattern) and the ranking | `SchedulingService`, `Suggestion`, `SuggestionResult`, `Schedule`, `RoomRule`, `InviteeRule` and the five rule classes |
| Domain | `domain` | Immutable records that enforce their own rules | `Student`, `Room`, `AvailabilityInterval`, `Availability`, `EventDetails`, `Event`, `ValidationException` |
| Persistence | `persistence` | Storage interfaces and their implementations | `StudentRepository`, `RoomRepository`, `AvailabilityRepository`, `EventRepository`, the `InMemory*` test repositories, `JsonDataStore`, `JsonRoomRepository`, `StorageException` |

Dependencies between layers:

- `ui` uses `application` and `domain`.
- `application` uses `persistence` and `domain`.
- `persistence` uses `domain`.
- `domain` depends on no other layer.
- Only `Main` references every layer.

Repository layout:

```
src/main/java/       Application source, by layer
src/main/resources/  Built-in room catalogue and bundled sample datasets (seed/)
src/test/java/       Tests, same package structure as src/main/java
sample-data/         Sample datasets: one where a meeting can be scheduled, one where none can
docs/                Requirements, design, plan, traceability.csv, test-report/, release.txt
data/                Created at runtime for the data file (not committed)
pom.xml              Maven build file
```
