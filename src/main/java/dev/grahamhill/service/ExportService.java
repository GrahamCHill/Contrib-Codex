package dev.grahamhill.service;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import dev.grahamhill.model.ContributorStats;
import dev.grahamhill.model.MeaningfulChangeAnalysis;
import dev.grahamhill.model.FileChange;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.util.List;

public class ExportService {

    public void exportToPdf(List<ContributorStats> stats, List<dev.grahamhill.model.CommitInfo> allCommits, MeaningfulChangeAnalysis meaningfulAnalysis, String filePath, String piePath, String barPath, String linePath, String calendarPath, String contribPath, String cpdPath, String aiReport, java.util.Map<String, String> mdSections, String coverHtml, String coverBasePath, int tableLimit, java.util.Map<String, String> metadata) throws Exception {
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
                // Render HTML cover page using OpenHTMLtoPDF to a temporary byte array
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(coverHtml, coverBasePath != null ? new File(coverBasePath).toURI().toString() : "");
                builder.toStream(baos);
                builder.run();

                // Import the rendered cover page into our OpenPDF document
                PdfReader reader = new PdfReader(baos.toByteArray());
                PdfContentByte cb = writer.getDirectContent();
                for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                    document.newPage();
                    PdfImportedPage page = writer.getImportedPage(reader, i);
                    cb.addTemplate(page, 0, 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Fallback
                document.newPage();
                Paragraph coverPara = new Paragraph("Report Cover Page", headerFont);
                coverPara.setAlignment(Element.ALIGN_CENTER);
                document.add(coverPara);
            }
            document.newPage();
        }
        event.setCoverPage(false);

        // 00. Document Control
        document.newPage();
        document.add(new Paragraph("00. Document Control", headerFont));
        Anchor docControlAnchor = new Anchor(" ");
        docControlAnchor.setName("doc_control");
        document.add(docControlAnchor);
        document.add(new Paragraph(" ", normalFont));

        document.add(new Paragraph("00.1 Version History", sectionFont));
        PdfPTable versionTable = new PdfPTable(4);
        versionTable.setWidthPercentage(100);
        versionTable.setSpacingBefore(5f);
        versionTable.setSpacingAfter(15f);
        versionTable.addCell("Version");
        versionTable.addCell("Date");
        versionTable.addCell("Author");
        versionTable.addCell("Description");
        versionTable.addCell("1.0");
        versionTable.addCell(java.time.LocalDate.now().toString());
        versionTable.addCell(System.getProperty("user.name"));
        versionTable.addCell("Initial Report Generation");
        document.add(versionTable);

        document.add(new Paragraph("00.2 Metrics Tooling & Generation Method", sectionFont));
        Paragraph toolingPara = new Paragraph("This report was generated using Contrib Codex, a Git forensics and analytics tool. " +
                "Metrics are extracted directly from the Git repository using JGit. " +
                "Qualitative analysis and engineering audits are performed by Large Language Models (LLM) " +
                "based on the extracted quantitative data.", normalFont);
        toolingPara.setSpacingBefore(5f);
        toolingPara.setSpacingAfter(15f);
        document.add(toolingPara);

        document.add(new Paragraph("00.3 Generation Metadata", sectionFont));
        PdfPTable metaTable = new PdfPTable(2);
        metaTable.setWidthPercentage(100);
        metaTable.setSpacingBefore(5f);
        metaTable.setSpacingAfter(15f);
        metaTable.setWidths(new float[]{1, 2});

        if (metadata != null) {
            metadata.forEach((k, v) -> {
                metaTable.addCell(new Phrase(k, new Font(Font.HELVETICA, 10, Font.BOLD)));
                metaTable.addCell(new Phrase(v, normalFont));
            });
        }
        document.add(metaTable);
        document.newPage();

        // Index Page
        document.add(new Paragraph("Report Index", headerFont));
        Anchor reportIndexAnchor = new Anchor(" ");
        reportIndexAnchor.setName("report_index");
        document.add(reportIndexAnchor);
        document.add(new Paragraph(" ", normalFont));
        
        PdfPTable indexTable = new PdfPTable(3);
        indexTable.setWidthPercentage(100);
        indexTable.setWidths(new float[]{4, 1, 1});
        
        // Add headers for Index Table
        PdfPCell h1 = new PdfPCell(new Phrase("Section", sectionFont));
        h1.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        indexTable.addCell(h1);
        PdfPCell h2 = new PdfPCell(new Phrase("Page", sectionFont));
        h2.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        indexTable.addCell(h2);
        PdfPCell h3 = new PdfPCell(new Phrase("Link", sectionFont));
        h3.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
        indexTable.addCell(h3);

        addIndexRow(indexTable, "Document Control", "doc_control", 2, normalFont);
        addIndexRow(indexTable, "Report Index", "report_index", 3, normalFont);
        addIndexRow(indexTable, "Introduction & Purpose", "intro", 4, normalFont);
        
        int currentPage = 5; // Estimated start page after index

        if (mdSections != null) {
            for (String title : mdSections.keySet()) {
                // Formatting title: swaps underscores for spaces and puts a fullstop after the first number
                String formattedTitle = title.replace("_", " ").replaceFirst("(\\d+)", "$1.");
                // Since MD sections are now part of AI review, we can omit them from Index if desired, 
                // but the user might still want to see the titles in the TOC.
                // However, they aren't separate sections in the PDF anymore.
                // addIndexRow(indexTable, "External Section: " + formattedTitle, "md_" + title, currentPage++, normalFont);
            }
        }

        if (aiReport != null) {
            addIndexRow(indexTable, "AI Assisted Review", "ai_review", currentPage, normalFont);
            // AI review can be multi-page, estimate pages based on lines
            int aiPages = Math.max(1, aiReport.split("\n").length / 40);
            currentPage += aiPages;
        }

        if (aiReport != null && !aiReport.isEmpty() && meaningfulAnalysis != null) {
            addIndexRow(indexTable, "Meaningful Change Detection", "meaningful", currentPage++, normalFont);
        }

        addIndexRow(indexTable, "Visual Analytics (Charts)", "charts", currentPage, normalFont);
        addIndexRow(indexTable, "  - Commits by Contributor (Pie Chart)", "chart1", currentPage++, normalFont);
        addIndexRow(indexTable, "  - Impact Analysis (Stacked Bar Chart)", "chart2", currentPage++, normalFont);
        addIndexRow(indexTable, "  - Recent Commit Activity (Line Chart)", "chart3", currentPage++, normalFont);
        addIndexRow(indexTable, "  - Daily Activity - Total Impact (Line Chart)", "chart4", currentPage++, normalFont);
        addIndexRow(indexTable, "  - Daily Activity per Contributor (Line Chart)", "chart5", currentPage++, normalFont);
        addIndexRow(indexTable, "  - Commits per Day (Line Chart)", "chart6", currentPage++, normalFont);
        
        addIndexRow(indexTable, "Detailed Contributor Metrics", "details", currentPage++, normalFont);
        
        if (allCommits != null && !allCommits.isEmpty()) {
            addIndexRow(indexTable, "Appendix A: Complete Commit History", "commits", currentPage, normalFont);
        }
        
        document.add(indexTable);
        document.newPage();

        // Introduction
        Paragraph introHeader = new Paragraph("Contrib Codex Report", headerFont);
        Anchor introAnchor = new Anchor(introHeader);
        introAnchor.setName("intro");
        document.add(introAnchor);
        
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
        

        // AI Assisted Review Section
        if (aiReport != null && !aiReport.isEmpty()) {
            document.newPage(); // Start AI Review on a new page
            Paragraph aiTitle = new Paragraph("AI Assisted Review", sectionFont);
            Anchor aiAnchor = new Anchor(aiTitle);
            aiAnchor.setName("ai_review");
            aiTitle.setSpacingBefore(15f);
            document.add(aiAnchor);

            Paragraph aiExplanation = new Paragraph("This section contains an AI assisted review of the project. " +
                    "The AI assists by analyzing git metrics, commit messages, and project structure to provide insights into code quality, " +
                    "contributor impact, and potential risks. It transforms raw data into a structured engineering audit, " +
                    "highlighting patterns that may require further human investigation.", smallFont);
            aiExplanation.setSpacingAfter(10f);
            document.add(aiExplanation);
            
            String[] lines = aiReport.split("\n");
            StringBuilder currentText = new StringBuilder();
            
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                // Replace literal \t with spaces for PDF rendering if it survived
                line = line.replace("\t", "    ");
                if (line.isEmpty()) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    continue;
                }

                if (line.startsWith("# ")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    document.newPage(); // Each markdown section (top-level header) on a new page
                    Paragraph h1Header = new Paragraph(line.substring(2).trim(), headerFont);
                    h1Header.setSpacingBefore(15f);
                    h1Header.setSpacingAfter(10f);
                    document.add(h1Header);
                } else if (line.startsWith("## ")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    Paragraph h2Header = new Paragraph(line.substring(3).trim(), sectionFont);
                    h2Header.setSpacingBefore(12f);
                    h2Header.setSpacingAfter(8f);
                    document.add(h2Header);
                } else if (line.startsWith("### ")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    Paragraph h3Header = new Paragraph(line.substring(4).trim(), new Font(Font.HELVETICA, 12, Font.BOLD));
                    h3Header.setSpacingBefore(10f);
                    h3Header.setSpacingAfter(6f);
                    document.add(h3Header);
                } else if (line.startsWith("#### ")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    Paragraph h4Header = new Paragraph(line.substring(5).trim(), new Font(Font.HELVETICA, 11, Font.BOLD));
                    h4Header.setSpacingBefore(8f);
                    h4Header.setSpacingAfter(4f);
                    document.add(h4Header);
                } else if (line.startsWith("##### ")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    Paragraph h5Header = new Paragraph(line.substring(6).trim(), new Font(Font.HELVETICA, 10, Font.BOLD));
                    h5Header.setSpacingBefore(6f);
                    h5Header.setSpacingAfter(2f);
                    document.add(h5Header);
                } else if (line.startsWith("$\\boxed{") && line.endsWith("}$")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    String boxedText = line.substring(9, line.length() - 2).trim();
                    PdfPTable boxedTable = new PdfPTable(1);
                    boxedTable.setWidthPercentage(100);
                    boxedTable.setSpacingBefore(10f);
                    boxedTable.setSpacingAfter(10f);
                    PdfPCell cell = new PdfPCell(processInlineFormatting(boxedText, new Font(Font.HELVETICA, 11, Font.BOLD)));
                    cell.setPadding(10f);
                    cell.setBorderWidth(1.5f);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    boxedTable.addCell(cell);
                    document.add(boxedTable);
                } else if (line.startsWith("**") && line.endsWith("**") && line.length() > 4) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    Paragraph bold = new Paragraph(line.substring(2, line.length() - 2).trim(), new Font(Font.HELVETICA, 11, Font.BOLD));
                    bold.setSpacingBefore(5f);
                    bold.setSpacingAfter(5f);
                    document.add(bold);
                } else if (line.matches("^\\d+\\.\\s+.*")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    // Extract the text after "1. "
                    String listItemText = line.replaceAll("^\\d+\\.\\s+", "");
                    Paragraph listItem = processInlineFormatting(listItemText, normalFont);
                    listItem.setIndentationLeft(20f);
                    listItem.setSpacingAfter(2f);
                    // Add a bullet or keep the number? User said "1. some text and 2. some text".
                    // Let's keep the number if it matches the pattern exactly.
                    Paragraph numberedItem = new Paragraph();
                    String number = line.substring(0, line.indexOf(".") + 1);
                    numberedItem.add(new Chunk(number + " ", new Font(normalFont.getFamily(), normalFont.getSize(), Font.BOLD)));
                    numberedItem.add(processInlineFormatting(listItemText, normalFont));
                    numberedItem.setIndentationLeft(20f);
                    numberedItem.setSpacingAfter(2f);
                    document.add(numberedItem);
                } else if (line.startsWith("- ")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    String listItemText = line.substring(2).trim();
                    Paragraph bulletItem = new Paragraph();
                    bulletItem.add(new Chunk("• ", new Font(normalFont.getFamily(), normalFont.getSize(), Font.BOLD)));
                    bulletItem.add(processInlineFormatting(listItemText, normalFont));
                    bulletItem.setIndentationLeft(20f);
                    bulletItem.setSpacingAfter(2f);
                    document.add(bulletItem);
                } else if (line.startsWith("|")) {
                    if (currentText.length() > 0) {
                        Paragraph p = processInlineFormatting(currentText.toString(), normalFont);
                        p.setSpacingAfter(5f);
                        document.add(p);
                        currentText = new StringBuilder();
                    }
                    
                    // Table detection and processing
                    java.util.List<String> tableLines = new java.util.ArrayList<>();
                    while (i < lines.length && (lines[i].trim().startsWith("|") || (lines[i].trim().isEmpty() && i + 1 < lines.length && lines[i+1].trim().startsWith("|")))) {
                        if (!lines[i].trim().isEmpty()) {
                            tableLines.add(lines[i].trim());
                        }
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
                document.add(processInlineFormatting(currentText.toString(), normalFont));
            }
            document.add(spacing);
        }

        // Meaningful Change Detection Section
        if (aiReport != null && !aiReport.isEmpty() && meaningfulAnalysis != null) {
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

            Paragraph mcExplanation = new Paragraph("The Meaningful Change Score is a heuristic metric used to evaluate the quality and impact of code changes. " +
                    "For the repository as a whole, it is calculated based on the proportion of 'Source Code' (70%) and 'Tests' (30%) relative to total insertions, " +
                    "filtering out non-meaningful changes. " +
                    "For individual contributors, the score is either calculated via heuristic patterns or, in this report, refined by AI to account for commit message quality, " +
                    "iterative development, and qualitative impact.", normalFont);
            mcExplanation.setSpacingAfter(15f);
            document.add(mcExplanation);

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

            // Add overall repository score explanation if AI assisted
            if (aiReport != null && !aiReport.isEmpty()) {
                Paragraph aiScoreNote = new Paragraph("Note: Contributor-specific meaningful scores shown in the tables below are generated by AI, taking into account commit message quality and development patterns.", smallFont);
                aiScoreNote.setSpacingAfter(10f);
                document.add(aiScoreNote);
            }

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
        // Pie chart is 1080x1080, scale it to fit nicely while maintaining square aspect
        float pieSize = Math.min(document.getPageSize().getWidth() * 0.85f, (document.getPageSize().getHeight() - 150) * 0.85f);
        pieImage.scaleToFit(pieSize, pieSize); 
        pieImage.setAlignment(Image.MIDDLE);
        document.add(pieImage);

        document.newPage();
        Paragraph chartTitle2 = new Paragraph("Impact Analysis (Stacked):", sectionFont);
        chartTitle2.setSpacingBefore(15f);
        document.add(chartTitle2);
        Image barImage = Image.getInstance(barPath);
        barImage.scaleToFit(document.getPageSize().getWidth() * 0.9f, (document.getPageSize().getHeight() - 150) * 0.9f);
        barImage.setAlignment(Image.MIDDLE);
        document.add(barImage);

        document.newPage();
        Paragraph chartTitle3 = new Paragraph("Recent Commit Activity:", sectionFont);
        chartTitle3.setSpacingBefore(15f);
        document.add(chartTitle3);
        Image lineImage = Image.getInstance(linePath);
        lineImage.scaleToFit(document.getPageSize().getWidth() * 0.9f, (document.getPageSize().getHeight() - 150) * 0.9f);
        lineImage.setAlignment(Image.MIDDLE);
        document.add(lineImage);

        document.newPage();
        Paragraph chartTitle4 = new Paragraph("Daily Activity (Total Impact):", sectionFont);
        chartTitle4.setSpacingBefore(15f);
        document.add(chartTitle4);
        Image calendarImage = Image.getInstance(calendarPath);
        calendarImage.scaleToFit(document.getPageSize().getWidth() * 0.9f, (document.getPageSize().getHeight() - 150) * 0.9f);
        calendarImage.setAlignment(Image.MIDDLE);
        document.add(calendarImage);

        document.newPage();
        Paragraph chartTitle5 = new Paragraph("Daily Activity per Contributor:", sectionFont);
        chartTitle5.setSpacingBefore(15f);
        document.add(chartTitle5);
        Image contribImage = Image.getInstance(contribPath);
        contribImage.scaleToFit(document.getPageSize().getWidth() * 0.9f, (document.getPageSize().getHeight() - 150) * 0.9f);
        contribImage.setAlignment(Image.MIDDLE);
        document.add(contribImage);

        document.newPage();
        Paragraph chartTitle6 = new Paragraph("Commits per Day:", sectionFont);
        chartTitle6.setSpacingBefore(15f);
        document.add(chartTitle6);
        Image cpdImage = Image.getInstance(cpdPath);
        cpdImage.scaleToFit(document.getPageSize().getWidth() * 0.9f, (document.getPageSize().getHeight() - 150) * 0.9f);
        cpdImage.setAlignment(Image.MIDDLE);
        document.add(cpdImage);

        // Back to Portrait for the rest
        document.setPageSize(PageSize.A4);
        document.newPage();

        // Set to landscape for the table
        document.setPageSize(PageSize.A4.rotate());
        document.newPage();

        Paragraph detailTitle = new Paragraph("Detailed Contributor Metrics:", sectionFont);
        Anchor detailAnchor = new Anchor(detailTitle);
        detailAnchor.setName("details");
        detailTitle.setSpacingBefore(15f);
        document.add(detailAnchor);
        document.add(spacing);

        // Group others for the table
        List<ContributorStats> tableStats = groupOthers(stats, tableLimit);

        PdfPTable table = new PdfPTable(aiReport != null && !aiReport.isEmpty() ? 12 : 10);
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
        if (aiReport != null && !aiReport.isEmpty()) {
            table.addCell("AI Prob");
            table.addCell("Meaningful Score");
        }

        for (ContributorStats stat : tableStats) {
            String displayName = stat.name();
            if (displayName.contains("<") && displayName.contains(">")) {
                displayName = displayName.substring(0, displayName.indexOf("<")).trim();
            }
            table.addCell(displayName);
            table.addCell(stat.gender());
            table.addCell(String.valueOf(stat.commitCount()));
            table.addCell(String.valueOf(stat.mergeCount()));
            table.addCell(String.valueOf(stat.linesAdded()));
            table.addCell(String.valueOf(stat.linesDeleted()));
            table.addCell(String.valueOf(stat.filesAdded()));
            table.addCell(String.valueOf(stat.filesEdited()));
            table.addCell(String.valueOf(stat.filesDeletedCount()));
            table.addCell(formatLanguages(stat.languageBreakdown()));
            if (aiReport != null && !aiReport.isEmpty()) {
                table.addCell(String.format("%.1f%%", stat.averageAiProbability() * 100));
                table.addCell(String.format("%.1f/100", stat.meaningfulChangeScore()));
            }
        }

        document.add(table);

        // Commit History Table
        if (allCommits != null && !allCommits.isEmpty()) {
            document.setPageSize(PageSize.A4.rotate());
            document.newPage();
            
            Paragraph appendixTitle = new Paragraph("Appendix A", headerFont);
            appendixTitle.setSpacingBefore(15f);
            document.add(appendixTitle);
            
            Paragraph commitTitle = new Paragraph("Complete Commit History:", sectionFont);
            Anchor commitAnchor = new Anchor(commitTitle);
            commitAnchor.setName("commits");
            commitTitle.setSpacingBefore(5f);
            document.add(commitAnchor);
            document.add(spacing);

            PdfPTable commitTable = new PdfPTable(5);
            commitTable.setWidthPercentage(100);
            commitTable.setSpacingBefore(5f);
            commitTable.setWidths(new float[]{1, 2, 2, 4, 3});
            
            commitTable.addCell("Date");
            commitTable.addCell("Branch");
            commitTable.addCell("Author");
            commitTable.addCell("Message");
            commitTable.addCell("Files Changed");

            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("EEEE dd MMM yy", java.util.Locale.ENGLISH);

            for (dev.grahamhill.model.CommitInfo ci : allCommits) {
                String dateStr = ci.timestamp().format(dtf);
                commitTable.addCell(new Phrase(dateStr, smallFont));
                
                commitTable.addCell(new Phrase(ci.branch(), smallFont));

                String authorName = ci.authorName();
                if (authorName.contains("<") && authorName.contains(">")) {
                    authorName = authorName.substring(0, authorName.indexOf("<")).trim();
                }
                commitTable.addCell(new Phrase(authorName, smallFont));
                commitTable.addCell(new Phrase(ci.message(), smallFont));
                
                String filesStr = String.format("+%d / e%d / -%d", ci.filesAdded(), ci.filesEdited(), ci.filesDeleted());
                commitTable.addCell(new Phrase(filesStr, smallFont));
            }
            document.add(commitTable);
        }

        document.close();
    }

    private Paragraph processInlineFormatting(String text, Font defaultFont) {
        Paragraph p = new Paragraph();
        p.setFont(defaultFont);
        
        // Basic parser for **bold**, *italic*, and `code`
        String regex = "(\\*\\*.*?\\*\\*|\\*.*?\\*|`.*?`)";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                p.add(new Chunk(text.substring(lastEnd, matcher.start()), defaultFont));
            }
            
            String match = matcher.group();
            if (match.startsWith("**")) {
                p.add(new Chunk(match.substring(2, match.length() - 2), new Font(Font.HELVETICA, defaultFont.getSize(), Font.BOLD)));
            } else if (match.startsWith("*")) {
                p.add(new Chunk(match.substring(1, match.length() - 1), new Font(Font.HELVETICA, defaultFont.getSize(), Font.ITALIC)));
            } else if (match.startsWith("`")) {
                p.add(new Chunk(match.substring(1, match.length() - 1), new Font(Font.COURIER, defaultFont.getSize(), Font.NORMAL, java.awt.Color.DARK_GRAY)));
            }
            lastEnd = matcher.end();
        }
        
        if (lastEnd < text.length()) {
            p.add(new Chunk(text.substring(lastEnd), defaultFont));
        }
        
        return p;
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
            
            // Header: Logo scaled to 50px height, 16px from top
            if (logoPath != null) {
                try {
                    java.io.File logoFile = new java.io.File(logoPath);
                    if (logoFile.exists()) {
                        Image logo = Image.getInstance(logoPath);
                        logo.scaleToFit(500, 20); // Max width 500, height 20
                        // Shifted slightly more right by using a small positive offset to the calculation or just moving it
                        logo.setAbsolutePosition(pageSize.getRight() - document.rightMargin() - logo.getScaledWidth() + 15, pageSize.getTop() - 16 - 20);
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

    private void addIndexRow(PdfPTable table, String text, String destination, int pageNumber, Font font) {
        Chunk chunk = new Chunk(text, font);
        chunk.setLocalGoto(destination);
        table.addCell(new Phrase(chunk));
        
        table.addCell(new Phrase(String.valueOf(pageNumber), font));
        
        Chunk goChunk = new Chunk("Go", font);
        goChunk.setLocalGoto(destination);
        table.addCell(new Phrase(goChunk));
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

        boolean oTouchedTests = others.stream().anyMatch(ContributorStats::touchedTests);

        top.add(new ContributorStats("Others", "others@example.com", "unknown", oCommits, oMerges, oAdded, oDeleted, oLangs, avgAi, oFAdded, oFEdited, oFDeleted, avgMeaningful, oTouchedTests));
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
                boolean isHeader = dataLines.indexOf(line) == 0;
                for (String p : parts) {
                    if (p.trim().isEmpty() && count == 0 && line.startsWith("|")) continue; 
                    if (count < maxCols) {
                        PdfPCell cell = new PdfPCell(new Phrase(p.trim(), isHeader ? new Font(Font.HELVETICA, 10, Font.BOLD) : font));
                        if (isHeader) {
                            cell.setBackgroundColor(java.awt.Color.LIGHT_GRAY);
                            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                        }
                        cell.setPadding(5f);
                        table.addCell(cell);
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
