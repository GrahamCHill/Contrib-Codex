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

    public void exportToPdf(List<ContributorStats> stats, MeaningfulChangeAnalysis meaningfulAnalysis, String filePath, String piePath, String barPath, String linePath, String calendarPath, String contribPath, String cpdPath, String aiReport, java.util.Map<String, String> mdSections, String coverHtml, String coverBasePath, int tableLimit) throws Exception {
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
        Font smallFont = new Font(Font.HELVETICA, 8, Font.NORMAL);

        if (coverHtml != null && !coverHtml.isEmpty()) {
            event.setCoverPage(true);
            try {
                // Wrap in full HTML if it's missing to help parser
                String fullHtml = coverHtml;
                if (!fullHtml.toLowerCase().contains("<html>")) {
                    fullHtml = "<html><body>" + fullHtml + "</body></html>";
                }
                
                StyleSheet styles = new StyleSheet();
                // Improved stylesheet handling to avoid CSS appearing as text
                String htmlToParse = fullHtml;
                
                // Extract and apply style tags manually if HTMLWorker struggles with them
                if (htmlToParse.contains("<style>")) {
                    int startStyle = htmlToParse.indexOf("<style>");
                    int endStyle = htmlToParse.indexOf("</style>");
                    if (endStyle > startStyle) {
                        String css = htmlToParse.substring(startStyle + 7, endStyle).trim();
                        // Strip the style block from the body to prevent it rendering as text
                        htmlToParse = htmlToParse.substring(0, startStyle) + htmlToParse.substring(endStyle + 8);
                        
                        // Basic CSS to StyleSheet mapping
                        String[] rules = css.split("}");
                        for (String rule : rules) {
                            if (rule.contains("{")) {
                                String[] kv = rule.split("\\{");
                                String selector = kv[0].trim();
                                if (selector.startsWith(".")) selector = selector.substring(1); // Remove dot for class selectors
                                String props = kv[1].trim();
                                for (String prop : props.split(";")) {
                                    if (prop.contains(":")) {
                                        String[] pk = prop.split(":");
                                        String key = pk[0].trim();
                                        String value = pk[1].trim();
                                        styles.loadTagStyle(selector, key, value);
                                        
                                        // If background-color is set for body or html, apply it to the whole page
                                        if ((selector.equalsIgnoreCase("body") || selector.equalsIgnoreCase("html")) 
                                            && key.equalsIgnoreCase("background-color")) {
                                            try {
                                                java.awt.Color awtColor = java.awt.Color.decode(value);
                                                com.lowagie.text.pdf.PdfContentByte canvas = writer.getDirectContentUnder();
                                                canvas.saveState();
                                                canvas.setColorFill(awtColor);
                                                canvas.rectangle(0, 0, PageSize.A4.getWidth(), PageSize.A4.getHeight());
                                                canvas.fill();
                                                canvas.restoreState();
                                            } catch (Exception ex) {
                                                // Ignore invalid colors
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Use more modern HTML parsing if possible, but stay with HTMLWorker for compatibility
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

        // Index Page
        document.add(new Paragraph("Report Index", headerFont));
        document.add(new Paragraph(" ", normalFont));
        
        PdfPTable indexTable = new PdfPTable(2);
        indexTable.setWidthPercentage(100);
        indexTable.setWidths(new float[]{4, 1});
        
        addIndexRow(indexTable, "Introduction & Purpose", "intro", normalFont);
        if (aiReport != null) addIndexRow(indexTable, "AI Generated Review", "ai_review", normalFont);
        if (meaningfulAnalysis != null) addIndexRow(indexTable, "Meaningful Change Detection", "meaningful", normalFont);
        addIndexRow(indexTable, "Visual Analytics (Charts)", "charts", normalFont);
        addIndexRow(indexTable, "Detailed Contributor Metrics", "details", normalFont);
        
        document.add(indexTable);
        document.newPage();

        Paragraph introHeader = new Paragraph("Git Contributor Metrics Report", headerFont);
        introHeader.setLocalDestination("intro");
        document.add(introHeader);
        
        // Purpose Explanation
        Paragraph explanation = new Paragraph("This report provides a comprehensive analysis of the Git repository's contribution history. " +
                "It evaluates contributor impact through line-level metrics, file status changes, and AI-generated probability heuristics. " +
                "The purpose is to identify meaningful development patterns, assess potential risks associated with bulk code generation, " +
                "and recognize the most valuable contributions based on iterative and qualitative development standards.", normalFont);
        explanation.setSpacingBefore(10f);
        explanation.setSpacingAfter(15f);
        document.add(explanation);

        Paragraph spacing = new Paragraph(" ");
        spacing.setSpacingAfter(10f);
        document.add(spacing);

        // Meaningful Change Detection Section
        if (aiReport != null && !aiReport.isEmpty()) {
            document.newPage(); // Start AI Review on a new page
            Paragraph aiTitle = new Paragraph("AI Generated Review", sectionFont);
        aiTitle.setLocalDestination("ai_review");
        aiTitle.setSpacingBefore(15f);
        document.add(aiTitle);
            
            String[] lines = aiReport.split("\n");
            StringBuilder currentText = new StringBuilder();
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    if (currentText.length() > 0) {
                        document.add(new Paragraph(currentText.toString(), normalFont));
                        currentText = new StringBuilder();
                    }
                    continue;
                }

                if (line.startsWith("# ")) {
                    if (currentText.length() > 0) {
                        document.add(new Paragraph(currentText.toString(), normalFont));
                        currentText = new StringBuilder();
                    }
                    Paragraph h1 = new Paragraph(line.substring(2).trim(), sectionFont);
                    h1.setSpacingBefore(10f);
                    document.add(h1);
                } else if (line.startsWith("## ")) {
                    if (currentText.length() > 0) {
                        document.add(new Paragraph(currentText.toString(), normalFont));
                        currentText = new StringBuilder();
                    }
                    Paragraph h2 = new Paragraph(line.substring(3).trim(), new Font(Font.HELVETICA, 12, Font.BOLD));
                    h2.setSpacingBefore(8f);
                    document.add(h2);
                } else if (line.startsWith("**") && line.endsWith("**") && line.length() > 4) {
                    if (currentText.length() > 0) {
                        document.add(new Paragraph(currentText.toString(), normalFont));
                        currentText = new StringBuilder();
                    }
                    Paragraph bold = new Paragraph(line.substring(2, line.length() - 2).trim(), new Font(Font.HELVETICA, 11, Font.BOLD));
                    bold.setSpacingBefore(5f);
                    document.add(bold);
                } else if (line.startsWith("|")) {
                    if (currentText.length() > 0) {
                        document.add(new Paragraph(currentText.toString(), normalFont));
                        currentText = new StringBuilder();
                    }
                    
                    // Table detection and processing
                    java.util.List<String> tableLines = new java.util.ArrayList<>();
                    while (i < lines.length && lines[i].trim().startsWith("|")) {
                        tableLines.add(lines[i].trim());
                        i++;
                    }
                    i--; // Step back because outer loop will increment
                    
                    if (tableLines.size() >= 2) {
                        processMarkdownTable(tableLines, document, normalFont);
                    }
                } else {
                    currentText.append(line).append(" ");
                }
            }
            if (currentText.length() > 0) {
                document.add(new Paragraph(currentText.toString(), normalFont));
            }
            document.add(spacing);
        }

        // Meaningful Change Detection Section
        if (meaningfulAnalysis != null) {
            document.newPage(); // New page for Meaningful Change Detection
            Paragraph mcTitle = new Paragraph("Meaningful Change Detection", sectionFont);
        Anchor mcAnchor = new Anchor(mcTitle);
        mcAnchor.setName("meaningful");
        mcTitle.setSpacingBefore(15f);
        document.add(mcAnchor);
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
        document.setPageSize(PageSize.A4.rotate());
        document.newPage(); // New page for Visuals
        Paragraph chartTitle1 = new Paragraph("Commit Distribution:", sectionFont);
        Anchor chartAnchor = new Anchor(chartTitle1);
        chartAnchor.setName("charts");
        chartTitle1.setSpacingBefore(15f);
        document.add(chartAnchor);
        
        com.lowagie.text.List graphList = new com.lowagie.text.List(true, 20);
        graphList.add(new ListItem("Commits by Contributor (Pie Chart)", normalFont));
        graphList.add(new ListItem("Impact Analysis (Stacked Bar Chart)", normalFont));
        graphList.add(new ListItem("Recent Commit Activity (Line Chart)", normalFont));
        graphList.add(new ListItem("Daily Activity - Total Impact (Line Chart)", normalFont));
        graphList.add(new ListItem("Daily Activity per Contributor (Line Chart)", normalFont));
        graphList.add(new ListItem("Commits per Day (Line Chart)", normalFont));
        document.add(graphList);
        document.add(new Paragraph(" ", normalFont));

        Image pieImage = Image.getInstance(piePath);
        pieImage.scalePercent(100f); // Keep Pie Chart as is
        pieImage.setAlignment(Image.MIDDLE);
        document.add(pieImage);

        document.newPage();
        Paragraph chartTitle2 = new Paragraph("Impact Analysis (Stacked):", sectionFont);
        chartTitle2.setSpacingBefore(15f);
        document.add(chartTitle2);
        Image barImage = Image.getInstance(barPath);
        barImage.scaleToFit(document.getPageSize().getWidth() - 100, (document.getPageSize().getHeight() - 150) / 2);
        barImage.setAlignment(Image.MIDDLE);
        document.add(barImage);

        document.newPage();
        Paragraph chartTitle3 = new Paragraph("Recent Commit Activity:", sectionFont);
        chartTitle3.setSpacingBefore(15f);
        document.add(chartTitle3);
        Image lineImage = Image.getInstance(linePath);
        lineImage.scaleToFit(document.getPageSize().getWidth() - 100, (document.getPageSize().getHeight() - 150) / 2);
        lineImage.setAlignment(Image.MIDDLE);
        document.add(lineImage);

        document.newPage();
        Paragraph chartTitle4 = new Paragraph("Daily Activity (Total Impact):", sectionFont);
        chartTitle4.setSpacingBefore(15f);
        document.add(chartTitle4);
        Image calendarImage = Image.getInstance(calendarPath);
        calendarImage.scaleToFit(document.getPageSize().getWidth() - 100, (document.getPageSize().getHeight() - 150) / 2);
        calendarImage.setAlignment(Image.MIDDLE);
        document.add(calendarImage);

        document.newPage();
        Paragraph chartTitle5 = new Paragraph("Daily Activity per Contributor:", sectionFont);
        chartTitle5.setSpacingBefore(15f);
        document.add(chartTitle5);
        Image contribImage = Image.getInstance(contribPath);
        contribImage.scaleToFit(document.getPageSize().getWidth() - 100, (document.getPageSize().getHeight() - 150) / 2);
        contribImage.setAlignment(Image.MIDDLE);
        document.add(contribImage);

        document.newPage();
        Paragraph chartTitle6 = new Paragraph("Commits per Day:", sectionFont);
        chartTitle6.setSpacingBefore(15f);
        document.add(chartTitle6);
        Image cpdImage = Image.getInstance(cpdPath);
        cpdImage.scaleToFit(document.getPageSize().getWidth() - 100, (document.getPageSize().getHeight() - 150) / 2);
        cpdImage.setAlignment(Image.MIDDLE);
        document.add(cpdImage);

        // Back to Portrait for the rest
        document.setPageSize(PageSize.A4);
        document.newPage();

        Paragraph detailTitle = new Paragraph("Detailed Contributor Metrics:", sectionFont);
        Anchor detailAnchor = new Anchor(detailTitle);
        detailAnchor.setName("details");
        detailTitle.setSpacingBefore(15f);
        document.add(detailAnchor);
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

    private void processMarkdownTable(java.util.List<String> tableLines, Document document, Font font) {
        try {
            // Filter out separator lines (e.g. |---|---|)
            java.util.List<String> dataLines = new java.util.ArrayList<>();
            for (String line : tableLines) {
                if (line.contains("---")) continue;
                dataLines.add(line);
            }

            if (dataLines.isEmpty()) return;

            // Determine max columns
            int maxCols = 0;
            for (String line : dataLines) {
                String[] parts = line.split("\\|");
                int cols = 0;
                for (String p : parts) if (!p.trim().isEmpty()) cols++;
                maxCols = Math.max(maxCols, cols);
            }

            if (maxCols == 0) return;

            PdfPTable table = new PdfPTable(maxCols);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10f);
            table.setSpacingAfter(10f);

            for (String line : dataLines) {
                String[] parts = line.split("\\|");
                int count = 0;
                for (String p : parts) {
                    if (p.trim().isEmpty() && count == 0 && line.startsWith("|")) continue; 
                    if (count < maxCols) {
                        table.addCell(new Phrase(p.trim(), font));
                        count++;
                    }
                }
                while (count < maxCols) {
                    table.addCell("");
                    count++;
                }
            }
            document.add(table);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
