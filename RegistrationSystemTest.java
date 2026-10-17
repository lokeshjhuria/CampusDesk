package college.registration;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RegistrationSystemTest {
    public static void main(String[] args) throws Exception {
        Path temporaryData = Files.createTempDirectory("campusdesk-test-");
        try {
            verifyCompleteWorkflow(temporaryData);
            verifyWebWorkflow(temporaryData.resolve("web"));
            System.out.println("All registration system tests passed.");
        } finally {
            try (java.util.stream.Stream<Path> paths = Files.walk(temporaryData)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                    try { Files.deleteIfExists(path); }
                    catch (IOException e) { throw new java.io.UncheckedIOException(e); }
                });
            }
        }
    }

    private static void verifyWebWorkflow(Path dataDirectory) throws Exception {
        Files.createDirectories(dataDirectory);
        FileHandler files = new FileHandler(dataDirectory);
        StudentManager students = new StudentManager(files);
        CourseManager courses = new CourseManager(files);
        RegistrationManager registrations = new RegistrationManager(files, students, courses);
        WebServer server = new WebServer(files, students, courses, registrations, 0);
        server.start();
        try {
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            String base = "http://127.0.0.1:" + server.getPort();
            HttpResponse<String> loginPage = client.send(HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            check(loginPage.statusCode() == 200 && loginPage.body().contains("Welcome back"),
                    "web server shows the login page");
            check(!loginPage.body().contains("Demo:")
                    && !loginPage.body().contains("admin123"),
                    "sign-in page does not display demo credentials");
            check(loginPage.body().contains("voxel-backdrop")
                    && loginPage.body().contains("@keyframes block-float")
                    && loginPage.body().contains("@keyframes cloud-drift")
                    && loginPage.body().contains("voxel-tree")
                    && loginPage.body().contains("background-color:#101a1b"),
                    "website includes its block-world theme and voxel animation");
            HttpResponse<String> signupPage = client.send(HttpRequest.newBuilder(URI.create(base + "/?mode=signup"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            check(signupPage.body().contains("Create your account")
                    && signupPage.body().contains("Confirm password")
                    && signupPage.body().contains("every new account has administrator access"),
                    "website provides a create-account form");
            HttpResponse<String> invalidSignup = post(client, base + "/signup",
                    form("username", "new.web.user", "password", "strongPass123",
                            "confirmPassword", "differentPass123"));
            check(invalidSignup.statusCode() == 303
                    && invalidSignup.headers().firstValue("Location").orElse("").contains("mode=signup"),
                    "signup validation returns the visitor to account creation");
            HttpResponse<String> successfulSignup = post(client, base + "/signup",
                    form("username", "new.web.user", "password", "strongPass123",
                            "confirmPassword", "strongPass123"));
            check(successfulSignup.statusCode() == 303
                    && successfulSignup.headers().firstValue("Location").orElse("").contains("Account"),
                    "website creates accounts and reports success");
            CookieManager newUserCookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient newUserClient = HttpClient.newBuilder().cookieHandler(newUserCookies)
                    .followRedirects(HttpClient.Redirect.NEVER).build();
            post(newUserClient, base + "/login", form("username", "new.web.user", "password", "strongPass123"));
            HttpResponse<String> newUserDashboard = newUserClient.send(
                    HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            check(newUserDashboard.body().contains("Welcome to CampusDesk"),
                    "newly created accounts can sign in");
            HttpResponse<String> login = post(client, base + "/login", "username=admin&password=admin123");
            check(login.statusCode() == 303 && !cookies.getCookieStore().getCookies().isEmpty(),
                    "valid web login creates an authenticated session");
            HttpResponse<String> dashboard = client.send(HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            check(dashboard.body().contains("Welcome to CampusDesk"), "authenticated dashboard is served");

            check(post(client, base + "/action", form(
                    "page", "students", "action", "student-save", "originalId", "",
                    "id", "WEB001", "name", "Web User", "email", "web@example.edu",
                    "phone", "5551000", "department", "Computing", "semester", "1")).statusCode() == 303,
                    "browser can create a student");
            check(post(client, base + "/action", form(
                    "page", "courses", "action", "course-save", "originalId", "",
                    "id", "WEBCRS", "name", "Web Design", "code", "WEB101",
                    "department", "Computing", "instructor", "Dr. Web", "credits", "3", "capacity", "1"))
                    .statusCode() == 303, "browser can create a course");
            post(client, base + "/action", form("page", "registrations", "action", "register",
                    "studentId", "WEB001", "courseId", "WEBCRS"));
            check(registrations.getAll().size() == 1, "browser can register a student for a course");

            HttpResponse<String> reports = client.send(HttpRequest.newBuilder(URI.create(base + "/?page=reports"))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            check(reports.body().contains("WEB101") && reports.body().contains("Seats remaining"),
                    "web reports display registrations and remaining seats");
            expectIllegalArgument(() -> registrations.deleteStudent("WEB001"),
                    "web-created active registrations retain deletion safety");

            Registration registration = registrations.getAll().get(0);
            post(client, base + "/action", form("page", "registrations", "action", "cancel",
                    "registrationId", registration.getId(), "confirmed", "true"));
            check(registrations.getAll().isEmpty()
                    && courses.findById("WEBCRS").getCapacity() == 1,
                    "browser cancellation removes an enrollment and restores its seat");

            HttpResponse<String> confirmation = post(client, base + "/action",
                    form("page", "students", "action", "student-delete", "id", "WEB001"));
            check(confirmation.statusCode() == 200 && confirmation.body().contains("Confirm deletion")
                    && students.findById("WEB001") != null,
                    "web deletion requires a separate confirmation step");
            post(client, base + "/action", form("page", "students", "action", "student-delete",
                    "id", "WEB001", "confirmed", "true"));
            check(students.findById("WEB001") == null, "browser can safely delete an unregistered student");
            post(client, base + "/logout", "");
            HttpResponse<String> signedOut = client.send(HttpRequest.newBuilder(URI.create(base + "/")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            check(signedOut.body().contains("Welcome back")
                    && !signedOut.body().contains("Welcome to CampusDesk"),
                    "signing out invalidates the browser session");
        } finally {
            server.close();
        }
    }

    private static HttpResponse<String> post(HttpClient client, String url, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static String form(String... values) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < values.length; i += 2) {
            if (result.length() > 0) result.append('&');
            result.append(URLEncoder.encode(values[i], StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(values[i + 1], StandardCharsets.UTF_8));
        }
        return result.toString();
    }

    private static void verifyCompleteWorkflow(Path dataDirectory) throws Exception {
        FileHandler files = new FileHandler(dataDirectory);
        check(files.authenticate("admin", "admin123"), "default administrator can sign in");
        check(!files.authenticate("admin", "incorrect"), "incorrect password is rejected");
        files.createAccount("new.student", "longPassword123");
        check(files.authenticate("new.student", "longPassword123"), "new file-backed account can authenticate");
        check(!files.authenticate("new.student", "incorrect"), "new account rejects an incorrect password");
        String userFile = Files.readString(dataDirectory.resolve("users.txt"));
        check(userFile.contains("pbkdf2-sha256$") && !userFile.contains("longPassword123"),
                "new accounts persist salted password hashes rather than passwords");
        expectIllegalArgument(() -> files.createAccount("NEW.STUDENT", "differentPassword"),
                "account usernames are unique without case sensitivity");
        expectIllegalArgument(() -> files.createAccount("ab", "longPassword123"),
                "account creation validates username length");
        expectIllegalArgument(() -> files.createAccount("valid.name", "short"),
                "account creation validates password length");

        StudentManager students = new StudentManager(files);
        CourseManager courses = new CourseManager(files);
        RegistrationManager registrations = new RegistrationManager(files, students, courses);
        check(students.getAll().size() >= 3, "sample students are created");
        check(courses.getAll().size() >= 5, "sample courses are created");

        Student first = new Student("T100", "Mira, \"M\"", "mira@example.edu", "555-0100", "Computing", 1);
        Student second = new Student("T101", "Noah Chen", "noah@example.edu", "555-0101", "Science", 2);
        Student third = new Student("T103", "Sam Rivera", "sam@example.edu", "555-0103", "Computing", 1);
        students.add(first);
        students.add(second);
        students.add(third);
        expectIllegalArgument(() -> students.add(first), "duplicate student ID is rejected");
        Student updated = new Student("T100", "Mira Updated", "mira.updated@example.edu", "555-0102", "Computing", 2);
        students.update(updated);
        check("Mira Updated".equals(students.findById("T100").getFullName()), "student update is visible in memory");

        Course course = new Course("X900", "File Handling, \"Advanced\"", "FH900",
                "Computing", "Dr. Rivera", 3, 2);
        courses.add(course);
        expectIllegalArgument(() -> courses.add(course), "duplicate course ID is rejected");
        expectIllegalArgument(() -> courses.add(new Course("X901", "Other", "FH900",
                "Computing", "Dr. Rivera", 3, 2)), "duplicate course code is rejected");

        Registration firstRegistration = registrations.register("T100", "X900");
        check(registrations.getEnrollmentCount("X900") == 1, "course enrollment count increases");
        expectIllegalArgument(() -> registrations.deleteStudent("T100"), "student with an active registration cannot be deleted");
        expectIllegalArgument(() -> registrations.deleteCourse("X900"), "course with an active registration cannot be deleted");
        expectIllegalArgument(() -> registrations.register("T100", "X900"), "duplicate course registration is rejected");
        Registration secondRegistration = registrations.register("T101", "X900");
        expectIllegalArgument(() -> registrations.updateCourse(new Course("X900", "File Handling, \"Advanced\"",
                "FH900", "Computing", "Dr. Rivera", 3, 1)), "course capacity cannot fall below enrollment");
        check(registrations.getEnrollmentCount("X900") == 2
                && course.getCapacity() - registrations.getEnrollmentCount("X900") == 0,
                "full course has no remaining seats");
        expectIllegalArgument(() -> registrations.register("T103", "X900"), "full course rejects another student");
        registrations.cancel(firstRegistration.getId());
        check(registrations.getEnrollmentCount("X900") == 1, "cancellation restores one course seat");
        Registration thirdRegistration = registrations.register("T103", "X900");
        check(registrations.getForStudent("T101").size() == 1, "student enrollment list is available");
        registrations.cancel(secondRegistration.getId());
        registrations.cancel(thirdRegistration.getId());

        students.delete("T100");
        students.delete("T101");
        students.delete("T103");
        courses.delete("X900");
        students.add(new Student("T102", "Ari, \"Q\"", "ari@example.edu", "555-0102", "Computing", 1));
        students.update(new Student("T102", "Ari Updated, \"Q\"", "ari.updated@example.edu",
                "555-0104", "Computing", 2));
        FileHandler reloadedFiles = new FileHandler(dataDirectory);
        StudentManager reloadedStudents = new StudentManager(reloadedFiles);
        CourseManager reloadedCourses = new CourseManager(reloadedFiles);
        RegistrationManager reloadedRegistrations = new RegistrationManager(reloadedFiles, reloadedStudents, reloadedCourses);
        check(reloadedStudents.findById("T100") == null, "student deletions persist after restart");
        check("Ari Updated, \"Q\"".equals(reloadedStudents.findById("T102").getFullName()),
                "updated CSV commas and quotation marks round-trip after restart");
        check(reloadedCourses.findById("X900") == null, "course deletions persist after restart");
        check(reloadedRegistrations.getAll().isEmpty(), "registration cancellations persist after restart");
        check(reloadedStudents.findById("STU001") != null, "sample data remains present after record updates");
    }

    private static void expectIllegalArgument(ThrowingOperation operation, String message) throws Exception {
        try {
            operation.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface ThrowingOperation {
        void run() throws Exception;
    }
}
