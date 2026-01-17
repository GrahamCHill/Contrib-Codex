package dev.grahamhill.service;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.lowagie.text.html.simpleparser.HTMLWorker;
import com.lowagie.text.html.simpleparser.StyleSheet;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import dev.grahamhill.model.FileChange;

import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.List;

public class ExportService {

    public void exportToPdf(List<ContributorStats> stats, MeaningfulChangeAnalysis meaningfulAnalysis, String filePath, String piePath, String barPath, String linePath, String calendarPath, String aiReport, java.util.Map<String, String> mdSections, String coverHtml, String coverBasePath, int tableLimit) throws Exception {
        Document document = new Document(PageSize.A4);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(filePath));
        
        // Find logo path if available from cover page or similar
        String logoPath = null;
        if (coverHtml != null && coverHtml.contains("<img")) {
            int srcStart = coverHtml.indexOf("src=\"") + 5;
            int srcEnd = coverHtml.indexOf("\"", srcStart);
            if (srcStart > 4 && srcEnd > srcStart) {
                logoPath = coverHtml.substring(srcStart, srcEnd);
            }
        }
        
        HeaderFooterEvent event = new HeaderFooterEvent(logoPath);
        writer.setPageEvent(event);
        
        document.open();

        Font headerFont = new Font(Font.HELVETICA, 18, Font.BOLD);
        Font sectionFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font normalFont = new Font(Font.HELVETICA, 10, Font.NORMAL);

        if (coverHtml != null && !coverHtml.isEmpty()) {
            event.setCoverPage(true);
            try {
                StyleSheet styles = new StyleSheet();
                // Improved stylesheet handling to avoid CSS appearing as text
                String htmlToParse = coverHtml;
                
                // Extract and apply style tags manually if HTMLWorker struggles with them
                if (htmlToParse.contains("<style>")) {
                    int startStyle = htmlToParse.indexOf("<style>");
                    int endStyle = htmlToParse.indexOf("</style>");
                    if (endStyle > startStyle) {
                        String css = htmlToParse.substring(startStyle + 7, endStyle).trim();
                        // OpenPDF StyleSheet.loadTagStyle is one way, or we just rely on standard tags
                        // For simplicity, we'll strip the style block from the body to prevent it rendering as text
                        htmlToParse = htmlToParse.substring(0, startStyle) + htmlToParse.substring(endStyle + 8);
                        
                        // Basic CSS to StyleSheet mapping
                        String[] rules = css.split("}");
                        for (String rule : rules) {
                            if (rule.contains("{")) {
                                String[] kv = rule.split("\\{");
                                String selector = kv[0].trim().replace(".", ""); // Remove dot for class selectors
                                String props = kv[1].trim();
                                for (String prop : props.split(";")) {
                                    if (prop.contains(":")) {
                                        String[] pk = prop.split(":");
                                        styles.loadTagStyle(selector, pk[0].trim(), pk[1].trim());
                                    }
                                }
                            }
                        }
                    }
                }

                List<com.lowagie.text.Element> elements = HTMLWorker.parseToList(new StringReader(htmlToParse), styles);
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
        event.setCoverPage(false);

        document.add(new Paragraph("Git Contributor Metrics Report", headerFont));
        Paragraph spacing = new Paragraph(" ");
        spacing.setSpacingAfter(10f);
        document.add(spacing);

        // Meaningful Change Detection Section
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

        Paragraph chartTitle2 = new Paragraph("Impact Analysis (Stacked):", sectionFont);
        chartTitle2.setSpacingBefore(15f);
        document.add(chartTitle2);
        Image barImage = Image.getInstance(barPath);
        barImage.scaleToFit(520, 400);
        barImage.setAlignment(Image.MIDDLE);
        document.add(barImage);

        Paragraph chartTitle3 = new Paragraph("Recent Commit Activity:", sectionFont);
        chartTitle3.setSpacingBefore(15f);
        document.add(chartTitle3);
        Image lineImage = Image.getInstance(linePath);
        lineImage.scaleToFit(520, 400);
        lineImage.setAlignment(Image.MIDDLE);
        document.add(lineImage);

        // Calendar Activity on a new Landscape page
        document.setPageSize(PageSize.A4.rotate());
        document.newPage();
        Paragraph chartTitle4 = new Paragraph("Daily Activity (Calendar):", sectionFont);
        chartTitle4.setSpacingBefore(15f);
        document.add(chartTitle4);
        Image calendarImage = Image.getInstance(calendarPath);
        calendarImage.scaleToFit(750, 450);
        calendarImage.setAlignment(Image.MIDDLE);
        document.add(calendarImage);

        // Back to Portrait for the rest
        document.setPageSize(PageSize.A4);
        document.newPage();

        Paragraph detailTitle = new Paragraph("Detailed Contributor Metrics:", sectionFont);
        detailTitle.setSpacingBefore(15f);
        document.add(detailTitle);
        document.add(spacing);

        // Group others for the table
        List<ContributorStats> tableStats = groupOthers(stats, tableLimit);

        PdfPTable table = new PdfPTable(12);
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
        table.addCell("Meaningful Score");

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
            table.addCell(String.format("%.1f/100", stat.meaningfulChangeScore()));
        }

        document.add(table);
        document.close();
    }

    private static class HeaderFooterEvent extends PdfPageEventHelper {
        private String logoPath;
        private Font footerFont = new Font(Font.HELVETICA, 8, Font.NORMAL);
        private boolean isCoverPage = true;

        public HeaderFooterEvent(String logoPath) {
            this.logoPath = logoPath;
        }

        public void setCoverPage(boolean coverPage) {
            isCoverPage = coverPage;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            if (isCoverPage) return;

            PdfContentByte cb = writer.getDirectContent();
            Rectangle pageSize = writer.getPageSize();
            
            // Header: Logo scaled to 64px height, 16px from top
            if (logoPath != null) {
                try {
                    java.io.File logoFile = new java.io.File(logoPath);
                    if (logoFile.exists()) {
                        Image logo = Image.getInstance(logoPath);
                        logo.scaleToFit(500, 64); // Max width 500, height 64
                        logo.setAbsolutePosition(document.left(), pageSize.getTop() - 16 - 64);
                        cb.addImage(logo);
                    }
                } catch (Exception e) {
                    // Ignore logo errors
                }
            }

            // Footer: Page number and AI-generated risk report
            // Adjust page number to not count cover page
            int pageNum = writer.getPageNumber() - 1; 
            String footerText = "AI-generated risk report for the codebase - Page " + pageNum;
            Phrase footer = new Phrase(footerText, footerFont);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    footer,
                    (pageSize.getRight() - pageSize.getLeft()) / 2 + pageSize.getLeft(),
                    pageSize.getBottom() + 10, 0);
        }
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

        double avgMeaningful = others.stream().mapToDouble(ContributorStats::meaningfulChangeScore).average().orElse(0.0);

        java.util.Map<String, Integer> oLangs = new java.util.HashMap<>();
        others.forEach(s -> s.languageBreakdown().forEach((k, v) -> oLangs.merge(k, v, Integer::sum)));

        top.add(new ContributorStats("Others", "others@example.com", "unknown", oCommits, oMerges, oAdded, oDeleted, oLangs, avgAi, oFAdded, oFEdited, oFDeleted, avgMeaningful));
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
