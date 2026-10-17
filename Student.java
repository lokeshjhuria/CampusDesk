package college.registration;

public class Student extends Person {
    private String department;
    private int semester;

    public Student(String id, String fullName, String email, String phone, String department, int semester) {
        super(id, fullName, email, phone);
        setDepartment(department);
        setSemester(semester);
    }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = requireText(department, "Department"); }
    public int getSemester() { return semester; }
    public void setSemester(int semester) {
        if (semester < 1 || semester > 12) {
            throw new IllegalArgumentException("Semester must be between 1 and 12.");
        }
        this.semester = semester;
    }

    @Override
    public String getSummary() {
        return getId() + " - " + getFullName() + " (" + department + ", semester " + semester + ")";
    }

    @Override
    public String toString() { return getSummary(); }
}
