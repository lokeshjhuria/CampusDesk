# CampusDesk — Student Course Registration Website

CampusDesk is a local Java web application for managing students, courses, registrations, and enrollment reports. Open it in a web browser; Java's built-in HTTP server handles requests, while the existing Java managers and CSV text files provide business rules and persistent storage. No Node.js, npm, framework, external library, or database is required.

## Requirements

- Java 17 or newer
- IntelliJ IDEA, Eclipse, or another Java IDE (optional)
- A web browser

## Project structure

```text
├── *.java                  # Application sources
├── RegistrationSystemTest.java
├── Dockerfile              # Standard Container image
├── Dockerfile.vercel       # Vercel Container Runtime image
├── vercel.json             # Vercel deployment configuration
├── compose.yaml            # Local container deployment with persistent data
├── run.ps1 / run.bat       # Quick launch scripts for Windows
└── data/                   # Created automatically; keep this directory persistent
```

## Quick Start (Local)

Run with PowerShell:
```powershell
.\run.ps1
```
Or with Command Prompt:
```cmd
run.bat
```
Or manually with `javac`:
```powershell
$sources = Get-ChildItem -Path . -Filter *.java | ForEach-Object { $_.FullName }
New-Item -ItemType Directory -Force out | Out-Null
javac -encoding UTF-8 -d out $sources
java -cp out college.registration.Main
```

Open **http://localhost:8080** in your browser. Default login: `admin` / `admin123`.

## Deploy to Vercel

CampusDesk includes first-class support for **Vercel Container Runtime** (`Dockerfile.vercel`).

1. Push your repository to GitHub.
2. In the [Vercel Dashboard](https://vercel.com), click **Add New...** -> **Project** and import `CampusDesk`.
3. Vercel automatically detects `Dockerfile.vercel` and deploys your Java web app to a live URL.

For detailed deployment instructions and options, see [VERCEL_DEPLOYMENT.md](file:///C:/Users/lokes/.gemini/antigravity/scratch/CampusDesk/VERCEL_DEPLOYMENT.md).

## Deploy with Docker

Install Docker with Compose, then run from the project root:

```powershell
docker compose up --build -d
```

Open **http://localhost:8080**. Compose keeps application records in the `campusdesk-data` named volume across container rebuilds and restarts. To stop the application without deleting data, run:

```powershell
docker compose down
```

The container listens on all interfaces inside Docker and reads its port from `PORT` (default `8080`). Set `PORT` in the environment before starting Compose to change the host port, for example `$env:PORT = "8090"`. When deploying the image on a hosting platform, configure its port as `8080` and attach persistent storage at `/app/data`.

**Before exposing this classroom application to the public internet:** the default data store seeds an `admin` / `admin123` account, and open sign-up grants administrator permissions. Replace or remove the default account and restrict account creation before public deployment. The built-in HTTP server does not provide TLS; use a platform HTTPS proxy. The app's file-based storage is intended for a single application instance, not horizontally scaled replicas.

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
