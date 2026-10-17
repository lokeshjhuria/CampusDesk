# Test Cases

## Automated workflow

Run `college.registration.RegistrationSystemTest` using the README's test command. It creates isolated temporary data directories, exercises manager operations and the HTTP website workflow, checks persistence after reload, and removes its temporary files.

## Manual functional test cases

| ID | Scenario and steps | Expected result |
|---|---|---|
| TC-01 | Start the program for the first time. | `data/` and all four text files are created; sample courses/students and the demo account are initialized. |
| TC-02 | Sign in with `admin` / `admin123`. | Dashboard opens and summary counts are displayed. |
| TC-03 | Sign in with an incorrect password. | Browser displays a sign-in error and remains on the login page. |
| TC-21 | Open Create an account and submit a valid username and matching password confirmation. | Account is saved with a salted password hash and can sign in. |
| TC-22 | Submit signup with mismatched passwords, invalid username, or a password shorter than 8 characters. | Account creation is rejected with a clear message; invalid accounts are not stored. |
| TC-23 | Register an existing username with different capitalization. | Duplicate username is rejected. |
| TC-04 | Add a student with valid details. | Student is added to the table and persisted. |
| TC-05 | Add another student with an existing student ID. | Duplicate is rejected and no duplicate row is written. |
| TC-06 | Enter a malformed email address for a student. | Validation error appears and the record is not saved. |
| TC-07 | Open a student's edit link and save updated details. | Updated values appear and survive application restart. |
| TC-08 | Delete a student with no active registration and confirm. | Student is removed from the table and file. |
| TC-09 | Add a course with valid details. | Course appears with its capacity as available seats. |
| TC-10 | Add a course with an existing course ID or code. | Duplicate is rejected. |
| TC-11 | Edit a course and set capacity below current enrollment. | Update is rejected; existing course data remains unchanged. |
| TC-12 | Register a student for a course with an available seat. | Unique registration is created, listed, saved, and available-seat count decreases. |
| TC-13 | Register the same student for the same course again. | Duplicate registration is rejected. |
| TC-14 | Register a different student after the course is full. | Registration is rejected; no extra record is saved. |
| TC-15 | Cancel an active registration and confirm on the following page. | Registration is removed and one seat becomes available. |
| TC-16 | Delete a student or course with active registrations. | Deletion is blocked until the related registrations are cancelled. |
| TC-17 | Search registrations by student ID, name, or course code. | Matching registration rows are displayed. |
| TC-18 | Open reports and select a student. | Their complete active enrollment list and course seat counts are shown. |
| TC-19 | Restart after creating/updating/cancelling records. | Files reload the latest persisted records correctly. |
| TC-20 | Store commas and quotation marks in a name/course title, then restart. | Values round-trip without corruption. |
