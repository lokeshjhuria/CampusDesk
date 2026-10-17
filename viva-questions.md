# Viva Questions and Answers

## Java and OOP

**1. What is a class and what is an object?**  
A class defines fields and behavior; an object is a runtime instance of that class. `Student` is a class, and each enrolled student record is an object.

**2. How does this project demonstrate encapsulation?**  
Model fields are private, and public getters/setters control access. Setters validate values such as semester and course capacity.

**3. Explain inheritance in the project.**  
`Student` extends the abstract `Person` class and reuses its ID, name, email, and phone fields.

**4. What is polymorphism? Give an example from the code.**  
Polymorphism allows the same operation to behave according to an object's concrete type. `Student` overrides `Person.getSummary()`, so the student-specific implementation runs when called through a `Person` reference.

**5. Where is abstraction used?**  
`Person` is abstract because shared person state is meaningful but a generic person record is incomplete. The concrete `Student` class supplies the student-specific implementation.

**6. What is the association between registration, student, and course?**  
A registration stores student and course IDs. The registration manager resolves them against the student and course managers, avoiding duplicated full records.

**7. Why use `ArrayList`?**  
It provides a straightforward resizable in-memory collection for records read from files and supports iteration, search, and table refreshes.

**8. What is a constructor?**  
A constructor initializes a new object. Parameterized constructors in `Student` and `Course` establish required values and apply validation immediately.

## Website and HTTP

**9. How does the website run without Spring Boot or another framework?**  
`WebServer` uses the HTTP server included in the JDK. It renders HTML pages and accepts standard URL-encoded HTML form submissions.

**10. How does a browser interact with the Java application?**  
The browser sends GET requests for pages and POST requests for form actions. `WebServer` parses the form values and calls the existing Java manager classes.

**11. How is login maintained between page requests?**  
After valid credentials, the server issues a random session token in an HTTP-only cookie and keeps active tokens in memory. Signing out invalidates the token.

**12. Why use HTML forms instead of a JavaScript front end?**  
HTML forms provide the required interactive operations with no frontend framework or extra dependencies, keeping the project straightforward to explain and run.

## Files and validation

**13. How are records persisted without a database?**  
`FileHandler` reads and writes one CSV record per line in the local `data` directory. Managers update their in-memory lists after successful file operations.

**14. How are commas and quotes in values handled?**  
CSV fields are quoted and embedded quote characters are doubled. The parser reverses that escaping when reading.

**15. How are updates and deletes made safe?**  
The application writes the full updated collection to a temporary file, then replaces the original file, preferring an atomic move where supported.

**16. Why are registrations cancelled before deleting a student or course?**  
Registrations refer to student/course IDs. Blocking deletion preserves referential integrity instead of leaving invalid records.

**17. How does the application prevent over-enrollment?**  
Before appending a registration, the manager counts active registrations for the course and compares the count with its capacity.

**18. How are account passwords stored?**  
New accounts use a unique random salt and PBKDF2-HMAC-SHA256 hash rather than storing password text. The demo includes a one-time upgrade path for older SHA-256-only records. Production systems should also add TLS, rate limits, account recovery protections, and managed secrets.

**19. How should I run the automated workflow test?**  
Compile the main and test sources together, then run `college.registration.RegistrationSystemTest`. It uses an isolated temporary folder so project demo data is unaffected.

**20. What improvements would you make for production?**  
Use managed accounts and stronger password hashing, add audit and backup features, test the UI automatically, and consider a database if multiple users need concurrent access.

**21. What access does a newly created account have?**  
This project gives each self-registered account the same administrator access as the demo account. Open sign-up should be restricted or paired with roles before deploying a real registration system.
