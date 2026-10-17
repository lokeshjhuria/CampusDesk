package college.registration;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RegistrationManager {
    private final FileHandler fileHandler;
    private final StudentManager studentManager;
    private final CourseManager courseManager;
    private final List<Registration> registrations;

    public RegistrationManager(FileHandler fileHandler, StudentManager studentManager, CourseManager courseManager) throws IOException {
        this.fileHandler = fileHandler;
        this.studentManager = studentManager;
        this.courseManager = courseManager;
        registrations = new ArrayList<>(fileHandler.readRegistrations());
        validateLoadedRecords();
    }

    private void validateLoadedRecords() throws IOException {
        List<String> enrolledPairs = new ArrayList<>();
        for (Registration registration : registrations) {
            if (!studentManager.exists(registration.getStudentId()) || !courseManager.exists(registration.getCourseId())) {
                throw new IOException("Registration " + registration.getId() + " refers to a missing student or course.");
            }
            String pair = registration.getStudentId().toLowerCase(java.util.Locale.ROOT) + "|"
                    + registration.getCourseId().toLowerCase(java.util.Locale.ROOT);
            if (enrolledPairs.contains(pair)) throw new IOException("Duplicate registration found for " + pair + ".");
            enrolledPairs.add(pair);
        }
        for (Course course : courseManager.getAll()) {
            if (getEnrollmentCount(course.getId()) > course.getCapacity()) {
                throw new IOException("Course " + course.getCode() + " exceeds its configured capacity.");
            }
        }
    }

    public List<Registration> getAll() { return List.copyOf(registrations); }

    public boolean exists(String id) {
        for (Registration registration : registrations) if (registration.getId().equalsIgnoreCase(id)) return true;
        return false;
    }

    public Registration register(String studentId, String courseId) throws IOException {
        Student student = studentManager.findById(studentId);
        Course course = courseManager.findById(courseId);
        if (student == null) throw new IllegalArgumentException("Select a valid student.");
        if (course == null) throw new IllegalArgumentException("Select a valid course.");
        for (Registration registration : registrations) {
            if (registration.getStudentId().equalsIgnoreCase(studentId)
                    && registration.getCourseId().equalsIgnoreCase(courseId)) {
                throw new IllegalArgumentException("This student is already registered for the selected course.");
            }
        }
        if (getEnrollmentCount(courseId) >= course.getCapacity()) throw new IllegalArgumentException("The selected course is full.");
        String registrationId;
        do {
            registrationId = "REG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
        } while (exists(registrationId));
        Registration registration = new Registration(registrationId, studentId, courseId, LocalDate.now());
        fileHandler.appendRegistration(registration);
        registrations.add(registration);
        return registration;
    }

    public void cancel(String registrationId) throws IOException {
        Registration existing = findById(registrationId);
        if (existing == null) throw new IllegalArgumentException("Registration not found.");
        List<Registration> updated = new ArrayList<>(registrations);
        updated.remove(existing);
        fileHandler.writeRegistrations(updated);
        registrations.clear();
        registrations.addAll(updated);
    }

    public void deleteStudent(String studentId) throws IOException {
        if (hasStudentRegistrations(studentId)) {
            throw new IllegalArgumentException("Cancel this student's registrations before deleting the student.");
        }
        studentManager.delete(studentId);
    }

    public void deleteCourse(String courseId) throws IOException {
        if (hasCourseRegistrations(courseId)) {
            throw new IllegalArgumentException("Cancel the course's active registrations before deleting it.");
        }
        courseManager.delete(courseId);
    }

    public void updateCourse(Course updatedCourse) throws IOException {
        Course current = courseManager.findById(updatedCourse.getId());
        if (current == null) throw new IllegalArgumentException("Course not found.");
        if (updatedCourse.getCapacity() < getEnrollmentCount(current.getId())) {
            throw new IllegalArgumentException("Capacity cannot be lower than the current enrollment count.");
        }
        courseManager.update(updatedCourse);
    }

    public Registration findById(String id) {
        for (Registration registration : registrations) if (registration.getId().equalsIgnoreCase(id)) return registration;
        return null;
    }

    public boolean hasStudentRegistrations(String studentId) {
        for (Registration registration : registrations) if (registration.getStudentId().equalsIgnoreCase(studentId)) return true;
        return false;
    }

    public boolean hasCourseRegistrations(String courseId) {
        for (Registration registration : registrations) if (registration.getCourseId().equalsIgnoreCase(courseId)) return true;
        return false;
    }

    public int getEnrollmentCount(String courseId) {
        int count = 0;
        for (Registration registration : registrations) if (registration.getCourseId().equalsIgnoreCase(courseId)) count++;
        return count;
    }

    public List<Registration> getForStudent(String studentId) {
        List<Registration> result = new ArrayList<>();
        for (Registration registration : registrations) {
            if (registration.getStudentId().equalsIgnoreCase(studentId)) result.add(registration);
        }
        return result;
    }
}
