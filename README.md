# CampusDesk — Student Course Registration Website

CampusDesk is a local Java web application for managing students, courses, registrations, and enrollment reports. Open it in a web browser; Java's built-in HTTP server handles requests, while the existing Java managers and CSV text files provide business rules and persistent storage. No Node.js, npm, framework, external library, or database is required.

## Requirements

- Java 17 or newer
- IntelliJ IDEA, Eclipse, or another Java IDE (optional)
- A web browser

## Project structure

```text
OOPs/
├── data/                              # Created automatically on first run
│   ├── students.txt
│   ├── courses.txt
│   ├── registrations.txt
│   └── users.txt
├── src/main/java/college/registration/
│   ├── Main.java                      # Starts the local website
│   ├── WebServer.java                 # HTTP routes, login session, browser UI
│   ├── Person.java
│   ├── Student.java
│   ├── Course.java
│   ├── Registration.java
│   ├── FileHandler.java               # CSV/text-file persistence
│   ├── StudentManager.java
│   ├── CourseManager.java
│   └── RegistrationManager.java
├── tests/RegistrationSystemTest.java  # Model, storage, and HTTP workflow tests
└── docs/
    ├── project-report.md
    ├── test-cases.md
    └── viva-questions.md
```

## Run the website

From the project root in Windows PowerShell:

```powershell
$sources = Get-ChildItem -Path src\main\java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out $sources
java -cp out college.registration.Main
```

Open **http://localhost:8080** in your browser. Sign in with the demo account below. Stop the server with **Ctrl+C** in the terminal.

An optional port can be passed to `Main`, for example:

```powershell
java -cp out college.registration.Main 8090
```

Then open `http://localhost:8090`. The server binds to this computer's loopback interface only; it is not exposed to the local network.

### IntelliJ IDEA or Eclipse

1. Open the project folder as a Java project.
2. Mark `src/main/java` as the source root.
3. Select Java 17 or later as the project SDK.
4. Run `college.registration.Main`.
5. Open the URL printed in the Run console (default `http://localhost:8080`).

The application's working directory determines where `data/` is created. Use the project root as the working directory to keep data alongside the source.

## Run tests

```powershell
$sources = Get-ChildItem -Path src\main\java,tests -Recurse -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out $sources
java -cp out college.registration.RegistrationSystemTest
```

The tests use temporary data folders and an ephemeral local HTTP port. They do not change the project's demo data.

## Account sign-in

Use **Create an account** on the sign-in page to register a username and password. Usernames must be 3-24 letters, numbers, dots, dashes, or underscores; passwords must be at least 8 characters. Each new account receives administrator permissions, so only expose this classroom demo on a trusted computer. Passwords are stored as salted PBKDF2 hashes; older SHA-256 hashes in existing installations are upgraded after a successful sign-in. Production applications also need TLS, robust session protections, abuse prevention, and managed credentials.

## Features

- Responsive browser dashboard with student, course, registration, and report sections.
- Custom block-world theme with a forest-night sky, grass/stone surfaces, pixel-edged controls, floating voxel decorations, softly animated page/card entrances, and reduced-motion accessibility support.
- Student creation, editing, searching, and safe deletion, including email validation.
- Course creation, editing, searching, and safe deletion, with calculated remaining seats.
- Student enrollment and cancellation, with duplicate and course-capacity checks.
- Searchable registration records, per-student enrollment filters, and course seat reports.
- Local CSV storage, automatically created demo data, and preserved commas/quotes.
- Open account creation, session-based sign-in, and sign-out.

## Sample records and interaction

On first launch, the project seeds three students and five courses from different departments. Existing non-empty data files are not overwritten.

Example student:

```text
Student ID: STU101
Full name: Jordan Lee
Email: jordan.lee@example.edu
Phone: 555-0142
Department: Computer Science
Semester: 3
```

After registering the student for `CS201 - Data Structures`, the registration list shows a unique registration ID and date, and the available-seat count decreases by one. Cancelling that registration restores the seat.

The [project report](docs/project-report.md), [test cases](docs/test-cases.md), and [viva questions and answers](docs/viva-questions.md) explain the design, Java OOP concepts, file operations, and validation.
