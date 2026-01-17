package dev.grahamhill.service;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import dev.grahamhill.model.ContributorStats;

import java.io.FileOutputStream;
import java.util.List;

public class ExportService {

    public void exportToPdf(List<ContributorStats> stats, String filePath, String piePath, String barPath) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));
        document.open();

        document.add(new Paragraph("Git Contributor Metrics Report"));
        document.add(new Paragraph(" "));

        // Add Charts
        document.add(new Paragraph("Commit Distribution:"));
        Image pieImage = Image.getInstance(piePath);
        pieImage.scaleToFit(500, 300);
        document.add(pieImage);

        document.add(new Paragraph("Impact Analysis:"));
        Image barImage = Image.getInstance(barPath);
        barImage.scaleToFit(500, 300);
        document.add(barImage);

        document.add(new Paragraph("Detailed Metrics:"));
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
        table.addCell("Top Languages");
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
        document.close();
    }

    private String formatLanguages(java.util.Map<String, Integer> languages) {
        if (languages == null || languages.isEmpty()) return "N/A";
        return languages.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
