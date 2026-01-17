package dev.grahamhill.service;

import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.html.simpleparser.HTMLWorker;
import com.lowagie.text.html.simpleparser.StyleSheet;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import dev.grahamhill.model.FileChange;

import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.List;

public class ExportService {

    public void exportToPdf(List<ContributorStats> stats, MeaningfulChangeAnalysis meaningfulAnalysis, String filePath, String piePath, String barPath, String aiReport, java.util.Map<String, String> mdSections, String coverHtml, String coverBasePath, int tableLimit) throws Exception {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(filePath));
        document.open();

        Font headerFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        if (coverHtml != null && !coverHtml.isEmpty()) {
            try {
                StyleSheet styles = new StyleSheet();
                // Simple parser for HTML/CSS in OpenPDF
                java.util.HashMap<String, Object> providers = new java.util.HashMap<>();
                // OpenPDF HTMLWorker might not have IMG_BASEURL constant in some versions or it might be different.
                // It usually uses an ImageProvider or just looks for images.
                
                List<com.lowagie.text.Element> elements = HTMLWorker.parseToList(new StringReader(coverHtml), styles);
                for (com.lowagie.text.Element element : elements) {
                    document.add(element);
                }
            } catch (Exception e) {
                // Fallback to plain text if HTML parsing fails
                String plainText = coverHtml.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
                Paragraph coverPara = new Paragraph(plainText, headerFont);
                coverPara.setAlignment(com.lowagie.text.Element.ALIGN_CENTER);
                document.add(coverPara);
            }
            document.newPage();
        }

        document.add(new Paragraph("Git Contributor Metrics Report", headerFont));
        Paragraph spacing = new Paragraph(" ");
        spacing.setSpacingAfter(10f);
        document.add(spacing);

        // Custom MD Sections
        if (mdSections != null && !mdSections.isEmpty()) {
            for (java.util.Map.Entry<String, String> entry : mdSections.entrySet()) {
                Paragraph pTitle = new Paragraph(entry.getKey(), sectionFont);
                pTitle.setSpacingBefore(10f);
                document.add(pTitle);
                document.add(new Paragraph(entry.getValue(), normalFont));
                document.add(spacing);
            }
        }

        // AI Review Section
        if (aiReport != null && !aiReport.isEmpty()) {
            Paragraph aiTitle = new Paragraph("AI Generated Review", sectionFont);
            aiTitle.setSpacingBefore(15f);
            document.add(aiTitle);
            
            // Split by Markdown-style headers (e.g. **Header** or # Header)
            String[] parts = aiReport.split("(?=\n\\*\\*|\\n# )");
            for (String part : parts) {
                part = part.trim();
                if (part.isEmpty()) continue;
                
                if (part.startsWith("**") && part.contains("**")) {
                    int endBold = part.indexOf("**", 2);
                    if (endBold != -1) {
                        String subTitle = part.substring(2, endBold);
                        Paragraph pSub = new Paragraph(subTitle, new Font(Font.HELVETICA, 12, Font.BOLD));
                        pSub.setSpacingBefore(5f);
                        document.add(pSub);
                        String subContent = part.substring(endBold + 2).trim();
                        if (!subContent.isEmpty()) {
                            document.add(new Paragraph(subContent, normalFont));
                        }
                        continue;
                    }
                } else if (part.startsWith("#")) {
                    int contentStart = 0;
                    while (contentStart < part.length() && part.charAt(contentStart) == '#') contentStart++;
                    int lineEnd = part.indexOf("\n");
                    if (lineEnd != -1) {
                        String subTitle = part.substring(contentStart, lineEnd).trim();
                        Paragraph pSub = new Paragraph(subTitle, new Font(Font.HELVETICA, 12, Font.BOLD));
                        pSub.setSpacingBefore(5f);
                        document.add(pSub);
                        document.add(new Paragraph(part.substring(lineEnd).trim(), normalFont));
                        continue;
                    }
                }
                document.add(new Paragraph(part, normalFont));
            }
            document.add(spacing);
        }

        // Meaningful Change Detection Section
        if (meaningfulAnalysis != null) {
            Paragraph mcTitle = new Paragraph("Meaningful Change Detection", sectionFont);
            mcTitle.setSpacingBefore(15f);
            document.add(mcTitle);
            document.add(new Paragraph("Commit Range: " + meaningfulAnalysis.commitRange(), normalFont));
            document.add(new Paragraph("Meaningful Change Score: " + String.format("%.1f/100", meaningfulAnalysis.meaningfulChangeScore()), normalFont));
            document.add(new Paragraph("Summary: " + meaningfulAnalysis.summary(), normalFont));
            
            if (!meaningfulAnalysis.warnings().isEmpty()) {
                document.add(new Paragraph("Warnings:", normalFont));
                for (String warning : meaningfulAnalysis.warnings()) {
                    document.add(new Paragraph("  • " + warning, normalFont));
                }
            }
            document.add(spacing);

            document.add(new Paragraph("Category Breakdown:", normalFont));
            PdfPTable catTable = new PdfPTable(4);
            catTable.setSpacingBefore(5f);
            catTable.setSpacingAfter(10f);
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

            document.add(new Paragraph("Top Changed Files (by LOC):", normalFont));
            PdfPTable fileTable = new PdfPTable(4);
            fileTable.setSpacingBefore(5f);
            fileTable.setSpacingAfter(10f);
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
        }

        // Add Charts
        Paragraph chartTitle1 = new Paragraph("Commit Distribution:", sectionFont);
        chartTitle1.setSpacingBefore(15f);
        document.add(chartTitle1);
        Image pieImage = Image.getInstance(piePath);
        pieImage.scaleToFit(520, 400);
        pieImage.setAlignment(Image.MIDDLE);
        document.add(pieImage);

        Paragraph chartTitle2 = new Paragraph("Impact Analysis:", sectionFont);
        chartTitle2.setSpacingBefore(15f);
        document.add(chartTitle2);
        Image barImage = Image.getInstance(barPath);
        barImage.scaleToFit(520, 400);
        barImage.setAlignment(Image.MIDDLE);
        document.add(barImage);

        Paragraph detailTitle = new Paragraph("Detailed Contributor Metrics:", sectionFont);
        detailTitle.setSpacingBefore(15f);
        document.add(detailTitle);
        document.add(spacing);

        // Group others for the table
        List<ContributorStats> tableStats = groupOthers(stats, tableLimit);

        PdfPTable table = new PdfPTable(11);
        table.setWidthPercentage(100);
        table.setSpacingBefore(5f);
        table.setSpacingAfter(15f);
        table.addCell("Contributor");
        table.addCell("Gender");
        table.addCell("Commits");
        table.addCell("Merges");
        table.addCell("Lines Added");
        table.addCell("Lines Deleted");
        table.addCell("New Files");
        table.addCell("Edited Files");
        table.addCell("Deleted Files");
        table.addCell("File Types");
        table.addCell("AI Prob");

        for (ContributorStats stat : tableStats) {
            table.addCell(stat.name() + (stat.name().equals("Others") ? "" : " (" + stat.email() + ")"));
            table.addCell(stat.gender());
            table.addCell(String.valueOf(stat.commitCount()));
            table.addCell(String.valueOf(stat.mergeCount()));
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
            Paragraph summaryTitle = new Paragraph("Analysis Summary & Conclusion", sectionFont);
            summaryTitle.setSpacingBefore(15f);
            document.add(summaryTitle);
            
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

    private List<ContributorStats> groupOthers(List<ContributorStats> stats, int limit) {
        if (stats.size() <= limit) return stats;
        java.util.List<ContributorStats> top = new java.util.ArrayList<>(stats.subList(0, limit));
        java.util.List<ContributorStats> others = stats.subList(limit, stats.size());

        int oCommits = others.stream().mapToInt(ContributorStats::commitCount).sum();
        int oMerges = others.stream().mapToInt(ContributorStats::mergeCount).sum();
        int oAdded = others.stream().mapToInt(ContributorStats::linesAdded).sum();
        int oDeleted = others.stream().mapToInt(ContributorStats::linesDeleted).sum();
        int oFAdded = others.stream().mapToInt(ContributorStats::filesAdded).sum();
        int oFEdited = others.stream().mapToInt(ContributorStats::filesEdited).sum();
        int oFDeleted = others.stream().mapToInt(ContributorStats::filesDeletedCount).sum();
        double avgAi = others.stream().mapToDouble(ContributorStats::averageAiProbability).average().orElse(0.0);

        java.util.Map<String, Integer> oLangs = new java.util.HashMap<>();
        others.forEach(s -> s.languageBreakdown().forEach((k, v) -> oLangs.merge(k, v, Integer::sum)));

        top.add(new ContributorStats("Others", "others@example.com", "unknown", oCommits, oMerges, oAdded, oDeleted, oLangs, avgAi, oFAdded, oFEdited, oFDeleted));
        return top;
    }

    private String formatLanguages(java.util.Map<String, Integer> languages) {
        if (languages == null || languages.isEmpty()) return "N/A";
        return languages.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + "(" + e.getValue() + ")")
                .collect(java.util.stream.Collectors.joining(", "));
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
}
