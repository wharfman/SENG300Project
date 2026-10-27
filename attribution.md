# Attribution

This file records generative AI use (required for every release) and the third-party libraries and sources the project uses.

## 1. Generative AI use (P1)

Each member records their own use. For each substantive use the brief asks for: member, date, tool and model, task or issue, prompt or faithful prompt summary, output used, affected files, changes made, and verification evidence. Exported transcripts of the chat sessions are submitted with the release (see section 1.3).

### 1.1 Gavin Kyle

Gavin Kyle | October 24 | Opus 5.5 | FR-01 classes and files | FR-01 student field input tests | Reading room seed data from JSON file |

Gavin Kyle | October 25 | Opus 5.5 | FR-01 room ID + working character trimming tests | FR-01 CLI menu input tests |

Gavin Kyle | October 26 | Opus 5.5 | Pointing maven to project url | Where to find maven surefire test reports | Finding potentially missed alternate flows for use cases in requirements.pdf | Formatting requirements.pdf |

### 1.2 Omar Almishri

All uses below were in one Claude Code session with Claude Opus 5.5 (Anthropic), on 26-27 October 2026, on branch `feat/FR-02-03`. Omar wrote the prompts and made the decisions noted under "Changes made". Claude read the project brief, `Requirements.pdf` and the existing code, wrote the code, tests and documents, and ran the builds and tests listed under "Verification".

| Date | Task / issue | Prompt (faithful summary) | Output used | Affected files | Changes made | Verification |
|---|---|---|---|---|---|---|
| 26 Oct | Planning P1 | Read the project description and explain how to complete P1 to full marks using AI within the course rules. | Gap analysis of the repository against the P1 rubric, and an ordered list of tasks and prompts. Not code. | None | Omar chose the order of work. | Checked against the brief (sections 7, 11, 12). |
| 26 Oct | Environment | Install JDK 21 and set `JAVA_HOME`. | Install command and `JAVA_HOME` setting. | None (local machine) | None | `java -version` reports 21.0.13; `./mvnw test` passed 76 existing tests on JDK 21. |
| 27 Oct | FR-02 availability | Implement availability per brief section 2 (30-minute boundaries, one date, 08:00-22:00, start before end, merge overlapping and adjacent intervals, half-open); domain, service, repository interface and in-memory implementation; boundary and invalid-input tests first; match existing style. | All generated code and tests. | `domain/AvailabilityInterval.java`, `domain/Availability.java`, `persistence/AvailabilityRepository.java`, `persistence/InMemoryAvailabilityRepository.java`, `application/AvailabilityService.java`, `AvailabilityIntervalTest`, `AvailabilityTest`, `AvailabilityServiceTest` | Accepted as generated. Commit 4580b52. | `./mvnw test`: 113 tests, 0 failures. |
| 27 Oct | FR-02 draft events | Implement create, edit and view of draft events with every limit from the brief; reject invalid references and limits; tests for both sides of every limit. | All generated code and tests. | `domain/EventDetails.java`, `domain/Event.java`, `domain/EventStatus.java`, `domain/AttendanceResponse.java`, `persistence/EventRepository.java`, `persistence/InMemoryEventRepository.java`, `application/EventService.java`, `EventDetailsTest`, `EventTest`, `EventServiceTest` | Accepted as generated. Commit df90b74. | `./mvnw test`: 157 tests, 0 failures. |
| 27 Oct | FR-03 storage | Implement UTF-8 JSON file storage for students, availability and events behind the repository interfaces, keeping IDs; missing file starts empty; corrupt file is never overwritten; failed save keeps the old file; integration tests in temporary folders. | All generated code and tests. | `persistence/JsonDataStore.java`, `persistence/JsonRoomRepository.java` (duplicate check made reusable), `JsonDataStoreTest` | Accepted as generated. Omar set the commit author and asked for AI trailers to be removed from commit messages (disclosure is kept here instead). Commit 5b96b6d. | `./mvnw test`: 170 tests, 0 failures. |
| 27 Oct | FR-02/FR-03 CLI | Wire availability, draft events and storage into the CLI and `Main` so the P1 demo works, including restart; add CLI acceptance tests. | All generated code and tests. | `ui/CampusPlanCli.java`, `Main.java`, `application/SeedLoader.java`, `CampusPlanCliAcceptanceTest` (unknown-option test changed from 9 to 99 because 9 became a real option), `CampusPlanCliPlanningAcceptanceTest`, `.gitignore` | Accepted. While running the real app, Claude found that piped input starting with a byte-order mark shifted the menu answers, fixed it and added `CampusPlanCliAcceptanceTest#ignoresByteOrderMark`. Commit 2d4246d. | `./mvnw test`: 181 tests, 0 failures; two real runs of the app on the same data file showed the data after restart. |
| 27 Oct | Sample data | Create sample datasets (at least 12 students, 6 rooms, 7 days of availability, 3 events; one with a valid suggestion and one with none). | Generated JSON (from a Python script that was not committed) and `SampleDataTest`. | `sample-data/campusplan-sample.json`, `sample-data/campusplan-no-suggestion.json`, `src/main/resources/seed/` copies, `SampleDataTest` | Accepted. Commit 4ed443c. Names first reused well-known computer scientists; they were replaced with invented names so the data is fictional, as the brief requires. | `SampleDataTest` checks sizes and that E001 has 5 of 6 eligible (minimum 4 met in one file, 6 not met in the other). |
| 27 Oct | Align code with `Requirements.pdf` | Claude compared the code with UC-01 to UC-07 and AC-01 to AC-07 and listed ten gaps. Omar chose to fix them before writing traceability, and to drop the "reset to sample data with YES" requirement because it contradicts the brief. | Generated code and tests for the nine kept gaps. | `Main.java`, `application/SeedLoader.java`, `application/EventService.java`, `domain/Event.java`, `persistence/JsonDataStore.java`, `ui/CampusPlanCli.java`, `ui/CorruptDataPrompt.java`, tests in `EventTest`, `EventServiceTest`, `JsonDataStoreTest`, `CampusPlanCliPlanningAcceptanceTest`, `CorruptDataPromptTest` | Omar's decisions as above. Commit 162b91c. | `./mvnw test`: 205 tests, 0 failures; real run of corrupt-file recovery and the seed menu. |
| 27 Oct | Traceability and coverage | Move `traceability.csv` to `docs/`, add FR-02 and FR-03 rows from real test names, regenerate the test report. | Rebuilt `traceability.csv` (184 rows), five more tests for uncovered acceptance criteria, JaCoCo in `pom.xml`, regenerated reports, updated test strategy. | `docs/traceability.csv`, `docs/test-report/`, `pom.xml`, `JsonDataStoreTest`, `EventServiceTest`, `EventTest` | Accepted. Claude kept the existing room-catalogue rows as FR-03 after finding that `Requirements.pdf` assigns UC-01 to FR-03. Commit 8cf6bec. | Script check that every `test_id` names a real test method and every test is listed; `./mvnw test`: 208 tests, 0 failures; coverage domain 98.2%, application 96.8% of lines. |
| 27 Oct | Design document | Write `docs/design.md`: class diagram, interface contracts (event planning, scheduling, storage, undo/redo), two pattern proposals, three ADRs. | Generated document. | `docs/design.md` | Accepted as drafted; patterns (Command, Strategy) to be reviewed by the team. Commit 4131959. | Class diagram checked against the source; search confirmed the domain package has no I/O imports. |
| 27 Oct | Plan document | Write `docs/plan.md`: roles, backlog with acceptance criteria, schedule, top 5 risks, dependencies, retrospective section. | Generated document with name placeholders and an empty retrospective table. | `docs/plan.md` | Names and retrospective conclusions to be filled in by the team. Commit 5c5ab01. | Dates checked against the brief and git history. |
| 27 Oct | README and attribution | Update the README for storage, seed loading and reset/backup; write this disclosure; cite libraries. | Generated text; `exec-maven-plugin` pinned in `pom.xml`. | `README.md`, `attribution.md`, `pom.xml` | Accepted. | `./mvnw -q compile exec:java` run with a scratch data file. |

### 1.3 Transcripts

- Omar Almishri: exported Claude Code transcript of the 26-27 October session, with confidential information removed: `[file name to be added]`.
- Gavin Kyle: `[file name to be added]`.

### 1.4 Members who did not use generative AI

`[Name] | [date] | I did not use generative AI for P1.`

## 2. Third-party libraries and tools

| Library or tool | Version | Used for | Licence |
|---|---|---|---|
| Jackson Databind (`com.fasterxml.jackson.core:jackson-databind`) | 2.18.2 | Reading and writing JSON in `JsonRoomRepository` and `JsonDataStore` | Apache License 2.0 |
| JUnit Jupiter (`org.junit.jupiter:junit-jupiter`) | 5.11.4 | All tests | Eclipse Public License 2.0 |
| JaCoCo Maven plugin (`org.jacoco:jacoco-maven-plugin`) | 0.8.12 | Coverage report | Eclipse Public License 2.0 |
| Exec Maven plugin (`org.codehaus.mojo:exec-maven-plugin`) | 3.5.0 | Running the application with `exec:java` | Apache License 2.0 |
| Maven Wrapper, Maven Surefire and Surefire Report plugins | see `pom.xml` and `.mvn/wrapper/` | Building, running tests, HTML test report | Apache License 2.0 |

The libraries are downloaded by Maven and are not redistributed in this repository, except the generated HTML reports in `docs/test-report/`, which include Maven Fluido skin and JaCoCo report assets under the same licences.

## 3. Other sources

- Project brief: "CampusPlan Student Project", SENG 300 (course document).
- JSON primer linked in the brief: https://www.geeksforgeeks.org/javascript/json/
- All student names in the sample data are invented for this project, and every email address uses the reserved `.example` domain. Test code uses placeholder names such as "Ada" only as test input.
