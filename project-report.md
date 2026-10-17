# Project Report: CampusDesk Student Course Registration System

## Abstract

CampusDesk is a local Java web application for managing student information, course offerings, and course enrollments. It serves a responsive browser interface from Java's built-in HTTP server, uses Java collections for in-memory operations, and local CSV-formatted text files for persistent storage. The project demonstrates core object-oriented programming concepts without requiring a database or external libraries.

## Introduction

Colleges need reliable ways to maintain student and course information and track enrollments. A spreadsheet-based process can make it difficult to enforce capacity and duplicate-enrollment rules. CampusDesk provides a small, self-contained workflow: an administrator signs in, maintains student and course records, registers students, cancels enrollments, and reviews reports.

## Problem statement

Design and implement a beginner-friendly application that stores student, course, and registration records between launches while validating identifiers, email addresses, duplicate registrations, and course capacity.

## Objectives

- Demonstrate encapsulation, inheritance, abstraction, polymorphism, and association.
- Build an interactive, responsive website with tables, forms, navigation, alerts, and summary cards.
- Implement CRUD operations and persist their results to local files.
- Enforce student/course uniqueness, registration uniqueness, capacity, and safe deletion.
- Provide documentation, representative tests, and a runnable end-to-end example.

## System requirements

**Software:** Java 17+, a modern web browser, IntelliJ IDEA/Eclipse (optional).  
**Hardware:** A computer capable of running Java and opening a local web page; no external server or database is required.  
**Storage:** A writable project working directory for the automatically created `data/` folder.

## Modules

1. **Authentication:** `WebServer` verifies accounts against password hashes in `users.txt`, provides account creation, and issues an HTTP-only session cookie.
2. **Dashboard:** `WebServer` renders navigation and live summary counts in the browser.
3. **Student management:** Browser forms call `StudentManager` for CRUD, search, and validation.
4. **Course management:** Browser forms call `CourseManager` for CRUD, search, and course details.
5. **Registration:** Web routes call `RegistrationManager` to enforce duplicate/capacity rules and handle cancellations.
6. **Reports:** The website displays searchable registration details, individual enrollment lists, and course seat counts.
7. **File storage:** `FileHandler` creates data files, seeds initial examples, parses and writes CSV, and verifies login credentials.

## Implementation

The application's entry point is `Main`. It loads the file-backed services and starts the JDK `HttpServer` on loopback, by default at port 8080. Open the printed URL in a browser to sign in and use the application. The HTML forms post URL-encoded data to the server; `WebServer` calls the managers rather than writing files directly. Managers keep records in `ArrayList` collections and update their memory only after a successful file operation.

The four automatically created files are:

| File | Stored data |
|---|---|
| `data/students.txt` | Student ID, name, email, phone, department, semester |
| `data/courses.txt` | Course ID, name, code, department, instructor, credits, capacity |
| `data/registrations.txt` | Registration ID, student ID, course ID, registration date |
| `data/users.txt` | Username and salted PBKDF2 password hash (older demo hashes are upgraded at sign-in) |

Each line is one CSV record. Fields are enclosed in quotes; embedded quotes are doubled, and commas inside values are preserved. Line breaks are rejected so each record remains one physical line. New registrations, records, and accounts are appended; updates and deletions write a replacement file and move it over the old file, using an atomic move when supported. Files are read at startup. Empty student/course files receive starter examples, and an empty users file receives the demo account; existing non-empty files are not overwritten. Open sign-up gives every account administrator access in this classroom demonstration.

## OOP concepts used

1. **Encapsulation:** `Student`, `Course`, and `Registration` keep fields private. Public methods control access; setters validate model values.
2. **Inheritance:** `Student` extends the abstract `Person` class and inherits shared identity/contact information.
3. **Polymorphism:** `Student` overrides `Person.getSummary()`. Code can call the method on a `Person` reference and receive student-specific output.
4. **Abstraction:** `Person` defines shared person behavior without representing a complete person record.
5. **Association:** A `Registration` references its student and course by IDs. `RegistrationManager` resolves those associations through `StudentManager` and `CourseManager`; the in-memory relationships cannot silently point to deleted records because deletion is blocked while registrations exist.
6. **Collections:** `ArrayList` is used for loaded records, search results, and file serialization.
7. **Exception handling:** Invalid values raise clear validation exceptions; file failures are reported as `IOException` and displayed as browser error messages.
8. **Constructors:** Parameterized constructors initialize and validate students, courses, registrations, and application services.

## Testing

`tests/RegistrationSystemTest.java` is an executable dependency-free workflow test. It checks authentication, initial samples, student/course creation, duplicate prevention, updates, full-course and duplicate-registration rejection, cancellation, seat restoration, CSV persistence, reload behavior, and the HTTP sign-in/forms/reports workflow. Run it using the command in the README. Additional manual test cases are in [`test-cases.md`](test-cases.md).

## Conclusion

CampusDesk provides a functional example of a layered Java web application. It demonstrates how an HTTP server and browser interface can work with domain models, manager classes, collections, validation, and durable file storage while enforcing essential enrollment rules.

## Future scope

- Add administrator-managed accounts and password changes using salted PBKDF2 or Argon2.
- Add role-based permissions and audit history.
- Add export-to-CSV/PDF, timetable views, and semester-specific reports.
- Add browser-based UI tests and stronger backup/recovery tools.
- Introduce a database only if deployment size and concurrent access later require one.
