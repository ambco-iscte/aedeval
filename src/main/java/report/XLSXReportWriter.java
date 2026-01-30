package report;

import evaluator.Report;
import evaluator.Submission;
import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Console;
import extensions.Extensions;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xddf.usermodel.chart.*;
import org.apache.poi.xssf.usermodel.*;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTCatAx;
import org.openxmlformats.schemas.drawingml.x2006.chart.CTChartSpace;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

public class XLSXReportWriter {

    private static final XSSFColor LIGHT_BLUE = new XSSFColor(new java.awt.Color(60, 113, 201), new DefaultIndexedColorMap());

    private static final XSSFColor LIGHTER_BLUE = new XSSFColor(new java.awt.Color(221, 233, 255), new DefaultIndexedColorMap());

    private static final int MAX_LENGTH = 32767;

    private enum FontStyle {
        REGULAR, HEADER, WARNING
    }

    private static void createRow(Sheet sheet, int index, FontStyle fontStyle, Object... values) {
        Row row = sheet.createRow(index);
        CellStyle style = sheet.getWorkbook().createCellStyle();
        for (int i = 0; i < values.length; i++) {
            Cell cell = row.createCell(i);

            if (values[i] instanceof Number) {
                cell.setCellValue(Extensions.round(((Number) values[i]).doubleValue(), 1));
            }
            else {
                String content = Objects.toString(values[i]);
                if (content.length() >= MAX_LENGTH)
                    content = content.substring(0, MAX_LENGTH);
                cell.setCellValue(new XSSFRichTextString(content));
            }

            stylise(cell, style, fontStyle);
        }
        row.setHeight((short) -1); // Automatically adjust row height
    }

    private static void fitCellContent(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            sheet.autoSizeColumn(i, true);
        }
    }

    private static void disableRoundedCorners(XSSFChart chart) {
        CTChartSpace space = chart.getCTChartSpace();
        if (space.getRoundedCorners() == null)
            space.addNewRoundedCorners();
        space.getRoundedCorners().setVal(false);
    }

    private static void stylise(Cell cell, CellStyle style, FontStyle fontStyle) {
        Font regular = cell.getSheet().getWorkbook().createFont();

        Font bold = cell.getSheet().getWorkbook().createFont();
        bold.setBold(true);

        Font red = cell.getSheet().getWorkbook().createFont();
        red.setBold(true);
        red.setColor(Font.COLOR_RED);

        style.setWrapText(true);
        if (fontStyle == FontStyle.HEADER) {
            style.setFont(bold);
            style.setFillForegroundColor(LIGHT_BLUE);
            style.setAlignment(HorizontalAlignment.CENTER);
        } else if (cell.getRowIndex() % 2 != 0) {
            style.setFillForegroundColor(LIGHTER_BLUE);
        } else
            style.setFillForegroundColor(IndexedColors.WHITE.index);

        if (fontStyle == FontStyle.REGULAR)
            style.setFont(regular);
        else if (fontStyle == FontStyle.WARNING)
            style.setFont(red);

        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        cell.setCellStyle(style);
    }

    private static void plot(XSSFDrawing drawing, XSSFClientAnchor anchor, String title, String x, String y, int n, Map<String, ? extends Number> data, ChartTypes type, boolean percentage) {
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText(title + " (n = " + n + ")");
        chart.setTitleOverlay(false);
        chart.getTitle().getOrAddTextProperties().setFontSize(16.0);

        XDDFCategoryAxis bottomAxis = chart.createCategoryAxis(AxisPosition.BOTTOM);
        bottomAxis.setTitle(x);
        bottomAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        bottomAxis.getOrAddTextProperties().setFontSize(16.0);

        XDDFValueAxis leftAxis = chart.createValueAxis(AxisPosition.LEFT);
        leftAxis.setTitle(y);
        leftAxis.setCrosses(AxisCrosses.AUTO_ZERO);
        leftAxis.setCrossBetween(AxisCrossBetween.BETWEEN);
        leftAxis.getOrAddTextProperties().setFontSize(16.0);

        XDDFCategoryDataSource verticalLabels = XDDFDataSourcesFactory.fromArray(data.keySet().toArray(new String[0]));
        XDDFNumericalDataSource<Number> horizontalValues;

        if (percentage) {
            horizontalValues =
                    XDDFDataSourcesFactory.fromArray(
                        data.values().stream().map(i -> 100 * i.doubleValue() / n
                    ).toList().toArray(new Double[0]));
            leftAxis.setMinimum(0);
        }
        else
            horizontalValues = XDDFDataSourcesFactory.fromArray(data.values().toArray(new Number[0]));

        XDDFChartData chartData = chart.createData(type, bottomAxis, leftAxis);
        XDDFBarChartData.Series series = (XDDFBarChartData.Series) chartData.addSeries(verticalLabels, horizontalValues);
        series.setTitle(title, null);

        chartData.setVaryColors(false);
        disableRoundedCorners(chart);
        chart.plot(chartData);
    }

    private static void buildDashboard(Sheet sheet, String title, int sampleSize, Map<String, Integer> grades, Map<String, Integer> errors, Map<String, Integer> correctness) {
        XSSFDrawing drawing = (XSSFDrawing) sheet.createDrawingPatriarch();

        // Grade distributions
        XSSFClientAnchor anchor1 = drawing.createAnchor(0, 0, 0, 0, 0, 0, 16, 37);
        plot(drawing, anchor1, "Grades: " + title, "Grade", "% of Submissions with Grade", sampleSize, grades, ChartTypes.BAR, true);

        // Error type distributions
        XSSFClientAnchor anchor2 = drawing.createAnchor(0, 0, 0, 0, 16, 0, 29, 19);
        plot(drawing, anchor2, "Errors: " + title, "Error Type", "% of Submissions with Errors of Type", sampleSize, errors, ChartTypes.BAR, true);

        // Method failures distribution
        XSSFClientAnchor anchor3 = drawing.createAnchor(0, 0, 0, 0, 16, 19, 29, 37);
        plot(drawing, anchor3, "Failures per Method/Test: " + title, "Method or Test Case", "Total # of Failures in Test Case", sampleSize, correctness, ChartTypes.BAR, false);
    }

    public static void write(Report report, String path, int plagiarismClusterMinimumSize) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet results = workbook.createSheet("Results");
            int rowCount = 0;
            createRow(results, rowCount++, FontStyle.HEADER, "Submission ID", "Name", "Error Logs", "Grade");
            createRow(results, rowCount++, FontStyle.HEADER, "", "", "", "");

            Map<String, Integer> grades = new LinkedHashMap<>();
            for (int i = 0; i <= 20; i++)
                grades.put(String.valueOf(i), 0);
            Map<String, Integer> errorsOfEachType = new LinkedHashMap<>();
            Map<String, Integer> errorsPerTest = new LinkedHashMap<>();

            // Analyse each Entry
            for (Report.Entry entry : report) {
                // Collect error type counts
                Map<String, Integer> submissionErrors = entry.getErrorCountPerCode();
                for (String errorCode : submissionErrors.keySet()) {
                    errorsOfEachType.put(errorCode, errorsOfEachType.getOrDefault(errorCode, 0) + 1);
                }

                // Collect count of errors per test
                for (Map.Entry<Test, List<Result>> t : entry.getResults().entrySet()) {
                    String description = t.getKey().description();
                    int errors = (int) t.getValue().stream().filter(result -> !result.passed()).count();
                    errorsPerTest.put(description, errorsPerTest.getOrDefault(description, 0) + errors);
                }

                // General submission information
                Submission sub = entry.getSubmission();
                Object[] info;

                if (report.hasPlagiarismAnalysis()) {
                    Set<de.jplag.Submission> cluster = report.getPlagiarismCluster(entry);
                    if (cluster == null || cluster.size() < plagiarismClusterMinimumSize) {
                        info = new Object[] {
                                sub.getID(),
                                sub.getName(),
                                Extensions.joinToString(System.lineSeparator(), entry.getErrorMessages()),
                                entry.getGrade()
                        };
                        createRow(results, rowCount++, FontStyle.REGULAR, info);
                    } else {
                        String names = Extensions.joinToString(cluster, t -> t.getName().split("_")[0]);
                        info = new Object[] {
                                sub.getID(),
                                sub.getName(),
                                "Submission annulled due to 100% similarity between " + cluster.size() + " students: " + names,
                                0.0
                        };
                        createRow(results, rowCount++, FontStyle.WARNING, info);
                    }
                } else {
                    info = new Object[] {
                            sub.getID(),
                            sub.getName(),
                            Extensions.joinToString(System.lineSeparator(), entry.getErrorMessages()),
                            entry.getGrade()
                    };
                    createRow(results, rowCount++, FontStyle.REGULAR, info);
                }

                String rounded = String.valueOf((int) Math.round((double) info[3]));
                grades.put(rounded, grades.getOrDefault(rounded, 0) + 1);
            }

            // Fit Cell to Contents
            fitCellContent(results, results.getRow(0).getPhysicalNumberOfCells());

            // Merge initial header cells to make it look prettier
            for (int i = 0; i < 4; i++)
                results.addMergedRegion(new CellRangeAddress(0, 1, i, i));

            // Create Statistics sheet
            Sheet statistics = workbook.createSheet("Statistics");
            buildDashboard(statistics, report.getDescription(), report.getEntries().size(), grades, errorsOfEachType, errorsPerTest);

            // Write to File
            try (FileOutputStream outputStream = new FileOutputStream(path + ".xlsx")) {
                workbook.write(outputStream);
            } catch (IOException e) {
                Console.error("Exception thrown when writing XLSX report: " + e.getMessage());
                throw e;
            }
            System.out.println("Report available at: " + path + ".xlsx");
        }
    }
}
