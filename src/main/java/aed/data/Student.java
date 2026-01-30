package aed.data;

import java.io.Serializable;

public class Student implements Serializable {
    private final String name;
    private final String surname;
    private final String username;
    private final String id;
    private final String email;
    private final String department;
    private final String groups;

    public Student(String name, String surname, String username, String id, String email, String department, String groups) {
        this.name = name;
        this.surname = surname;
        this.username = username;
        this.id = id;
        this.email = email;
        this.department = department;
        this.groups = groups;
    }

    public String name() {
        return name + " " + surname;
    }

    public boolean hasID() {
        return this.id != null && !this.id.isEmpty() && !this.id.isBlank();
    }

    public long getID() {
        return Long.parseLong(id);
    }

    @Override
    public String toString() {
        return name() + " " + id;
    }
}
