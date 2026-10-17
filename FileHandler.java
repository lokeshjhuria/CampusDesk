package college.registration;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class FileHandler {
    private static final int PASSWORD_ITERATIONS = 120_000;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final Path studentsFile;
    private final Path coursesFile;
    private final Path registrationsFile;
    private final Path usersFile;

    public FileHandler() throws IOException { this(Path.of("data")); }

    public FileHandler(Path dataDirectory) throws IOException {
        Files.createDirectories(dataDirectory);
        studentsFile = dataDirectory.resolve("students.txt");
        coursesFile = dataDirectory.resolve("courses.txt");
        registrationsFile = dataDirectory.resolve("registrations.txt");
        usersFile = dataDirectory.resolve("users.txt");
        createIfMissing(studentsFile);
        createIfMissing(coursesFile);
        createIfMissing(registrationsFile);
        createIfMissing(usersFile);
        seedSamplesIfEmpty();
    }

    private static void createIfMissing(Path file) throws IOException {
        if (Files.notExists(file)) Files.createFile(file);
    }

    private void seedSamplesIfEmpty() throws IOException {
        if (readRows(studentsFile).isEmpty()) {
            writeRows(studentsFile, List.of(
                    List.of("STU001", "Aarav Sharma", "aarav@example.com", "9876543210", "Computer Science", "3"),
                    List.of("STU002", "Diya Patel", "diya@example.com", "9876543211", "Electronics", "2"),
                    List.of("STU003", "Kabir Singh", "kabir@example.com", "9876543212", "Mechanical", "4")));
        }
        if (readRows(coursesFile).isEmpty()) {
            writeRows(coursesFile, List.of(
                    List.of("CRS001", "Data Structures", "CS201", "Computer Science", "Dr. Meera Rao", "4", "30"),
                    List.of("CRS002", "Digital Electronics", "EC202", "Electronics", "Prof. Anil Shah", "3", "25"),
                    List.of("CRS003", "Engineering Mechanics", "ME203", "Mechanical", "Dr. Neha Joshi", "4", "20"),
                    List.of("CRS004", "Linear Algebra", "MA204", "Mathematics", "Prof. R. Iyer", "3", "40"),
                    List.of("CRS005", "Technical Writing", "HU205", "Humanities", "Dr. Sana Khan", "2", "35")));
        }
        if (readRows(usersFile).isEmpty()) {
            writeRows(usersFile, List.of(List.of("admin", passwordHash("admin123"))));
        }
    }

    public List<Student> readStudents() throws IOException {
        List<Student> records = new ArrayList<>();
        for (List<String> row : readRows(studentsFile)) {
            requireColumns(row, 6, studentsFile);
            try {
                records.add(new Student(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4), Integer.parseInt(row.get(5))));
            } catch (IllegalArgumentException e) {
                throw malformed(studentsFile, e);
            }
        }
        return records;
    }

    public List<Course> readCourses() throws IOException {
        List<Course> records = new ArrayList<>();
        for (List<String> row : readRows(coursesFile)) {
            requireColumns(row, 7, coursesFile);
            try {
                records.add(new Course(row.get(0), row.get(1), row.get(2), row.get(3), row.get(4),
                        Integer.parseInt(row.get(5)), Integer.parseInt(row.get(6))));
            } catch (IllegalArgumentException e) {
                throw malformed(coursesFile, e);
            }
        }
        return records;
    }

    public List<Registration> readRegistrations() throws IOException {
        List<Registration> records = new ArrayList<>();
        for (List<String> row : readRows(registrationsFile)) {
            requireColumns(row, 4, registrationsFile);
            try {
                records.add(new Registration(row.get(0), row.get(1), row.get(2), java.time.LocalDate.parse(row.get(3))));
            } catch (RuntimeException e) {
                throw malformed(registrationsFile, e);
            }
        }
        return records;
    }

    public void writeStudents(List<Student> records) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Student s : records) rows.add(List.of(s.getId(), s.getFullName(), s.getEmail(), s.getPhone(), s.getDepartment(), String.valueOf(s.getSemester())));
        writeRows(studentsFile, rows);
    }

    public void writeCourses(List<Course> records) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Course c : records) rows.add(List.of(c.getId(), c.getName(), c.getCode(), c.getDepartment(),
                c.getInstructor(), String.valueOf(c.getCreditHours()), String.valueOf(c.getCapacity())));
        writeRows(coursesFile, rows);
    }

    public void writeRegistrations(List<Registration> records) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        for (Registration r : records) rows.add(List.of(r.getId(), r.getStudentId(), r.getCourseId(), r.getRegisteredOn().toString()));
        writeRows(registrationsFile, rows);
    }

    public void appendStudent(Student student) throws IOException {
        appendRow(studentsFile, List.of(student.getId(), student.getFullName(), student.getEmail(), student.getPhone(),
                student.getDepartment(), String.valueOf(student.getSemester())));
    }

    public void appendCourse(Course course) throws IOException {
        appendRow(coursesFile, List.of(course.getId(), course.getName(), course.getCode(), course.getDepartment(),
                course.getInstructor(), String.valueOf(course.getCreditHours()), String.valueOf(course.getCapacity())));
    }

    public void appendRegistration(Registration registration) throws IOException {
        appendRow(registrationsFile, List.of(registration.getId(), registration.getStudentId(),
                registration.getCourseId(), registration.getRegisteredOn().toString()));
    }

    public synchronized boolean authenticate(String username, String password) throws IOException {
        List<List<String>> rows = readRows(usersFile);
        for (int i = 0; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            requireColumns(row, 2, usersFile);
            if (row.get(0).equals(username) && verifyPassword(password, row.get(1))) {
                if (!row.get(1).startsWith("pbkdf2-sha256$")) {
                    rows.set(i, List.of(row.get(0), passwordHash(password)));
                    writeRows(usersFile, rows);
                }
                return true;
            }
        }
        return false;
    }

    public synchronized void createAccount(String username, String password) throws IOException {
        String normalizedUsername = username == null ? "" : username.trim();
        if (!normalizedUsername.matches("[A-Za-z0-9_.-]{3,24}")) {
            throw new IllegalArgumentException("Username must be 3-24 characters and use only letters, numbers, dots, dashes, or underscores.");
        }
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Password must be between 8 and 128 characters.");
        }
        for (List<String> row : readRows(usersFile)) {
            requireColumns(row, 2, usersFile);
            if (row.get(0).equalsIgnoreCase(normalizedUsername)) {
                throw new IllegalArgumentException("That username is already taken.");
            }
        }
        appendRow(usersFile, List.of(normalizedUsername, passwordHash(password)));
    }

    private static String passwordHash(String password) {
        try {
            byte[] salt = new byte[16];
            SECURE_RANDOM.nextBytes(salt);
            byte[] derived = derivePassword(password, salt, PASSWORD_ITERATIONS);
            return "pbkdf2-sha256$" + PASSWORD_ITERATIONS + "$"
                    + HexFormat.of().formatHex(salt) + "$" + HexFormat.of().formatHex(derived);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 password hashing is unavailable.", e);
        }
    }

    private static boolean verifyPassword(String password, String storedHash) throws IOException {
        if (storedHash.startsWith("pbkdf2-sha256$")) {
            String[] parts = storedHash.split("\\$", -1);
            if (parts.length != 4) throw new IOException("Invalid password hash in " + "users.txt.");
            try {
                int iterations = Integer.parseInt(parts[1]);
                byte[] salt = HexFormat.of().parseHex(parts[2]);
                byte[] expected = HexFormat.of().parseHex(parts[3]);
                if (iterations < 10_000 || iterations > 2_000_000 || salt.length < 16 || expected.length != 32) {
                    throw new IOException("Invalid password hash parameters in users.txt.");
                }
                byte[] actual = derivePassword(password, salt, iterations);
                return MessageDigest.isEqual(expected, actual);
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid password hash in users.txt.", e);
            } catch (java.security.GeneralSecurityException e) {
                throw new IOException("Could not verify password hash.", e);
            }
        }
        return MessageDigest.isEqual(storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                legacyPasswordHash(password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static byte[] derivePassword(String password, byte[] salt, int iterations)
            throws java.security.GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PASSWORD_KEY_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } finally {
            spec.clearPassword();
        }
    }

    private static String legacyPasswordHash(String password) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private static IOException malformed(Path file, Exception cause) {
        return new IOException("Invalid record in " + file + ": " + cause.getMessage(), cause);
    }

    private static void requireColumns(List<String> row, int expected, Path file) throws IOException {
        if (row.size() != expected) throw new IOException("Invalid record in " + file + ": expected " + expected + " fields, found " + row.size() + ".");
    }

    private static List<List<String>> readRows(Path file) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file.toFile(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (!line.isEmpty()) {
                    try {
                        rows.add(parseCsv(line));
                    } catch (IllegalArgumentException e) {
                        throw new IOException("Invalid CSV at " + file + ":" + lineNumber + ".", e);
                    }
                }
            }
        }
        return rows;
    }

    private static void writeRows(Path file, List<List<String>> rows) throws IOException {
        Path temporaryFile = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(temporaryFile.toFile(), java.nio.charset.StandardCharsets.UTF_8))) {
                for (List<String> row : rows) {
                    writer.write(formatCsv(row));
                    writer.newLine();
                }
            }
            try {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void appendRow(Path file, List<String> row) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file.toFile(), java.nio.charset.StandardCharsets.UTF_8, true))) {
            writer.write(formatCsv(row));
            writer.newLine();
        }
    }

    private static String formatCsv(List<String> fields) {
        List<String> escaped = new ArrayList<>();
        for (String field : fields) {
            if (field.indexOf('\n') >= 0 || field.indexOf('\r') >= 0) throw new IllegalArgumentException("CSV fields cannot contain line breaks.");
            escaped.add("\"" + field.replace("\"", "\"\"") + "\"");
        }
        return String.join(",", escaped);
    }

    private static List<String> parseCsv(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '"' && field.length() == 0) {
                quoted = true;
            } else {
                field.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unclosed quoted field.");
        fields.add(field.toString());
        return fields;
    }
}
