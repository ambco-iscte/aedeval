package report;

import com.google.gson.*;
import evaluator.Report;
import extensions.Console;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;

public class JSONReportWriter {

    private static class ReportAdapter implements JsonSerializer<Report> {

        @Override
        public JsonElement serialize(Report report, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject obj = new JsonObject();
            obj.addProperty("description", report.getDescription());
            JsonArray entries = new JsonArray();
            for (Report.Entry entry : report) {
                entries.add(jsonSerializationContext.serialize(entry));
            }
            obj.add("entries", entries);
            return obj;
        }
    }

    private static class EntryAdapter implements JsonSerializer<Report.Entry> {

        @Override
        public JsonElement serialize(Report.Entry entry, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", entry.getSubmission().getName());
            obj.addProperty("grade", entry.getGrade());
            return obj;
        }
    }

    public static void write(Report report, String path) throws IOException {
        GsonBuilder builder = new GsonBuilder().setPrettyPrinting();
        builder.registerTypeAdapter(Report.class, new ReportAdapter());
        builder.registerTypeAdapter(Report.Entry.class, new EntryAdapter());

        // Write to File
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(path + ".json"))) {
            writer.write(builder.create().toJson(report));
        } catch (IOException e) {
            Console.error("Exception thrown when writing report in JSON format: " + e.getMessage());
            throw e;
        }
        System.out.println("Report in JSON format available at: " + path + ".json");
    }
}
