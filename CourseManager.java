package college.registration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CourseManager {
    private final FileHandler fileHandler;
    private final List<Course> courses;

    public CourseManager(FileHandler fileHandler) throws IOException {
        this.fileHandler = fileHandler;
        courses = new ArrayList<>(fileHandler.readCourses());
        validateLoadedRecords();
    }

    private void validateLoadedRecords() throws IOException {
        for (int i = 0; i < courses.size(); i++) {
            for (int j = 0; j < i; j++) {
                Course prior = courses.get(j);
                Course current = courses.get(i);
                if (prior.getId().equalsIgnoreCase(current.getId())) {
                    throw new IOException("Duplicate course ID in data file: " + current.getId());
                }
                if (prior.getCode().equalsIgnoreCase(current.getCode())) {
                    throw new IOException("Duplicate course code in data file: " + current.getCode());
                }
            }
        }
    }

    public List<Course> getAll() { return List.copyOf(courses); }

    public boolean exists(String id) { return findById(id) != null; }

    public Course findById(String id) {
        for (Course course : courses) if (course.getId().equalsIgnoreCase(id)) return course;
        return null;
    }

    public Course findByCode(String code) {
        for (Course course : courses) if (course.getCode().equalsIgnoreCase(code)) return course;
        return null;
    }

    public List<Course> search(String query) {
        String value = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Course> matches = new ArrayList<>();
        for (Course course : courses) {
            if (course.getId().toLowerCase(Locale.ROOT).contains(value)
                    || course.getCode().toLowerCase(Locale.ROOT).contains(value)
                    || course.getName().toLowerCase(Locale.ROOT).contains(value)) matches.add(course);
        }
        return matches;
    }

    public void add(Course course) throws IOException {
        if (exists(course.getId())) throw new IllegalArgumentException("A course with this ID already exists.");
        if (findByCode(course.getCode()) != null) throw new IllegalArgumentException("A course with this code already exists.");
        fileHandler.appendCourse(course);
        courses.add(course);
    }

    public void update(Course updatedCourse) throws IOException {
        Course existing = findById(updatedCourse.getId());
        if (existing == null) throw new IllegalArgumentException("Course not found.");
        Course codeOwner = findByCode(updatedCourse.getCode());
        if (codeOwner != null && codeOwner != existing) throw new IllegalArgumentException("A course with this code already exists.");
        List<Course> updated = new ArrayList<>(courses);
        updated.set(updated.indexOf(existing), updatedCourse);
        fileHandler.writeCourses(updated);
        courses.clear();
        courses.addAll(updated);
    }

    public void delete(String id) throws IOException {
        Course existing = findById(id);
        if (existing == null) throw new IllegalArgumentException("Course not found.");
        List<Course> updated = new ArrayList<>(courses);
        updated.remove(existing);
        fileHandler.writeCourses(updated);
        courses.clear();
        courses.addAll(updated);
    }
}
