package college.registration;

public final class Course {
    private String id;
    private String name;
    private String code;
    private String department;
    private String instructor;
    private int creditHours;
    private int capacity;

    public Course(String id, String name, String code, String department,
                  String instructor, int creditHours, int capacity) {
        setId(id);
        setName(name);
        setCode(code);
        setDepartment(department);
        setInstructor(instructor);
        setCreditHours(creditHours);
        setCapacity(capacity);
    }

    private static String text(String value, String label) {
        return Person.requireText(value, label);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = text(id, "Course ID"); }
    public String getName() { return name; }
    public void setName(String name) { this.name = text(name, "Course name"); }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = text(code, "Course code").toUpperCase(java.util.Locale.ROOT); }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = text(department, "Department"); }
    public String getInstructor() { return instructor; }
    public void setInstructor(String instructor) { this.instructor = text(instructor, "Instructor"); }
    public int getCreditHours() { return creditHours; }
    public void setCreditHours(int creditHours) {
        if (creditHours < 1 || creditHours > 12) throw new IllegalArgumentException("Credit hours must be between 1 and 12.");
        this.creditHours = creditHours;
    }
    public int getCapacity() { return capacity; }
    public void setCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Course capacity must be at least 1.");
        this.capacity = capacity;
    }
    @Override
    public String toString() { return code + " - " + name; }
}
