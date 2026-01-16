package com.nfv.validator.report;

import com.nfv.validator.model.cnf.CNFChecklistRequest;
import com.nfv.validator.model.comparison.CnfComparison;
import com.nfv.validator.model.comparison.CnfComparison.CnfChecklistResult;
import com.nfv.validator.model.comparison.CnfComparison.ValidationStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Generates Excel reports for CNF Checklist validation results
 */
@Slf4j
public class CNFChecklistExcelGenerator {

    /**
     * Generate CNF Checklist Excel report
     * 
     * @param request Original CNF checklist request
     * @param cnfComparisons List of CNF checklist comparison results (one per namespace)
     * @param outputPath Path to output Excel file
     */
    public void generateReport(CNFChecklistRequest request,
                               List<CnfComparison> cnfComparisons, 
                               String outputPath) throws IOException {
        log.info("Generating CNF Checklist Excel report to: {}", outputPath);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle titleStyle = createTitleStyle(workbook);
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle subHeaderStyle = createSubHeaderStyle(workbook);
            CellStyle matchStyle = createMatchStyle(workbook);
            CellStyle differentStyle = createDifferentStyle(workbook);
            CellStyle missingStyle = createMissingStyle(workbook);
            CellStyle extraStyle = createExtraStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle normalStyle = createNormalStyle(workbook);
            
            // Generate Overview Sheet (Sheet 1)
            generateOverviewSheet(workbook, cnfComparisons, titleStyle, headerStyle, 
                                subHeaderStyle, matchStyle, differentStyle, missingStyle, 
                                extraStyle, boldStyle, normalStyle);
            
            // Generate Summary Sheet (Sheet 2)
            generateSummarySheet(workbook, cnfComparisons, titleStyle, headerStyle, 
                               subHeaderStyle, matchStyle, differentStyle, missingStyle, boldStyle, normalStyle);
            
            // Generate detail sheets for each failed item
            generateDetailSheets(workbook, cnfComparisons, titleStyle, headerStyle, subHeaderStyle,
                               matchStyle, differentStyle, missingStyle, extraStyle, boldStyle, normalStyle);
            
            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
                log.info("CNF Checklist Excel report generated successfully: {}", outputPath);
            }
        }
    }
    
    /**
     * Generate Overview Sheet - Summary statistics and validation items list
     */
    private void generateOverviewSheet(Workbook workbook,
                                      List<CnfComparison> cnfComparisons,
                                      CellStyle titleStyle,
                                      CellStyle headerStyle,
                                      CellStyle subHeaderStyle,
                                      CellStyle matchStyle,
                                      CellStyle differentStyle,
                                      CellStyle missingStyle,
                                      CellStyle extraStyle,
                                      CellStyle boldStyle,
                                      CellStyle normalStyle) {
        
        Sheet sheet = workbook.createSheet("Overview");
        int rowNum = 0;
        
        // ===== TITLE =====
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("CNF CHECKLIST VALIDATION REPORT");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));
        rowNum++; // Empty row
        
        // ===== SUMMARY STATISTICS =====
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        Cell summaryHeaderCell = summaryHeaderRow.createCell(0);
        summaryHeaderCell.setCellValue("VALIDATION SUMMARY");
        summaryHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 5));
        
        // Calculate statistics across all namespaces
        int totalItems = 0;
        int passedItems = 0;
        int failedItems = 0;
        int missingItems = 0;
        
        for (CnfComparison cnfComp : cnfComparisons) {
            totalItems += cnfComp.getItems().size();
            for (CnfChecklistResult item : cnfComp.getItems()) {
                ValidationStatus status = item.getStatus();
                if (status == ValidationStatus.MATCH) {
                    passedItems++;
                } else if (status == ValidationStatus.MISSING_IN_RUNTIME) {
                    missingItems++;
                    failedItems++;
                } else {
                    failedItems++;
                }
            }
        }
        
        double passRate = totalItems > 0 ? (passedItems * 100.0 / totalItems) : 0.0;
        
        // Statistics rows
        createStatRow(sheet, rowNum++, "Total Items:", String.valueOf(totalItems), boldStyle, normalStyle);
        createStatRow(sheet, rowNum++, "Passed:", String.valueOf(passedItems), boldStyle, matchStyle);
        createStatRow(sheet, rowNum++, "Failed:", String.valueOf(failedItems), boldStyle, differentStyle);
        createStatRow(sheet, rowNum++, "Missing in Runtime:", String.valueOf(missingItems), boldStyle, missingStyle);
        createStatRow(sheet, rowNum++, "Pass Rate:", String.format("%.1f%%", passRate), boldStyle, normalStyle);
        
        rowNum += 2; // Empty rows
        
        // ===== VALIDATION ITEMS TABLE =====
        Row itemsHeaderRow = sheet.createRow(rowNum++);
        Cell itemsHeaderCell = itemsHeaderRow.createCell(0);
        itemsHeaderCell.setCellValue("VALIDATION ITEMS");
        itemsHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 7));
        
        // Table header
        Row tableHeaderRow = sheet.createRow(rowNum++);
        createCell(tableHeaderRow, 0, "STT", subHeaderStyle);
        createCell(tableHeaderRow, 1, "VIM/Namespace", subHeaderStyle);
        createCell(tableHeaderRow, 2, "Kind", subHeaderStyle);
        createCell(tableHeaderRow, 3, "Object Name", subHeaderStyle);
        createCell(tableHeaderRow, 4, "Field Key", subHeaderStyle);
        createCell(tableHeaderRow, 5, "Expected Value", subHeaderStyle);
        createCell(tableHeaderRow, 6, "Actual Value", subHeaderStyle);
        createCell(tableHeaderRow, 7, "Status", subHeaderStyle);
        
        // Table data - iterate through all namespaces
        int itemNum = 1;
        for (CnfComparison cnfComp : cnfComparisons) {
            String nsLabel = cnfComp.getVimName() + "/" + cnfComp.getNamespace();
            
            for (CnfChecklistResult item : cnfComp.getItems()) {
                Row dataRow = sheet.createRow(rowNum++);
                
                // STT
                createCell(dataRow, 0, String.valueOf(itemNum++), normalStyle);
                
                // VIM/Namespace
                createCell(dataRow, 1, nsLabel, normalStyle);
                
                // Kind
                createCell(dataRow, 2, item.getKind(), normalStyle);
                
                // Object Name
                createCell(dataRow, 3, item.getObjectName(), normalStyle);
                
                // Field Key
                createCell(dataRow, 4, item.getFieldKey(), normalStyle);
                
                // Expected Value
                String expectedValue = item.getBaselineValue() != null ? item.getBaselineValue() : "";
                createCell(dataRow, 5, expectedValue, normalStyle);
                
                // Actual Value
                String actualValue = item.getActualValue() != null ? item.getActualValue() : "";
                createCell(dataRow, 6, actualValue, normalStyle);
                
                // Status
                ValidationStatus status = item.getStatus();
                CellStyle statusStyle = getStatusCellStyle(status, matchStyle, differentStyle, missingStyle, missingStyle);
                createCell(dataRow, 7, status.toString(), statusStyle);
            }
        }
        
        // Set column widths
        sheet.setColumnWidth(0, 2000);   // STT
        sheet.setColumnWidth(1, 8000);   // VIM/Namespace
        sheet.setColumnWidth(2, 5000);   // Kind
        sheet.setColumnWidth(3, 8000);   // Object Name
        sheet.setColumnWidth(4, 12000);  // Field Key
        sheet.setColumnWidth(5, 8000);   // Expected Value
        sheet.setColumnWidth(6, 8000);   // Actual Value
        sheet.setColumnWidth(7, 6000);   // Status
    }
    
    /**
     * Generate Summary Sheet - Validation summary by namespace
     */
    private void generateSummarySheet(Workbook workbook,
                                     List<CnfComparison> cnfComparisons,
                                     CellStyle titleStyle,
                                     CellStyle headerStyle,
                                     CellStyle subHeaderStyle,
                                     CellStyle matchStyle,
                                     CellStyle differentStyle,
                                     CellStyle missingStyle,
                                     CellStyle boldStyle,
                                     CellStyle normalStyle) {
        
        Sheet sheet = workbook.createSheet("Summary");
        int rowNum = 0;
        
        // ===== TITLE =====
        Row titleRow = sheet.createRow(rowNum++);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("CNF CHECKLIST VALIDATION SUMMARY");
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
        rowNum++; // Empty row
        
        // ===== OVERALL STATISTICS =====
        Row summaryHeaderRow = sheet.createRow(rowNum++);
        Cell summaryHeaderCell = summaryHeaderRow.createCell(0);
        summaryHeaderCell.setCellValue("OVERALL STATISTICS");
        summaryHeaderCell.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 8));
        
        // Calculate overall statistics
        int totalNamespaces = cnfComparisons.size();
        int totalObjects = 0;
        int totalFields = 0;
        int totalFieldMatches = 0;
        
        for (CnfComparison cnfComp : cnfComparisons) {
            // Count unique objects
            Set<String> uniqueObjects = new HashSet<>();
            for (CnfComparison.CnfChecklistResult item : cnfComp.getItems()) {
                uniqueObjects.add(item.getKind() + "/" + item.getObjectName());
            }
            totalObjects += uniqueObjects.size();
            
            totalFields += cnfComp.getItems().size();
            for (CnfComparison.CnfChecklistResult item : cnfComp.getItems()) {
                if (item.getStatus() == ValidationStatus.MATCH) {
                    totalFieldMatches++;
                }
            }
        }
        
        double overallFieldMatchRate = totalFields > 0 ? (totalFieldMatches * 100.0 / totalFields) : 0.0;
        
        createStatRow(sheet, rowNum++, "Total Namespaces:", String.valueOf(totalNamespaces), boldStyle, normalStyle);
        createStatRow(sheet, rowNum++, "Total Objects:", String.valueOf(totalObjects), boldStyle, normalStyle);
        createStatRow(sheet, rowNum++, "Total Fields Validated:", String.valueOf(totalFields), boldStyle, normalStyle);
        createStatRow(sheet, rowNum++, "Total Field Matches:", String.valueOf(totalFieldMatches), boldStyle, matchStyle);
        createStatRow(sheet, rowNum++, "Overall Field Match Rate:", String.format("%.1f%%", overallFieldMatchRate), boldStyle, normalStyle);
        
        rowNum += 2; // Empty rows
        
        // ===== VALIDATION RESULTS TABLE =====
        Row tableHeaderLabelRow = sheet.createRow(rowNum++);
        Cell tableHeaderLabel = tableHeaderLabelRow.createCell(0);
        tableHeaderLabel.setCellValue("VALIDATION RESULTS BY NAMESPACE");
        tableHeaderLabel.setCellStyle(headerStyle);
        sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 8));
        
        // Table header
        Row tableHeaderRow = sheet.createRow(rowNum++);
        createCell(tableHeaderRow, 0, "Validation Name", subHeaderStyle);
        createCell(tableHeaderRow, 1, "Status", subHeaderStyle);
        createCell(tableHeaderRow, 2, "Result", subHeaderStyle);
        createCell(tableHeaderRow, 3, "Objects", subHeaderStyle);
        createCell(tableHeaderRow, 4, "Obj Matches", subHeaderStyle);
        createCell(tableHeaderRow, 5, "Obj Match Rate (%)", subHeaderStyle);
        createCell(tableHeaderRow, 6, "Fields", subHeaderStyle);
        createCell(tableHeaderRow, 7, "Field Matches", subHeaderStyle);
        createCell(tableHeaderRow, 8, "Field Match Rate (%)", subHeaderStyle);
        
        // Table data - one row per namespace
        for (CnfComparison cnfComp : cnfComparisons) {
            Row dataRow = sheet.createRow(rowNum++);
            
            String validationName = cnfComp.getVimName() + "/" + cnfComp.getNamespace();
            
            // Count unique objects in this namespace
            Set<String> uniqueObjects = new HashSet<>();
            int objectMatches = 0;
            int fieldCount = cnfComp.getItems().size();
            int fieldMatches = 0;
            
            Map<String, Boolean> objectStatus = new HashMap<>();
            for (CnfComparison.CnfChecklistResult item : cnfComp.getItems()) {
                String objKey = item.getKind() + "/" + item.getObjectName();
                uniqueObjects.add(objKey);
                
                // Track if object has all fields matching
                if (item.getStatus() == ValidationStatus.MATCH) {
                    fieldMatches++;
                    objectStatus.putIfAbsent(objKey, true);
                } else {
                    objectStatus.put(objKey, false);
                }
            }
            
            // Count objects where all fields match
            for (Boolean allMatch : objectStatus.values()) {
                if (allMatch) objectMatches++;
            }
            
            int totalObjs = uniqueObjects.size();
            double objMatchRate = totalObjs > 0 ? (objectMatches * 100.0 / totalObjs) : 0.0;
            double fieldMatchRate = fieldCount > 0 ? (fieldMatches * 100.0 / fieldCount) : 0.0;
            
            // Determine overall status
            String status = fieldMatchRate == 100.0 ? "PASS" : "FAIL";
            CellStyle statusStyle = fieldMatchRate == 100.0 ? matchStyle : differentStyle;
            
            // Validation Name
            createCell(dataRow, 0, validationName, normalStyle);
            
            // Status
            createCell(dataRow, 1, status, statusStyle);
            
            // Result (summary)
            String result = fieldMatches + "/" + fieldCount + " fields matched";
            createCell(dataRow, 2, result, normalStyle);
            
            // Objects
            createCell(dataRow, 3, String.valueOf(totalObjs), normalStyle);
            
            // Obj Matches
            createCell(dataRow, 4, String.valueOf(objectMatches), normalStyle);
            
            // Obj Match Rate
            createCell(dataRow, 5, String.format("%.1f", objMatchRate), normalStyle);
            
            // Fields
            createCell(dataRow, 6, String.valueOf(fieldCount), normalStyle);
            
            // Field Matches
            createCell(dataRow, 7, String.valueOf(fieldMatches), normalStyle);
            
            // Field Match Rate
            CellStyle rateStyle = fieldMatchRate == 100.0 ? matchStyle : 
                                 fieldMatchRate >= 80.0 ? normalStyle : differentStyle;
            createCell(dataRow, 8, String.format("%.1f", fieldMatchRate), rateStyle);
        }
        
        // Set column widths
        sheet.setColumnWidth(0, 8000);   // Validation Name
        sheet.setColumnWidth(1, 3000);   // Status
        sheet.setColumnWidth(2, 8000);   // Result
        sheet.setColumnWidth(3, 3000);   // Objects
        sheet.setColumnWidth(4, 4000);   // Obj Matches
        sheet.setColumnWidth(5, 5000);   // Obj Match Rate
        sheet.setColumnWidth(6, 3000);   // Fields
        sheet.setColumnWidth(7, 4000);   // Field Matches
        sheet.setColumnWidth(8, 5000);   // Field Match Rate
    }
    
    /**
     * Generate detail sheets for each namespace with failed validation items
     */
    private void generateDetailSheets(Workbook workbook,
                                     List<CnfComparison> cnfComparisons,
                                     CellStyle titleStyle,
                                     CellStyle headerStyle,
                                     CellStyle subHeaderStyle,
                                     CellStyle matchStyle,
                                     CellStyle differentStyle,
                                     CellStyle missingStyle,
                                     CellStyle extraStyle,
                                     CellStyle boldStyle,
                                     CellStyle normalStyle) {
        
        int sheetNum = 1;
        for (CnfComparison cnfComp : cnfComparisons) {
            String nsLabel = cnfComp.getVimName() + "/" + cnfComp.getNamespace();
            
            for (CnfChecklistResult item : cnfComp.getItems()) {
                // Only create detail sheets for failed items
                if (item.getStatus() != ValidationStatus.MATCH) {
                    String sheetName = sanitizeSheetName("Detail_" + sheetNum);
                    Sheet sheet = workbook.createSheet(sheetName);
                    
                    int rowNum = 0;
                    
                    // Title
                    Row titleRow = sheet.createRow(rowNum++);
                    Cell titleCell = titleRow.createCell(0);
                    titleCell.setCellValue("VALIDATION DETAIL");
                    titleCell.setCellStyle(titleStyle);
                    sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));
                    rowNum++; // Empty row
                    
                    // Section 1: Object Information (with background color)
                    Row objectHeaderRow = sheet.createRow(rowNum++);
                    Cell objHeaderCell = objectHeaderRow.createCell(0);
                    objHeaderCell.setCellValue("OBJECT INFORMATION");
                    objHeaderCell.setCellStyle(subHeaderStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));
                    
                    // VIM/Namespace
                    Row nsRow = sheet.createRow(rowNum++);
                    createCell(nsRow, 0, "VIM/Namespace:", boldStyle);
                    createCell(nsRow, 1, nsLabel, normalStyle);
                    
                    // Kind
                    Row kindRow = sheet.createRow(rowNum++);
                    createCell(kindRow, 0, "Kind:", boldStyle);
                    createCell(kindRow, 1, item.getKind(), normalStyle);
                    
                    // Object Name
                    Row objectRow = sheet.createRow(rowNum++);
                    createCell(objectRow, 0, "Object Name:", boldStyle);
                    createCell(objectRow, 1, item.getObjectName(), normalStyle);
                    
                    rowNum++; // Empty row
                    
                    // Section 2: Field Validation (with background color)
                    Row fieldHeaderRow = sheet.createRow(rowNum++);
                    Cell fieldHeaderCell = fieldHeaderRow.createCell(0);
                    fieldHeaderCell.setCellValue("FIELD VALIDATION");
                    fieldHeaderCell.setCellStyle(subHeaderStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));
                    
                    // Field Key
                    Row fieldKeyRow = sheet.createRow(rowNum++);
                    createCell(fieldKeyRow, 0, "Field Key:", boldStyle);
                    createCell(fieldKeyRow, 1, item.getFieldKey(), normalStyle);
                
                    // Expected Value
                    Row expectedRow = sheet.createRow(rowNum++);
                    createCell(expectedRow, 0, "Expected Value:", boldStyle);
                    String expectedValue = item.getBaselineValue() != null ? item.getBaselineValue() : "";
                    createCell(expectedRow, 1, expectedValue, normalStyle);
                    
                    // Actual Value
                    Row actualRow = sheet.createRow(rowNum++);
                    createCell(actualRow, 0, "Actual Value:", boldStyle);
                    String actualValue = item.getActualValue() != null ? item.getActualValue() : "";
                    CellStyle valueStyle = getStatusCellStyle(item.getStatus(), matchStyle, 
                                                             differentStyle, missingStyle, extraStyle);
                    createCell(actualRow, 1, actualValue, valueStyle);
                    
                    rowNum++; // Empty row
                    
                    // Section 3: Status (with background color)
                    Row statusHeaderRow = sheet.createRow(rowNum++);
                    Cell statusHeaderCell = statusHeaderRow.createCell(0);
                    statusHeaderCell.setCellValue("VALIDATION RESULT");
                    statusHeaderCell.setCellStyle(subHeaderStyle);
                    sheet.addMergedRegion(new CellRangeAddress(rowNum - 1, rowNum - 1, 0, 1));
                    
                    // Status
                    Row statusRow = sheet.createRow(rowNum++);
                    createCell(statusRow, 0, "Status:", boldStyle);
                    CellStyle statusStyle = getStatusCellStyle(item.getStatus(), matchStyle, 
                                                              differentStyle, missingStyle, extraStyle);
                    createCell(statusRow, 1, item.getStatus().toString(), statusStyle);
                    
                    // Message
                    if (item.getMessage() != null && !item.getMessage().isEmpty()) {
                        Row messageRow = sheet.createRow(rowNum++);
                        createCell(messageRow, 0, "Message:", boldStyle);
                        createCell(messageRow, 1, item.getMessage(), normalStyle);
                    }
                    
                    // Set column widths
                    sheet.setColumnWidth(0, 6000);
                    sheet.setColumnWidth(1, 15000);
                    
                    sheetNum++;
                }
            }
        }
    }
    
    /**
     * Create a statistics row with label and value
     */
    private void createStatRow(Sheet sheet, int rowNum, String label, String value, 
                              CellStyle labelStyle, CellStyle valueStyle) {
        Row row = sheet.createRow(rowNum);
        createCell(row, 0, label, labelStyle);
        createCell(row, 1, value, valueStyle);
    }
    
    /**
     * Create a cell with value and style
     */
    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
    
    /**
     * Get cell style based on comparison status
     */
    private CellStyle getStatusCellStyle(ValidationStatus status, CellStyle matchStyle, 
                                        CellStyle differentStyle, CellStyle missingStyle, 
                                        CellStyle extraStyle) {
        switch (status) {
            case MATCH:
                return matchStyle;
            case DIFFERENT:
                return differentStyle;
            case MISSING_IN_RUNTIME:
                return missingStyle;
            case ERROR:
                return extraStyle;
            default:
                return null;
        }
    }
    
    /**
     * Sanitize sheet name to comply with Excel naming rules
     */
    private String sanitizeSheetName(String name) {
        // Excel sheet names: max 31 chars, no invalid chars: / \ ? * [ ]
        String sanitized = name.replaceAll("[/\\\\?*\\[\\]]", "_");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        return sanitized;
    }
    
    // ===== STYLE CREATION METHODS =====
    
    /**
     * Create title style (large, bold, centered)
     */
    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 18);
        font.setColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * Create header cell style (dark blue background, white text, bold)
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 12);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create sub-header cell style (blue background, white text)
     */
    private CellStyle createSubHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create MATCH cell style (green background)
     */
    private CellStyle createMatchStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create DIFFERENT/MISMATCH cell style (orange background)
     */
    private CellStyle createDifferentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create MISSING cell style (red/coral background)
     */
    private CellStyle createMissingStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create EXTRA cell style (light blue background)
     */
    private CellStyle createExtraStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create bold text style
     */
    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create normal text style with borders
     */
    private CellStyle createNormalStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
}
