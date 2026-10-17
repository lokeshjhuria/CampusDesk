package college.registration;

public class Main {
    public static void main(String[] args) {
        try {
            int port = args.length == 0 ? 8080 : Integer.parseInt(args[0]);
            FileHandler fileHandler = new FileHandler();
            StudentManager students = new StudentManager(fileHandler);
            CourseManager courses = new CourseManager(fileHandler);
            RegistrationManager registrations = new RegistrationManager(fileHandler, students, courses);
            WebServer server = new WebServer(fileHandler, students, courses, registrations, port);
            server.start();
            System.out.println("CampusDesk website is running at http://localhost:" + server.getPort());
            System.out.println("Press Ctrl+C to stop the server.");
        } catch (Exception e) {
            System.err.println("CampusDesk could not start: " + e.getMessage());
            System.exit(1);
        }
    }
}
