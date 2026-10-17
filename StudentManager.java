package college.registration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public class StudentManager {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private final FileHandler fileHandler;
    private final List<Student> students;

    public StudentManager(FileHandler fileHandler) throws IOException {
        this.fileHandler = fileHandler;
        students = new ArrayList<>(fileHandler.readStudents());
        validateLoadedRecords();
    }

    private void validateLoadedRecords() throws IOException {
        for (int i = 0; i < students.size(); i++) {
            Student current = students.get(i);
            if (!EMAIL_PATTERN.matcher(current.getEmail()).matches()) {
                throw new IOException("Invalid email for student " + current.getId() + ".");
            }
            for (int j = 0; j < i; j++) {
                if (students.get(j).getId().equalsIgnoreCase(current.getId())) {
                    throw new IOException("Duplicate student ID in data file: " + current.getId());
                }
            }
        }
    }

    public List<Student> getAll() { return List.copyOf(students); }

    public boolean exists(String id) { return findById(id) != null; }

    public Student findById(String id) {
        for (Student student : students) if (student.getId().equalsIgnoreCase(id)) return student;
        return null;
    }

    public List<Student> search(String query) {
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Student> matches = new ArrayList<>();
        for (Student student : students) {
            if (student.getId().toLowerCase(Locale.ROOT).contains(value)
                    || student.getFullName().toLowerCase(Locale.ROOT).contains(value)) matches.add(student);
        }
        return matches;
    }

    public void add(Student student) throws IOException {
        validateStudent(student);
        if (exists(student.getId())) throw new IllegalArgumentException("A student with this ID already exists.");
        fileHandler.appendStudent(student);
        students.add(student);
    }

    public void update(Student updatedStudent) throws IOException {
        validateStudent(updatedStudent);
        Student existing = findById(updatedStudent.getId());
        if (existing == null) throw new IllegalArgumentException("Student not found.");
        List<Student> updated = new ArrayList<>(students);
        updated.set(updated.indexOf(existing), updatedStudent);
        fileHandler.writeStudents(updated);
        students.clear();
        students.addAll(updated);
    }

    public void delete(String id) throws IOException {
        Student existing = findById(id);
        if (existing == null) throw new IllegalArgumentException("Student not found.");
        List<Student> updated = new ArrayList<>(students);
        updated.remove(existing);
        fileHandler.writeStudents(updated);
        students.clear();
        students.addAll(updated);
    }

    private void validateStudent(Student student) {
        if (!EMAIL_PATTERN.matcher(student.getEmail()).matches()) {
            throw new IllegalArgumentException("Enter a valid email address.");
        }
    }
}
