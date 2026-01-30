package aed.data;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import extensions.Console;
import extensions.Levenshtein;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.poi.util.StringUtil;

import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.util.Arrays;

public class StudentBody implements Serializable {

    private static class StudentDeserializer implements JsonDeserializer<Student> {

        private String get(JsonObject json, String memberName) {
            JsonElement element = json.get(memberName);
            if (element.isJsonNull())
                return "";
            else
                return element.getAsString();
        }

        @Override
        public Student deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject json = jsonElement.getAsJsonObject();

            String name = get(json, "nome");
            String surname = get(json, "apelido");
            String username = get(json, "nomedeutilizador");
            String id = get(json, "nmerodeidentificaoid");
            String email = get(json, "endereodee-mail");
            String department = get(json, "departamento");
            String groups = get(json, "grupos");

            return new Student(name, surname, username, id, email, department, groups);
        }
    }

    private Student[] students;

    public StudentBody() {

    }

    public static StudentBody load(String path) {
        GsonBuilder gson = new GsonBuilder();
        gson.registerTypeAdapter(Student.class, new StudentDeserializer());
        Gson loader = gson.create();
        try (JsonReader reader = new JsonReader(new FileReader(path))) {
            return loader.fromJson(reader, StudentBody.class);
        } catch (IOException e) {
            Console.warning("Could not load student information from file " + path + " due to error: " + e);
        }
        return null;
    }

    public Student find(String name) {
        for (Student student : students) {
            if (student.name().equals(name))
                return student;
        }
        return null;
    }

    public Student findClosest(String name) {
        Levenshtein lev = new Levenshtein();
        Student min = null;
        double minDiff = Double.POSITIVE_INFINITY;
        for (Student student : students) {
            double diff = lev.distance(student.name(), name);
            if (diff < minDiff) {
                min = student;
                minDiff = diff;
            }
        }
        return min;
    }

    @Override
    public String toString() {
        return Arrays.toString(students);
    }
}
