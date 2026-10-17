package college.registration;

public class Main {
    public static void main(String[] args) {
        try {
            String configuredPort = System.getenv("PORT");
            int port = args.length > 0
                    ? Integer.parseInt(args[0])
                    : configuredPort == null || configuredPort.isBlank() ? 8080 : Integer.parseInt(configuredPort);
            String host = System.getenv().getOrDefault("HOST", "127.0.0.1");
            FileHandler fileHandler = new FileHandler();
            StudentManager students = new StudentManager(fileHandler);
            CourseManager courses = new CourseManager(fileHandler);
            RegistrationManager registrations = new RegistrationManager(fileHandler, students, courses);
            WebServer server = new WebServer(fileHandler, students, courses, registrations, port, host);
            server.start();
            System.out.println("CampusDesk website is listening on " + host + ":" + server.getPort());
            System.out.println("Press Ctrl+C to stop the server.");
        } catch (Exception e) {
            System.err.println("CampusDesk could not start: " + e.getMessage());
            System.exit(1);
        }
    }
}
