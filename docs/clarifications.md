# Client Clarifications (P1)

The brief asks for at least five clarification questions to the client (the TA), with the answers, our assumptions and unresolved risks.

**Status at the P1 release:** the questions below were written by the team at the end of P1 and have not yet been asked or answered. P1 is built on the assumptions in the third column. The questions will be asked at the week 4 tutorial and the answers added here; any answer that differs from an assumption becomes an issue with a test and a `traceability.csv` update.

| # | Question for the client | Assumption used in P1 | Where it shows in the code |
|---|---|---|---|
| Q1 | May the organizer reload the sample data while data already exists (a reset), or only when the application is empty? | Only into an empty application, as the brief says. Reset is done by removing the data file, as described in the README. | `JsonDataStore.loadSeed`, `CampusPlanCli.offerSeed` |
| Q2 | If the organizer adds availability that is already fully covered, is that an error, or just "nothing changed"? | Not an error: CampusPlan says nothing changed, and the data is untouched. | `CampusPlanCli.changeAvailability` (AC-04.6) |
| Q3 | When removing availability, must the range match an existing interval exactly, or may it cut part of one? | It may cut part of an interval; the rest is kept or split in two. Removing a range with no availability at all is an error. | `Availability.without` (AC-04.7, AC-04.8) |
| Q4 | If the same student is listed twice as an invitee, should CampusPlan reject the draft or quietly keep one copy? | Reject it, naming the repeated ID, because the brief says invitees are distinct. | `EventDetails` (AC-05.9) |
| Q5 | May a draft event's date window be in the past? | Yes. The brief has no "today" rule, and dates are stored without time zones, so tests and sample data can use fixed dates. | `EventDetails` (no date check) |
| Q6 | If the saved data file is damaged, should CampusPlan only refuse to start, or may it offer to set the file aside and start empty? | Offer both: quit is the default; setting aside needs YES and keeps the old file under a new name. The file is never overwritten. | `CorruptDataPrompt`, `JsonDataStore.setAside` (AC-01.13 to AC-01.15) |
| Q7 | Must two students have different names, or only different emails? | Only emails must be unique; names may repeat, and search shows each match with its ID. | `StudentService.ensureEmailUnused` |
| Q8 | Is a room catalogue change between sessions expected, for example a new room added to the seed file? | No. Once data is saved, rooms come from the data file; editing rooms is outside the baseline scope. | `JsonDataStore.open` |

## Unresolved risks

- **All eight assumptions are unconfirmed.** If the client disagrees with one, the related code, tests and acceptance criteria change in P2.
- **Ranking details (P2).** The brief defines the tie-break order, but not how "unused seats" is counted when fewer invitees are eligible than invited. We plan to use room capacity minus the number of invitees.
- **Availability edits and ACCEPTED responses (P2).** It is not yet clear whether removing availability that only partly overlaps an accepted event should also be rejected. We plan to reject any overlap and name the affected events.
