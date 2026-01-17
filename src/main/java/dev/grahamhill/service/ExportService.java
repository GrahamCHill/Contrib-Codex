package dev.grahamhill.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import dev.grahamhill.model.FileChange;

import java.io.FileOutputStream;
import java.util.List;

public class ExportService {

    public void exportToPdf(List<ContributorStats> stats, MeaningfulChangeAnalysis meaningfulAnalysis, String filePath, String piePath, String barPath, String aiReport) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));
        document.open();

        Font headerFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        document.add(new Paragraph("Git Contributor Metrics Report", headerFont));
        document.add(new Paragraph(" "));

        // AI Review Section
        if (aiReport != null && !aiReport.isEmpty()) {
            document.add(new Paragraph("AI Generated Review", sectionFont));
            document.add(new Paragraph(aiReport, normalFont));
            document.add(new Paragraph(" "));
        }

        // Meaningful Change Detection Section
        if (meaningfulAnalysis != null) {
            document.add(new Paragraph("Meaningful Change Detection", sectionFont));
            document.add(new Paragraph("Commit Range: " + meaningfulAnalysis.commitRange()));
            document.add(new Paragraph("Meaningful Change Score: " + String.format("%.1f/100", meaningfulAnalysis.meaningfulChangeScore())));
            document.add(new Paragraph("Summary: " + meaningfulAnalysis.summary()));
            
            if (!meaningfulAnalysis.warnings().isEmpty()) {
                document.add(new Paragraph("Warnings:"));
                for (String warning : meaningfulAnalysis.warnings()) {
                    document.add(new Paragraph("  • " + warning));
                }
            }
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Category Breakdown:"));
            PdfPTable catTable = new PdfPTable(4);
            catTable.setWidthPercentage(100);
            catTable.addCell("Category");
            catTable.addCell("File Count");
            catTable.addCell("Insertions");
            catTable.addCell("Deletions");
            meaningfulAnalysis.categoryBreakdown().forEach((cat, m) -> {
                if (m.fileCount() > 0) {
                    catTable.addCell(cat);
                    catTable.addCell(String.valueOf(m.fileCount()));
                    catTable.addCell(String.valueOf(m.insertions()));
                    catTable.addCell(String.valueOf(m.deletions()));
                }
            });
            document.add(catTable);
            document.add(new Paragraph(" "));

            document.add(new Paragraph("Top Changed Files (by LOC):"));
            PdfPTable fileTable = new PdfPTable(4);
            fileTable.setWidthPercentage(100);
            fileTable.addCell("File Path");
            fileTable.addCell("Insertions");
            fileTable.addCell("Deletions");
            fileTable.addCell("Category");
            for (FileChange fc : meaningfulAnalysis.topChangedFiles()) {
                fileTable.addCell(fc.path());
                fileTable.addCell(String.valueOf(fc.insertions()));
                fileTable.addCell(String.valueOf(fc.deletions()));
                fileTable.addCell(fc.category());
            }
            document.add(fileTable);
            document.add(new Paragraph(" "));
        }

        // Add Charts
        document.add(new Paragraph("Commit Distribution:", sectionFont));
        Image pieImage = Image.getInstance(piePath);
        pieImage.scaleToFit(500, 300);
        document.add(pieImage);

        document.add(new Paragraph("Impact Analysis:", sectionFont));
        Image barImage = Image.getInstance(barPath);
        barImage.scaleToFit(500, 300);
        document.add(barImage);

        document.add(new Paragraph("Detailed Contributor Metrics:", sectionFont));
        document.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(9);
        table.setWidthPercentage(100);
        table.addCell("Contributor");
        table.addCell("Commits");
        table.addCell("Lines Added");
        table.addCell("Lines Deleted");
        table.addCell("New Files");
        table.addCell("Edited Files");
        table.addCell("Deleted Files");
        table.addCell("File Types");
        table.addCell("AI Prob");

        for (ContributorStats stat : stats) {
            table.addCell(stat.name() + " (" + stat.email() + ")");
            table.addCell(String.valueOf(stat.commitCount()));
            table.addCell(String.valueOf(stat.linesAdded()));
            table.addCell(String.valueOf(stat.linesDeleted()));
            table.addCell(String.valueOf(stat.filesAdded()));
            table.addCell(String.valueOf(stat.filesEdited()));
            table.addCell(String.valueOf(stat.filesDeletedCount()));
            table.addCell(formatLanguages(stat.languageBreakdown()));
            table.addCell(String.format("%.1f%%", stat.averageAiProbability() * 100));
        }

        document.add(table);

        // Analysis Summary & Conclusion Section
        if (aiReport != null && !aiReport.isEmpty()) {
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Analysis Summary & Conclusion", sectionFont));
            
            // Try to extract a "Conclusion" section if it exists, otherwise use the whole report as summary
            String conclusion = extractConclusion(aiReport);
            if (!conclusion.isEmpty()) {
                document.add(new Paragraph(conclusion, normalFont));
            } else {
                document.add(new Paragraph("See AI Generated Review section for detailed analysis and findings.", normalFont));
            }
        }

        document.close();
    }

    private String extractConclusion(String report) {
        String lowerReport = report.toLowerCase();
        int index = lowerReport.lastIndexOf("conclusion");
        if (index != -1) {
            // Find the next newline or colon after "conclusion"
            int start = report.indexOf("\n", index);
            if (start == -1) start = report.indexOf(":", index) + 1;
            if (start != -1 && start < report.length()) {
                return report.substring(start).trim();
            }
            return report.substring(index).trim();
        }
        return "";
    }

    private String formatLanguages(java.util.Map<String, Integer> languages) {
        if (languages == null || languages.isEmpty()) return "N/A";
        return languages.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
