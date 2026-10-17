package college.registration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebServer implements AutoCloseable {
    private static final int MAX_REQUEST_BYTES = 1_048_576;
    private static final String SESSION_COOKIE = "CAMPUSDESK_SESSION";
    private final FileHandler fileHandler;
    private final StudentManager students;
    private final CourseManager courses;
    private final RegistrationManager registrations;
    private final HttpServer server;
    private final ExecutorService executor;
    private final Set<String> sessions = ConcurrentHashMap.newKeySet();

    public WebServer(FileHandler fileHandler, StudentManager students, CourseManager courses,
                     RegistrationManager registrations, int port) throws IOException {
        this.fileHandler = fileHandler;
        this.students = students;
        this.courses = courses;
        this.registrations = registrations;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handle);
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "campusdesk-web");
            thread.setDaemon(false);
            return thread;
        });
        server.setExecutor(executor);
    }

    public void start() {
        server.start();
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(1);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        Map<String, String> parameters = new HashMap<>();
        try {
            parameters = parseParameters(exchange);
            String path = exchange.getRequestURI().getPath();
            if ("POST".equals(exchange.getRequestMethod()) && "/signup".equals(path)) {
                signup(exchange, parameters);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && "/login".equals(path)) {
                login(exchange, parameters);
                return;
            }
            String session = sessionFrom(exchange);
            if ("POST".equals(exchange.getRequestMethod()) && "/logout".equals(path)) {
                if (session != null) sessions.remove(session);
                exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0");
                redirect(exchange, "/");
                return;
            }
            if ("POST".equals(exchange.getRequestMethod()) && "/action".equals(path)) {
                if (session == null) {
                    redirect(exchange, "/?error=" + url("Your session expired. Please sign in again."));
                    return;
                }
                if (requiresConfirmation(parameters) && !"true".equals(parameters.get("confirmed"))) {
                    send(exchange, 200, renderConfirmation(parameters));
                    return;
                }
                performAction(parameters);
                String page = parameters.getOrDefault("page", "dashboard");
                redirect(exchange, "/?page=" + url(page) + "&notice=" + url("Changes saved."));
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                send(exchange, 405, "Method not allowed.");
                return;
            }
            if (session == null) {
                send(exchange, 200, renderLogin(parameters.get("error"), parameters.get("notice"),
                        "signup".equals(parameters.get("mode"))));
                } else {
                    send(exchange, 200, renderPage(parameters));
                }
        } catch (IllegalArgumentException e) {
                String page = parameters.getOrDefault("page", "dashboard");
                if (sessionFrom(exchange) == null) {
                    String mode = "/signup".equals(exchange.getRequestURI().getPath()) ? "&mode=signup" : "";
                    redirect(exchange, "/?error=" + url(e.getMessage()) + mode);
                } else {
                    redirect(exchange, "/?page=" + url(page) + "&error=" + url(e.getMessage()));
                }
        } catch (Exception e) {
                String message = escape(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
                send(exchange, 500, layout("Server error", "<section class=\"panel\"><h2>Request failed</h2><p>"
                        + message + "</p></section>", null, null, false));
        } finally {
            exchange.close();
        }
    }

    private void login(HttpExchange exchange, Map<String, String> form) throws IOException {
        if (fileHandler.authenticate(form.getOrDefault("username", ""), form.getOrDefault("password", ""))) {
            byte[] token = new byte[32];
            new SecureRandom().nextBytes(token);
            String session = Base64.getUrlEncoder().withoutPadding().encodeToString(token);
            sessions.add(session);
            exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + session + "; Path=/; HttpOnly; SameSite=Lax");
            redirect(exchange, "/");
        } else {
            redirect(exchange, "/?error=" + url("The username or password is incorrect."));
        }
    }

    private void signup(HttpExchange exchange, Map<String, String> form) throws IOException {
        String password = form.getOrDefault("password", "");
        if (!password.equals(form.getOrDefault("confirmPassword", ""))) {
            throw new IllegalArgumentException("The passwords do not match.");
        }
        fileHandler.createAccount(form.getOrDefault("username", ""), password);
        redirect(exchange, "/?notice=" + url("Account created. Sign in with your new username and password."));
    }

    private void performAction(Map<String, String> form) throws IOException {
        switch (form.getOrDefault("action", "")) {
            case "student-save" -> {
                Student student = new Student(value(form, "id"), value(form, "name"), value(form, "email"),
                        value(form, "phone"), value(form, "department"), integer(form, "semester"));
                if (value(form, "originalId").isEmpty()) students.add(student);
                else {
                    if (!value(form, "originalId").equalsIgnoreCase(student.getId())) {
                        throw new IllegalArgumentException("Student ID cannot be changed.");
                    }
                    students.update(student);
                }
            }

            case "student-delete" -> registrations.deleteStudent(value(form, "id"));
            case "course-save" -> {
                Course course = new Course(value(form, "id"), value(form, "name"), value(form, "code"),
                        value(form, "department"), value(form, "instructor"), integer(form, "credits"),
                        integer(form, "capacity"));
                if (value(form, "originalId").isEmpty()) courses.add(course);
                else {
                    if (!value(form, "originalId").equalsIgnoreCase(course.getId())) {
                        throw new IllegalArgumentException("Course ID cannot be changed.");
                    }
                    registrations.updateCourse(course);
                }
            }
            case "course-delete" -> registrations.deleteCourse(value(form, "id"));
            case "register" -> registrations.register(value(form, "studentId"), value(form, "courseId"));
            case "cancel" -> registrations.cancel(value(form, "registrationId"));
            default -> throw new IllegalArgumentException("Unknown action.");
        }
    }

    private static boolean requiresConfirmation(Map<String, String> form) {
        String action = form.getOrDefault("action", "");
        return action.equals("student-delete") || action.equals("course-delete") || action.equals("cancel");
    }

    private String renderConfirmation(Map<String, String> form) {
        String action = form.getOrDefault("action", "");
        String item = switch (action) {
            case "student-delete" -> "student " + form.getOrDefault("id", "");
            case "course-delete" -> "course " + form.getOrDefault("id", "");
            case "cancel" -> "registration " + form.getOrDefault("registrationId", "");
            default -> "record";
        };
        String content = "<section class=\"panel confirm-card\"><p class=\"eyebrow\">PLEASE CONFIRM</p>"
                + "<h1>Confirm " + escape(action.equals("cancel") ? "cancellation" : "deletion")
                + "</h1><p>Are you sure you want to " + escape(action.equals("cancel") ? "cancel" : "delete")
                + " " + escape(item) + "?</p><form method=\"post\" action=\"/action\" class=\"form-actions\">"
                + hidden("page", form.getOrDefault("page", "dashboard"))
                + hidden("action", action)
                + hidden(action.equals("cancel") ? "registrationId" : "id",
                form.getOrDefault(action.equals("cancel") ? "registrationId" : "id", ""))
                + hidden("confirmed", "true")
                + "<button class=\"button danger-button\" type=\"submit\">Confirm</button>"
                + "<a class=\"button\" href=\"/?page=" + escape(form.getOrDefault("page", "dashboard"))
                + "\">Go back</a></form></section>";
        return layout("Confirm action", content, null, null, true);
    }

    private String renderPage(Map<String, String> query) {
        String page = query.getOrDefault("page", "dashboard");
        String content = switch (page) {
            case "students" -> studentsPage(query);
            case "courses" -> coursesPage(query);
            case "registrations" -> registrationsPage(query);
            case "reports" -> reportsPage(query);
            default -> dashboardPage();
        };
        String message = query.containsKey("error") ? query.get("error") : query.get("notice");
        return layout(page, content, message, query.get("error") != null ? "error" : "notice", true);
    }

    private String dashboardPage() {
        StringBuilder html = new StringBuilder("<section class=\"welcome\"><p class=\"eyebrow\">ADMINISTRATION</p>")
                .append("<h1>Welcome to CampusDesk</h1><p>Manage students, courses, and enrollments in one place.</p></section>")
                .append("<section class=\"stats\">")
                .append(stat("Registered students", students.getAll().size(), "students"))
                .append(stat("Available courses", courses.getAll().size(), "courses"))
                .append(stat("Active registrations", registrations.getAll().size(), "registrations"))
                .append("</section><section class=\"panel\"><div class=\"section-heading\"><div><h2>Course capacity</h2>")
                .append("<p>Enrollment and seats remaining</p></div><a class=\"text-link\" href=\"/?page=reports\">View reports</a></div>")
                .append(courseSummaryTable()).append("</section>");
        return html.toString();
    }

    private String studentsPage(Map<String, String> query) {
        Student selected = students.findById(query.getOrDefault("edit", ""));
        String search = query.getOrDefault("search", "");
        StringBuilder html = new StringBuilder("<section class=\"page-heading\"><div><p class=\"eyebrow\">DIRECTORY</p>")
                .append("<h1>Students</h1><p>Manage student profiles and contact details.</p></div></section>")
                .append("<section class=\"panel form-panel\"><h2>").append(selected == null ? "Add student" : "Update student")
                .append("</h2><form method=\"post\" action=\"/action\" class=\"form-grid\">")
                .append(hidden("page", "students")).append(hidden("action", "student-save"))
                .append(hidden("originalId", selected == null ? "" : selected.getId()))
                .append(input("id", "Student ID", selected == null ? "" : selected.getId(), "text", selected != null))
                .append(input("name", "Full name", selected == null ? "" : selected.getFullName()))
                .append(input("email", "Email", selected == null ? "" : selected.getEmail(), "email"))
                .append(input("phone", "Phone", selected == null ? "" : selected.getPhone()))
                .append(input("department", "Department", selected == null ? "" : selected.getDepartment()))
                .append(input("semester", "Semester", selected == null ? "" : String.valueOf(selected.getSemester()), "number"))
                .append("<div class=\"form-actions\"><button class=\"button primary\" type=\"submit\">")
                .append(selected == null ? "Add student" : "Save changes")
                .append("</button><a class=\"button\" href=\"/?page=students\">Clear</a></div></form></section>")
                .append("<section class=\"panel\"><div class=\"section-heading\"><div><h2>Student records</h2><p>")
                .append(students.getAll().size()).append(" student(s)</p></div>")
                .append(searchForm("students", search, "Search ID or name")).append("</div>")
                .append("<div class=\"table-wrap\"><table><thead><tr><th>ID</th><th>Name</th><th>Email</th><th>Phone</th>")
                .append("<th>Department</th><th>Semester</th><th>Actions</th></tr></thead><tbody>");
        for (Student student : students.search(search)) {
            html.append("<tr><td>").append(escape(student.getId())).append("</td><td><strong>")
                    .append(escape(student.getFullName())).append("</strong></td><td>").append(escape(student.getEmail()))
                    .append("</td><td>").append(escape(student.getPhone())).append("</td><td>")
                    .append(escape(student.getDepartment())).append("</td><td>").append(student.getSemester())
                    .append("</td><td class=\"row-actions\"><a class=\"text-link\" href=\"/?page=students&amp;edit=")
                    .append(url(student.getId())).append("\">Edit</a>")
                    .append(deleteForm("students", "student-delete", "id", student.getId())).append("</td></tr>");
        }
        return html.append("</tbody></table></div></section>").toString();
    }

    private String coursesPage(Map<String, String> query) {
        Course selected = courses.findById(query.getOrDefault("edit", ""));
        String search = query.getOrDefault("search", "");
        StringBuilder html = new StringBuilder("<section class=\"page-heading\"><div><p class=\"eyebrow\">CATALOG</p>")
                .append("<h1>Courses</h1><p>Maintain course details, capacity, and available seats.</p></div></section>")
                .append("<section class=\"panel form-panel\"><h2>").append(selected == null ? "Add course" : "Update course")
                .append("</h2><form method=\"post\" action=\"/action\" class=\"form-grid\">")
                .append(hidden("page", "courses")).append(hidden("action", "course-save"))
                .append(hidden("originalId", selected == null ? "" : selected.getId()))
                .append(input("id", "Course ID", selected == null ? "" : selected.getId(), "text", selected != null))
                .append(input("name", "Course name", selected == null ? "" : selected.getName()))
                .append(input("code", "Course code", selected == null ? "" : selected.getCode()))
                .append(input("department", "Department", selected == null ? "" : selected.getDepartment()))
                .append(input("instructor", "Instructor", selected == null ? "" : selected.getInstructor()))
                .append(input("credits", "Credit hours", selected == null ? "" : String.valueOf(selected.getCreditHours()), "number"))
                .append(input("capacity", "Capacity", selected == null ? "" : String.valueOf(selected.getCapacity()), "number"))
                .append("<div class=\"form-actions\"><button class=\"button primary\" type=\"submit\">")
                .append(selected == null ? "Add course" : "Save changes")
                .append("</button><a class=\"button\" href=\"/?page=courses\">Clear</a></div></form></section>")
                .append("<section class=\"panel\"><div class=\"section-heading\"><div><h2>Course catalog</h2><p>")
                .append(courses.getAll().size()).append(" course(s)</p></div>")
                .append(searchForm("courses", search, "Search ID, code, or name")).append("</div>")
                .append("<div class=\"table-wrap\"><table><thead><tr><th>ID</th><th>Course</th><th>Code</th><th>Department</th>")
                .append("<th>Instructor</th><th>Credits</th><th>Capacity</th><th>Seats left</th><th>Actions</th></tr></thead><tbody>");
        for (Course course : courses.search(search)) {
            int available = course.getCapacity() - registrations.getEnrollmentCount(course.getId());
            html.append("<tr><td>").append(escape(course.getId())).append("</td><td><strong>")
                    .append(escape(course.getName())).append("</strong></td><td>").append(escape(course.getCode()))
                    .append("</td><td>").append(escape(course.getDepartment())).append("</td><td>")
                    .append(escape(course.getInstructor())).append("</td><td>").append(course.getCreditHours())
                    .append("</td><td>").append(course.getCapacity()).append("</td><td><span class=\"badge\">")
                    .append(available).append("</span></td><td class=\"row-actions\"><a class=\"text-link\" href=\"/?page=courses&amp;edit=")
                    .append(url(course.getId())).append("\">Edit</a>")
                    .append(deleteForm("courses", "course-delete", "id", course.getId())).append("</td></tr>");
        }
        return html.append("</tbody></table></div></section>").toString();
    }

    private String registrationsPage(Map<String, String> query) {
        String search = query.getOrDefault("search", "").trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder html = new StringBuilder("<section class=\"page-heading\"><div><p class=\"eyebrow\">ENROLLMENT</p>")
                .append("<h1>Course registrations</h1><p>Enroll students, prevent over-capacity, and cancel registrations.</p></div></section>")
                .append("<section class=\"panel form-panel\"><h2>Register a student</h2>");
        if (students.getAll().isEmpty() || courses.getAll().isEmpty()) {
            html.append("<p class=\"muted\">Add at least one student and one course before registering.</p>");
        } else {
            html.append("<form method=\"post\" action=\"/action\" class=\"form-grid\">")
                    .append(hidden("page", "registrations")).append(hidden("action", "register"))
                    .append(selectStudents()).append(selectCourses())
                    .append("<div class=\"form-actions\"><button class=\"button primary\" type=\"submit\">Create registration</button></div></form>");
        }
        html.append("</section><section class=\"panel\"><div class=\"section-heading\"><div><h2>Active registrations</h2><p>")
                .append(registrations.getAll().size()).append(" enrollment(s)</p></div>")
                .append(searchForm("registrations", search, "Search student ID, name, or course code")).append("</div>")
                .append(registrationTable(search, null)).append("</section>");
        return html.toString();
    }

    private String reportsPage(Map<String, String> query) {
        String search = query.getOrDefault("search", "").trim().toLowerCase(java.util.Locale.ROOT);
        String studentId = query.getOrDefault("studentId", "");
        StringBuilder html = new StringBuilder("<section class=\"page-heading\"><div><p class=\"eyebrow\">INSIGHTS</p>")
                .append("<h1>Registration reports</h1><p>Search enrollment records and review course seat availability.</p></div></section>")
                .append("<section class=\"panel\"><div class=\"section-heading\"><div><h2>Student enrollment</h2>")
                .append("<p>Select a student to filter their complete course list.</p></div>")
                .append("<form method=\"get\" action=\"/\" class=\"toolbar-form\"><input type=\"hidden\" name=\"page\" value=\"reports\">")
                .append("<select name=\"studentId\"><option value=\"\">All students</option>");
        for (Student student : students.getAll()) {
            html.append("<option value=\"").append(escape(student.getId())).append("\"")
                    .append(student.getId().equalsIgnoreCase(studentId) ? " selected" : "").append(">")
                    .append(escape(student.getFullName())).append(" (").append(escape(student.getId())).append(")</option>");
        }
        html.append("</select><button class=\"button\" type=\"submit\">Filter</button></form></div>")
                .append(searchForm("reports", search, "Search student ID, name, or course code"))
                .append(registrationTable(search, studentId)).append("</section>")
                .append("<section class=\"panel\"><div class=\"section-heading\"><div><h2>Course enrollment and seats</h2>")
                .append("<p>Available seats update after registration or cancellation.</p></div></div>")
                .append(courseSummaryTable()).append("</section>");
        return html.toString();
    }

    private String registrationTable(String search, String studentId) {
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr><th>Registration ID</th>")
                .append("<th>Student ID</th><th>Student</th><th>Course code</th><th>Course</th><th>Registered</th><th>Action</th>")
                .append("</tr></thead><tbody>");
        for (Registration registration : registrations.getAll()) {
            Student student = students.findById(registration.getStudentId());
            Course course = courses.findById(registration.getCourseId());
            if (student == null || course == null) continue;
            boolean matches = (search.isEmpty()
                    || student.getId().toLowerCase(java.util.Locale.ROOT).contains(search)
                    || student.getFullName().toLowerCase(java.util.Locale.ROOT).contains(search)
                    || course.getCode().toLowerCase(java.util.Locale.ROOT).contains(search))
                    && (studentId == null || studentId.isEmpty() || student.getId().equalsIgnoreCase(studentId));
            if (!matches) continue;
            html.append("<tr><td>").append(escape(registration.getId())).append("</td><td>")
                    .append(escape(student.getId())).append("</td><td>").append(escape(student.getFullName()))
                    .append("</td><td>").append(escape(course.getCode())).append("</td><td>")
                    .append(escape(course.getName())).append("</td><td>").append(registration.getRegisteredOn())
                    .append("</td><td>").append(deleteForm("registrations", "cancel", "registrationId", registration.getId()))
                    .append("</td></tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    private String courseSummaryTable() {
        StringBuilder html = new StringBuilder("<div class=\"table-wrap\"><table><thead><tr><th>Course code</th>")
                .append("<th>Course</th><th>Department</th><th>Capacity</th><th>Enrolled</th><th>Seats remaining</th>")
                .append("</tr></thead><tbody>");
        for (Course course : courses.getAll()) {
            int enrolled = registrations.getEnrollmentCount(course.getId());
            html.append("<tr><td>").append(escape(course.getCode())).append("</td><td><strong>")
                    .append(escape(course.getName())).append("</strong></td><td>").append(escape(course.getDepartment()))
                    .append("</td><td>").append(course.getCapacity()).append("</td><td>").append(enrolled)
                    .append("</td><td><span class=\"badge\">").append(course.getCapacity() - enrolled)
                    .append("</span></td></tr>");
        }
        return html.append("</tbody></table></div>").toString();
    }

    private String selectStudents() {
        StringBuilder html = new StringBuilder("<label class=\"field\"><span>Student</span><select name=\"studentId\" required>");
        for (Student student : students.getAll()) {
            html.append("<option value=\"").append(escape(student.getId())).append("\">")
                    .append(escape(student.getId())).append(" — ").append(escape(student.getFullName())).append("</option>");
        }
        return html.append("</select></label>").toString();
    }

    private String selectCourses() {
        StringBuilder html = new StringBuilder("<label class=\"field\"><span>Course</span><select name=\"courseId\" required>");
        for (Course course : courses.getAll()) {
            int available = course.getCapacity() - registrations.getEnrollmentCount(course.getId());
            html.append("<option value=\"").append(escape(course.getId())).append("\"")
                    .append(available == 0 ? " disabled" : "").append(">")
                    .append(escape(course.getCode())).append(" — ").append(escape(course.getName()))
                    .append(" (").append(available).append(" seats left)</option>");
        }
        return html.append("</select></label>").toString();
    }

    private String searchForm(String page, String value, String placeholder) {
        return "<form method=\"get\" action=\"/\" class=\"toolbar-form\"><input type=\"hidden\" name=\"page\" value=\""
                + escape(page) + "\"><input type=\"search\" name=\"search\" value=\"" + escape(value)
                + "\" placeholder=\"" + escape(placeholder) + "\"><button class=\"button\" type=\"submit\">Search</button>"
                + "<a class=\"button\" href=\"/?page=" + escape(page) + "\">Clear</a></form>";
    }

    private String deleteForm(String page, String action, String field, String id) {
        return "<form method=\"post\" action=\"/action\" class=\"inline-form\">"
                + hidden("page", page) + hidden("action", action) + hidden(field, id)
                + "<button class=\"text-button danger\" type=\"submit\">"
                + (action.equals("cancel") ? "Cancel" : "Delete") + "</button></form>";
    }

    private static String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + escape(name) + "\" value=\"" + escape(value) + "\">";
    }

    private static String input(String name, String label, String value) {
        return input(name, label, value, "text", false);
    }

    private static String input(String name, String label, String value, String type) {
        return input(name, label, value, type, false);
    }

    private static String input(String name, String label, String value, String type, boolean readonly) {
        String minimum = type.equals("number") ? " min=\"1\"" : "";
        return "<label class=\"field\"><span>" + escape(label) + "</span><input name=\"" + escape(name)
                + "\" type=\"" + escape(type) + "\" value=\"" + escape(value) + "\""
                + (readonly ? " readonly" : "") + minimum + " required></label>";
    }

    private String stat(String label, int value, String page) {
        return "<a class=\"stat-card\" href=\"/?page=" + escape(page) + "\"><span>" + escape(label)
                + "</span><strong>" + value + "</strong><small>Open section →</small></a>";
    }

    private String renderLogin(String error, String notice, boolean signup) {
        String message = error == null ? "" : "<div class=\"alert error\">" + escape(error) + "</div>";
        if (notice != null) message += "<div class=\"alert notice\">" + escape(notice) + "</div>";
        String heading = signup ? "Create your account" : "Welcome back";
        String description = signup ? "Create an account to access student and course management."
                : "Sign in to manage courses and student registrations.";
        String accessNote = signup
                ? "<p class=\"access-note\">Account note: every new account has administrator access to student and course records.</p>"
                : "";
        String form = signup
                ? "<form method=\"post\" action=\"/signup\" class=\"login-form\">"
                + "<label class=\"field\"><span>Username</span><input name=\"username\" autocomplete=\"username\" minlength=\"3\" maxlength=\"24\" pattern=\"[A-Za-z0-9_.-]{3,24}\" required></label>"
                + "<label class=\"field\"><span>Password (at least 8 characters)</span><input name=\"password\" type=\"password\" autocomplete=\"new-password\" minlength=\"8\" maxlength=\"128\" required></label>"
                + "<label class=\"field\"><span>Confirm password</span><input name=\"confirmPassword\" type=\"password\" autocomplete=\"new-password\" minlength=\"8\" maxlength=\"128\" required></label>"
                + "<button class=\"button primary\" type=\"submit\">Create account</button></form>"
                + "<p class=\"account-switch\">Already have an account? <a href=\"/\">Sign in</a></p>"
                : "<form method=\"post\" action=\"/login\" class=\"login-form\">"
                + "<label class=\"field\"><span>Username</span><input name=\"username\" autocomplete=\"username\" required></label>"
                + "<label class=\"field\"><span>Password</span><input name=\"password\" type=\"password\" autocomplete=\"current-password\" required></label>"
                + "<button class=\"button primary\" type=\"submit\">Sign in</button></form>"
                + "<p class=\"account-switch\">New here? <a href=\"/?mode=signup\">Create an account</a></p>"
                ;
        String content = "<section class=\"login-wrap\"><div class=\"login-card\"><div class=\"brand-mark\">I2IT</div>"
                + "<p class=\"eyebrow\">CAMPUS ADMINISTRATION</p><h1>" + heading + "</h1>"
                + "<p class=\"muted\">" + description + "</p>" + accessNote + message + form + "</div></section>";
        return layout("Sign in", content, null, null, false);
    }

    private String layout(String page, String content, String message, String messageType, boolean authenticated) {
        StringBuilder html = new StringBuilder("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>")
                .append(escape(page)).append(" · CampusDesk</title><style>").append(STYLES)
                .append("</style></head><body><div class=\"voxel-backdrop\" aria-hidden=\"true\">")
                .append("<span class=\"voxel-moon\"></span><span class=\"voxel-cloud cloud-one\"></span>")
                .append("<span class=\"voxel-cloud cloud-two\"></span><span class=\"voxel-tree tree-one\"></span>")
                .append("<span class=\"voxel-tree tree-two\"></span><span class=\"voxel grass voxel-one\"></span>")
                .append("<span class=\"voxel gold voxel-two\"></span><span class=\"voxel stone voxel-three\"></span>")
                .append("<span class=\"voxel grass voxel-four\"></span><span class=\"voxel gold voxel-five\"></span></div>");
        if (authenticated) {
            html.append("<aside class=\"sidebar\"><a class=\"brand\" href=\"/\"><span class=\"brand-mark small\">I2IT</span>")
                    .append("<span>CampusDesk<small>COURSE ADMIN</small></span></a><nav>")
                    .append(nav("Overview", "dashboard", page)).append(nav("Students", "students", page))
                    .append(nav("Courses", "courses", page)).append(nav("Registrations", "registrations", page))
                    .append(nav("Reports", "reports", page)).append("</nav><div class=\"sidebar-bottom\">")
                    .append("<span>Administrator</span><form method=\"post\" action=\"/logout\"><button class=\"logout\" type=\"submit\">Sign out</button></form>")
                    .append("</div></aside><main class=\"main\"><header class=\"topbar\"><div class=\"mobile-brand\">CampusDesk</div>")
                    .append("<span>Student Course Registration System</span><span class=\"user-chip\">AD&nbsp; Admin</span></header>")
                    .append("<div class=\"content\">");
            if (message != null && messageType != null) {
                html.append("<div class=\"alert ").append(escape(messageType)).append("\">").append(escape(message)).append("</div>");
            }
            html.append(content).append("</div></main>");
        } else {
            html.append(content);
        }
        return html.append("</body></html>").toString();
    }

    private static String nav(String label, String pageId, String current) {
        boolean selected = current.equals(pageId) || (pageId.equals("dashboard") && current.equals("Overview"));
        return "<a class=\"nav-link" + (selected ? " active" : "") + "\" href=\"/?page="
                + pageId + "\"><span class=\"nav-dot\"></span>" + label + "</a>";
    }

    private static String value(Map<String, String> form, String key) {
        return form.getOrDefault(key, "").trim();
    }

    private static int integer(Map<String, String> form, String key) {
        try {
            return Integer.parseInt(value(form, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Enter a whole number for " + key + ".");
        }
    }

    private String sessionFrom(HttpExchange exchange) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        for (String cookieHeader : cookies) {
            for (String cookie : cookieHeader.split(";")) {
                String[] pair = cookie.trim().split("=", 2);
                if (pair.length == 2 && pair[0].equals(SESSION_COOKIE) && sessions.contains(pair[1])) return pair[1];
            }
        }
        return null;
    }

    private static Map<String, String> parseParameters(HttpExchange exchange) throws IOException {
        Map<String, String> result = parseQuery(exchange.getRequestURI().getRawQuery());
        if ("POST".equals(exchange.getRequestMethod())) result.putAll(readForm(exchange));
        return result;
    }

    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) throw new IllegalArgumentException("Request is too large.");
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase(java.util.Locale.ROOT)
                .startsWith("application/x-www-form-urlencoded")) {
            throw new IllegalArgumentException("Expected a form submission.");
        }
        return parseQuery(new String(body, StandardCharsets.UTF_8));
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null || query.isEmpty()) return result;
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            String key = separator < 0 ? pair : pair.substring(0, separator);
            String value = separator < 0 ? "" : pair.substring(separator + 1);
            result.put(decode(key), decode(value));
        }
        return result;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String url(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(303, -1);
    }

    private static void send(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static final String STYLES = """
            :root{--navy:#e9f0e4;--ink:#e1e9df;--muted:#99a694}
            *{box-sizing:border-box}body{margin:0;background:linear-gradient(180deg,#0b151d 0%,#111c20 48%,#16221b 100%);color:var(--ink);font:14px/1.5 Inter,"Segoe UI",Arial,sans-serif}
            .voxel-backdrop{position:fixed;inset:0;overflow:hidden;pointer-events:none;z-index:0;background-color:#101a1b;background-image:radial-gradient(1px 1px at 12% 18%,#d8e9c080 98%,transparent),radial-gradient(1px 1px at 78% 12%,#d8e9c080 98%,transparent),radial-gradient(1px 1px at 42% 28%,#d8e9c066 98%,transparent),radial-gradient(1px 1px at 91% 37%,#d8e9c066 98%,transparent),radial-gradient(1px 1px at 28% 62%,#d8e9c066 98%,transparent),linear-gradient(90deg,#ffffff04 1px,transparent 1px),linear-gradient(#ffffff04 1px,transparent 1px);background-size:180px 140px,230px 180px,270px 200px,310px 220px,370px 250px,48px 48px,48px 48px}
            .voxel-backdrop:before{content:"";position:absolute;inset:auto -4% 84px;height:42vh;min-height:220px;background:linear-gradient(145deg,#263b37,#172b2d 64%,#243a32);clip-path:polygon(0 76%,7% 76%,7% 60%,15% 60%,15% 46%,21% 46%,21% 62%,29% 62%,29% 39%,37% 39%,37% 50%,43% 50%,43% 27%,51% 27%,51% 44%,57% 44%,57% 58%,66% 58%,66% 36%,72% 36%,72% 50%,80% 50%,80% 30%,88% 30%,88% 46%,94% 46%,94% 61%,100% 61%,100% 100%,0 100%);opacity:.62}
            .voxel-backdrop:after{content:"";position:absolute;inset:auto 0 0;height:112px;background:linear-gradient(180deg,#18251e 0 8px,#526c37 8px 15px,#30482c 15px 22px,#493a2d 22px 100%);box-shadow:0 -7px 24px #080e0b88;opacity:.86}
            .voxel-moon{position:absolute;top:13%;right:21%;width:52px;height:52px;background:#e4d9a2;clip-path:polygon(25% 0,75% 0,75% 12%,88% 12%,88% 25%,100% 25%,100% 75%,88% 75%,88% 88%,75% 88%,75% 100%,25% 100%,25% 88%,12% 88%,12% 75%,0 75%,0 25%,12% 25%,12% 12%,25% 12%);filter:drop-shadow(0 0 13px #d9d09b55);animation:moon-drift 20s ease-in-out infinite}
            .voxel-cloud{position:absolute;width:32px;height:14px;background:#a9beb378;box-shadow:16px 0 #a9beb378,32px 0 #a9beb378,48px 0 #a9beb378,16px -14px #a9beb378,32px -14px #a9beb378,48px -7px #a9beb378;animation:cloud-drift 52s linear infinite}
            .cloud-one{top:23%;left:-130px}.cloud-two{top:42%;left:-260px;transform:scale(.66);animation-delay:-26s;opacity:.62}
            .voxel-tree{position:absolute;bottom:95px;width:16px;height:48px;background:#61472c;box-shadow:inset -5px 0 #493722}
            .voxel-tree:before{content:"";position:absolute;left:-30px;top:-76px;width:76px;height:82px;background:#385b38;clip-path:polygon(34% 0,66% 0,66% 13%,83% 13%,83% 27%,100% 27%,100% 62%,83% 62%,83% 78%,17% 78%,17% 62%,0 62%,0 27%,17% 27%,17% 13%,34% 13%);box-shadow:inset -9px -6px #2d4a31}
            .voxel-tree:after{content:"";position:absolute;left:-20px;top:-44px;width:56px;height:42px;background:#436b3d;clip-path:polygon(25% 0,75% 0,75% 20%,100% 20%,100% 72%,75% 72%,75% 100%,25% 100%,25% 72%,0 72%,0 20%,25% 20%)}
            .tree-one{left:12%;transform:scale(.8);transform-origin:bottom}.tree-two{right:8%;transform:scale(1.1);transform-origin:bottom;filter:brightness(.82)}
            .voxel{position:absolute;width:28px;height:28px;opacity:.7;animation:block-float 8s steps(12,end) infinite}
            .voxel:before{content:"";position:absolute;inset:-8px 0 auto;height:9px;background:#a0cf70;clip-path:polygon(0 100%,25% 0,100% 0,75% 100%)}
            .voxel:after{content:"";position:absolute;inset:0 -8px 0 auto;width:9px;background:#416642;clip-path:polygon(0 0,100% 20%,100% 100%,0 80%)}
            .voxel.grass{background:#668d48;box-shadow:inset -5px -5px #52743b}
            .voxel.gold{background:#b87b2d;box-shadow:inset -5px -5px #8f5c27}
            .voxel.gold:before{background:#e6ae4e}.voxel.gold:after{background:#80512b}
            .voxel.stone{background:#626c69;box-shadow:inset -5px -5px #4a5353}
            .voxel.stone:before{background:#929a91}.voxel.stone:after{background:#3e4948}
            .voxel-one{left:7%;top:25%;animation-delay:-2s}.voxel-two{right:9%;top:19%;animation-delay:-5s}
            .voxel-three{right:14%;bottom:21%;animation-delay:-1s}.voxel-four{left:18%;bottom:18%;animation-delay:-6s}
            .voxel-five{right:30%;top:10%;width:18px;height:18px;animation-delay:-3s}
            @keyframes block-float{0%,100%{transform:translateY(0)}50%{transform:translateY(-14px)}}
            @keyframes cloud-drift{from{margin-left:0}to{margin-left:calc(100vw + 180px)}}
            @keyframes moon-drift{0%,100%{transform:translateY(0)}50%{transform:translateY(9px)}}
            a{color:inherit;text-decoration:none}.sidebar{position:fixed;inset:0 auto 0 0;width:244px;background:linear-gradient(180deg,#17221d,#101815 76%);border-right:2px solid #2b3b32;color:#fff;padding:24px 16px;display:flex;flex-direction:column;z-index:2}
            .brand{display:flex;align-items:center;gap:12px;padding:3px 9px 32px;font-size:18px;font-weight:700}.brand small{display:block;font-size:9px;letter-spacing:1.6px;color:#a9b69f;margin-top:2px}
            .brand-mark{display:inline-grid;place-items:center;width:64px;height:48px;border-radius:3px;background:linear-gradient(145deg,#ffcf64,#c88635);color:#192017;font-weight:800;font-size:16px;letter-spacing:.5px;box-shadow:0 4px 0 #76542d}
            .brand-mark.small{width:48px;height:38px;border-radius:3px;font-size:12px;letter-spacing:0}nav{display:grid;gap:6px}.nav-link{height:44px;border-radius:3px;padding:11px 12px;color:#b8c5b4;display:flex;gap:12px;align-items:center;transition:background .22s ease,color .22s ease,transform .22s ease}
            .nav-link:hover,.nav-link.active{background:#ffffff0c;color:#fff;transform:translateX(3px)}.nav-link.active{box-shadow:inset 4px 0 #8fcb60;color:#c7e89e}.nav-dot{width:8px;height:8px;border:2px solid currentColor;border-radius:0}
            .sidebar-bottom{margin-top:auto;border-top:2px solid #2b3b32;padding:18px 8px 0;color:#c3d0c0;display:flex;justify-content:space-between;align-items:center;font-size:12px}
            .logout{color:#e4e9f1;background:none;border:0;cursor:pointer;font:inherit}.main{position:relative;z-index:1;margin-left:244px;min-height:100vh}.topbar{height:68px;background:#111a18;border-bottom:2px solid #293a30;display:flex;align-items:center;justify-content:space-between;padding:0 34px;color:var(--muted);font-size:13px}
            .user-chip{border-radius:3px;background:#202d23;color:#ebc16b;padding:7px 11px;font-weight:600;border:1px solid #435239}.content{max-width:1420px;margin:auto;padding:32px 36px 48px;animation:content-enter .55s cubic-bezier(.2,.75,.25,1) both}
            .welcome{padding:8px 0 25px}.eyebrow{font-size:10px;font-weight:700;letter-spacing:1.6px;color:#e6bd61;margin:0 0 7px}.welcome h1,.page-heading h1{margin:0;font-size:28px;letter-spacing:-.5px;color:#edf3e7}
            .welcome>p:last-child,.page-heading p:last-child{margin:6px 0 0;color:var(--muted)}.stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:16px;margin-bottom:22px}
            .stat-card,.panel{background:linear-gradient(145deg,#1b2824,#17221f);border:2px solid #35463a;border-radius:3px;box-shadow:0 5px 0 #0a100d,0 9px 22px #0005}.stat-card{padding:20px 22px;display:grid;gap:8px;transition:transform .28s steps(5,end),box-shadow .28s,border-color .28s;animation:card-enter .55s cubic-bezier(.2,.75,.25,1) both}
            .stat-card:nth-child(2){animation-delay:.08s}.stat-card:nth-child(3){animation-delay:.16s}.stat-card:hover{transform:translateY(-5px);box-shadow:0 9px 0 #0a100d,0 14px 30px #0008;border-color:#779653}.stat-card span,.stat-card small{color:#a9b6a5}.stat-card strong{font-size:31px;color:#edf3e7;line-height:1.1}.stat-card small{font-size:11px}
            .panel{padding:21px 22px;margin-bottom:20px;animation:card-enter .5s .1s cubic-bezier(.2,.75,.25,1) both}.panel h2{margin:0;font-size:16px;color:#edf3e7}.section-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:17px}
            .section-heading p,.muted{margin:4px 0 0;color:var(--muted);font-size:12px}.text-link{color:#ffc247;font-weight:600;font-size:12px;transition:color .2s}.text-link:hover{color:#ffe09a}.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:22px}
            .form-panel{padding-bottom:20px}.form-panel>h2{margin-bottom:17px}.form-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px}
            .field{display:grid;gap:6px;color:#bdc8b5;font-size:12px;font-weight:600}.field input,.field select,.toolbar-form input,.toolbar-form select{width:100%;height:38px;border:2px solid #34443a;border-radius:2px;background:#111a17;padding:0 10px;color:#edf3e7;font:inherit;font-size:13px;outline:none;transition:border-color .2s,box-shadow .2s}
            .field select option,.toolbar-form select option{background:#111a17;color:#edf3e7}.field input:focus,.field select:focus,.toolbar-form input:focus,.toolbar-form select:focus{border-color:#8fbd5d;box-shadow:0 0 0 3px #86bd581c}.field input[readonly]{background:#1b2824;color:var(--muted)}
            .form-actions{grid-column:1/-1;display:flex;gap:8px;align-items:center;padding-top:2px}.button{height:36px;padding:0 13px;border:2px solid #39493b;border-radius:2px;background:#202b23;color:#dce6d7;display:inline-flex;align-items:center;justify-content:center;font-weight:600;font-size:12px;cursor:pointer;transition:transform .18s steps(3,end),box-shadow .18s,background .18s}
            .button:hover{background:#29392b;transform:translateY(-2px);box-shadow:0 3px 0 #0b100c}.button.primary{background:linear-gradient(180deg,#a2d36b,#70a94b);border-color:#b7df83;color:#142013;box-shadow:0 4px 0 #456833}.button.primary:hover{background:linear-gradient(180deg,#b4e17e,#85bc58);box-shadow:0 5px 0 #456833}
            .toolbar-form{display:flex;gap:7px;align-items:center}.toolbar-form input{width:220px}.toolbar-form select{width:210px}
            .table-wrap{width:100%;overflow:auto}table{width:100%;border-collapse:collapse;text-align:left;white-space:nowrap}th{background:#121b17;color:#aab7a4;font-size:10px;text-transform:uppercase;letter-spacing:.55px;font-weight:700}
            th,td{padding:11px 12px;border-bottom:1px solid #2a3930}td{font-size:12px;color:#c2cec0}td strong{color:#edf3e7;font-weight:600}tbody tr{transition:background .18s}tbody tr:hover{background:#ffffff08}tbody tr:last-child td{border-bottom:0}
            .badge{display:inline-block;padding:3px 9px;border-radius:2px;background:#263b28;color:#a9d979;font-weight:700;font-size:11px}.row-actions{display:flex;align-items:center;gap:12px}.inline-form{display:inline}.text-button{border:0;padding:0;background:none;cursor:pointer;font:inherit;font-size:12px;font-weight:600}.danger{color:#ff9690}
            .alert{padding:11px 14px;border-radius:8px;margin-bottom:17px;font-size:13px;animation:card-enter .28s ease-out both}.alert.notice{background:#142c28;color:#65d9ba;border:1px solid #245044}.alert.error{background:#33191c;color:#ffaaa4;border:1px solid #5a282b}
            .confirm-card{max-width:540px;margin:40px auto;padding:30px}.confirm-card h1{margin:0;color:#edf3e7;font-size:24px}.confirm-card>p:not(.eyebrow){color:var(--muted);margin:10px 0 22px}.danger-button{background:#9b443b;border-color:#c56b52;color:#fff}
            .login-wrap{position:relative;z-index:1;min-height:100vh;display:grid;place-items:center;padding:24px;background:radial-gradient(ellipse at 78% 22%,#1b2b24aa,transparent 42%),transparent}
            .login-card{width:min(100%,410px);background:linear-gradient(145deg,#1b2824,#141d1a);border:2px solid #35463a;border-radius:3px;box-shadow:0 7px 0 #080d0b,0 20px 70px #0008;padding:34px;animation:login-enter .65s cubic-bezier(.2,.75,.25,1) both}
            .login-card .brand-mark{margin-bottom:24px}.login-card h1{margin:0;color:#f1f4fa;font-size:26px}.login-card>.muted{margin:7px 0 22px;font-size:13px}
            .login-form{display:grid;gap:15px}.login-form .button{margin-top:2px;height:42px}.account-switch{text-align:center;margin:18px 0 0;color:var(--muted);font-size:12px}.account-switch a{color:#ffca63;font-weight:700}.account-switch a:hover{color:#ffe19e}.access-note{padding:9px 11px;border-left:3px solid #d2a44c;background:#26251d;color:#dfc78c;font-size:11px;margin:0 0 17px}
            .mobile-brand{display:none}.mobile-brand{color:#edf3e7}
            @keyframes content-enter{from{opacity:0;transform:translateY(9px)}to{opacity:1;transform:translateY(0)}}
            @keyframes card-enter{from{opacity:0;transform:translateY(13px)}to{opacity:1;transform:translateY(0)}}
            @keyframes login-enter{from{opacity:0;transform:translateY(18px) scale(.985)}to{opacity:1;transform:translateY(0) scale(1)}}
            @media(prefers-reduced-motion:reduce){*,*::before,*::after{scroll-behavior:auto!important;animation-duration:.01ms!important;animation-iteration-count:1!important;transition-duration:.01ms!important}.voxel,.voxel-cloud,.voxel-moon{display:none}}
            @media(max-width:980px){.sidebar{width:205px}.main{margin-left:205px}.content{padding:25px 22px}.form-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.section-heading{align-items:flex-start;flex-direction:column}.toolbar-form{flex-wrap:wrap}}
            @media(max-width:650px){.sidebar{position:static;width:auto;height:auto;padding:10px 12px;display:block}.brand{padding:0 5px 10px}.brand-mark.small{width:42px;height:30px;font-size:10px}.brand small{display:none}.sidebar nav{display:flex;overflow:auto}.nav-link{flex:none;height:36px;padding:8px 10px;font-size:12px}.nav-link.active{box-shadow:inset 0 -2px #73b2ff}.sidebar-bottom{display:none}.main{margin:0}.topbar{height:50px;padding:0 16px}.topbar>span:first-of-type{display:none}.mobile-brand{display:block;color:var(--navy);font-weight:700}.content{padding:22px 14px}.stats{grid-template-columns:1fr;gap:10px}.stat-card{padding:14px 17px}.stat-card strong{font-size:25px}.welcome h1,.page-heading h1{font-size:24px}.form-grid{grid-template-columns:1fr}.panel{padding:16px 14px}.toolbar-form{width:100%}.toolbar-form input,.toolbar-form select{width:100%;flex:1 1 100%}.section-heading{width:100%}.login-card{padding:27px 23px}}
            """;
}
