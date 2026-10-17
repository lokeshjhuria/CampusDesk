package college.registration;

public abstract class Person {
    private String id;
    private String fullName;
    private String email;
    private String phone;

    protected Person(String id, String fullName, String email, String phone) {
        setId(id);
        setFullName(fullName);
        setEmail(email);
        setPhone(phone);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = requireText(id, "ID"); }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = requireText(fullName, "Full name"); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = requireText(email, "Email"); }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = requireText(phone, "Phone"); }

    public abstract String getSummary();

    protected static String requireText(String value, String label) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            throw new IllegalArgumentException(label + " cannot contain a line break.");
        }
        return value.trim();
    }
}
