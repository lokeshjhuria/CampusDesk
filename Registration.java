package college.registration;

import java.time.LocalDate;

public class Registration {
    private final String id;
    private final String studentId;
    private final String courseId;
    private final LocalDate registeredOn;

    public Registration(String id, String studentId, String courseId, LocalDate registeredOn) {
        this.id = Person.requireText(id, "Registration ID");
        this.studentId = Person.requireText(studentId, "Student ID");
        this.courseId = Person.requireText(courseId, "Course ID");
        if (registeredOn == null) throw new IllegalArgumentException("Registration date is required.");
        this.registeredOn = registeredOn;
    }

    public String getId() { return id; }
    public String getStudentId() { return studentId; }
    public String getCourseId() { return courseId; }
    public LocalDate getRegisteredOn() { return registeredOn; }
}
