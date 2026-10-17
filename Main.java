package college.registration;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        try {
            String configuredPort = System.getenv("PORT");
            int port = args.length > 0
                    ? Integer.parseInt(args[0])
                    : configuredPort == null || configuredPort.isBlank() ? 8080 : Integer.parseInt(configuredPort);
            String defaultHost = (configuredPort != null && !configuredPort.isBlank()) ? "0.0.0.0" : "127.0.0.1";
            String host = System.getenv().getOrDefault("HOST", defaultHost);
            String dataDir = System.getenv("DATA_DIR");
            FileHandler fileHandler = (dataDir != null && !dataDir.isBlank())
                    ? new FileHandler(Path.of(dataDir))
                    : new FileHandler();
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
