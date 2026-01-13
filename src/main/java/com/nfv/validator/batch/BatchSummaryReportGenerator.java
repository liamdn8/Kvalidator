package com.nfv.validator.batch;

import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.batch.BatchExecutionResult;
import com.nfv.validator.model.batch.ValidationRequest;
import com.nfv.validator.model.comparison.ComparisonStatus;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.comparison.ObjectComparison;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Generates a consolidated Excel report from batch validation results
 * Includes:
 * - Sheet 1: Detail Summary (overview of all comparisons with VIM-level status)
 * - Sheet 2+: Individual comparison Summary sheets (same format as individual reports)
 */
@Slf4j
public class BatchSummaryReportGenerator {
    
    /**
     * Generate consolidated batch summary report
     * 
     * @param batchResult The batch execution result
     * @param requestData Map of request name to execution data (namespaceModels, comparisons)
     * @param outputPath Path to output Excel file
     */
    public void generateBatchSummaryReport(
            BatchExecutionResult batchResult,
            Map<String, RequestExecutionData> requestData,
            String outputPath) throws IOException {
        
        log.info("Generating batch summary report to: {}", outputPath);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle baselineStyle = createBaselineStyle(workbook);
            CellStyle matchStyle = createMatchStyle(workbook);
            CellStyle mismatchStyle = createMismatchStyle(workbook);
            CellStyle missingStyle = createMissingStyle(workbook);
            CellStyle differentStyle = createDifferentStyle(workbook);
            
            // Sheet 1: Detail Summary (VIM-level overview of all comparisons)
            generateDetailSummarySheet(workbook, batchResult, requestData, 
                    headerStyle, baselineStyle, matchStyle, mismatchStyle, missingStyle, differentStyle);
            
            // Sheets 2+: Individual comparison Summary sheets (exact format as individual reports)
            for (Map.Entry<String, RequestExecutionData> entry : requestData.entrySet()) {
                String requestName = entry.getKey();
                RequestExecutionData data = entry.getValue();
                
                if (data.namespaceModels != null && data.comparisons != null) {
                    // Generate Summary sheet using same logic as ExcelReportGenerator
                    generateIndividualComparisonSheet(workbook, requestName, data,
                            headerStyle, baselineStyle, matchStyle, mismatchStyle, missingStyle, differentStyle);
                }
            }
            
            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
                log.info("Batch summary report generated successfully: {}", outputPath);
            }
        }
    }
    
    /**
     * Generate the Detail Summary sheet
     * Column headers show VIM names (cluster names)
     * Rows show comparison names with namespace-level status
     */
    private void generateDetailSummarySheet(
            Workbook workbook,
            BatchExecutionResult batchResult,
            Map<String, RequestExecutionData> requestData,
            CellStyle headerStyle,
            CellStyle baselineStyle,
            CellStyle matchStyle,
            CellStyle mismatchStyle,
            CellStyle missingStyle,
            CellStyle differentStyle) {
        
        Sheet sheet = workbook.createSheet("Detail Summary");
        
        // Collect all unique namespaces (VIM/namespace pairs) across all requests
        Set<String> allNamespaces = new LinkedHashSet<>();
        
        for (RequestExecutionData data : requestData.values()) {
            if (data.namespaceModels != null) {
                for (FlatNamespaceModel model : data.namespaceModels) {
                    String nsLabel = model.getClusterName() + "/" + model.getName();
                    allNamespaces.add(nsLabel);
                }
            }
        }
        
        // Create header row with namespace labels
        Row headerRow = sheet.createRow(0);
        createCell(headerRow, 0, "STT", headerStyle);
        createCell(headerRow, 1, "Comparison Name", headerStyle);
        
        int colIndex = 2;
        Map<String, Integer> namespaceColumnMap = new LinkedHashMap<>();
        for (String nsLabel : allNamespaces) {
            createCell(headerRow, colIndex, nsLabel, headerStyle);
            namespaceColumnMap.put(nsLabel, colIndex);
            colIndex++;
        }
        
        // Data rows - one row per comparison request
        int rowNum = 1;
        int stt = 1;
        
        for (BatchExecutionResult.RequestResult reqResult : batchResult.getRequestResults()) {
            String requestName = reqResult.getRequestName();
            RequestExecutionData data = requestData.get(requestName);
            
            if (data == null || !reqResult.isSuccess()) {
                continue;
            }
            
            Row row = sheet.createRow(rowNum++);
            createCell(row, 0, String.valueOf(stt++), null);
            
            // Comparison name only (no namespace info in Detail Summary)
            createCell(row, 1, requestName, null);
            
            // Get namespaces involved in this comparison
            Set<String> namespacesInComparison = new LinkedHashSet<>();
            for (FlatNamespaceModel model : data.namespaceModels) {
                String nsLabel = model.getClusterName() + "/" + model.getName();
                namespacesInComparison.add(nsLabel);
            }
            
            // Fill status for each namespace
            if (data.namespaceModels != null && data.comparisons != null) {
                FlatNamespaceModel baseline = data.namespaceModels.get(0);
                String baselineLabel = baseline.getClusterName() + "/" + baseline.getName();
                
                // Mark baseline namespace
                Integer baselineCol = namespaceColumnMap.get(baselineLabel);
                if (baselineCol != null) {
                    String cellValue = "BASELINE\n(" + baseline.getName() + ")";
                    CellStyle wrappedBaselineStyle = workbook.createCellStyle();
                    wrappedBaselineStyle.cloneStyleFrom(baselineStyle);
                    wrappedBaselineStyle.setWrapText(true);
                    createCell(row, baselineCol, cellValue, wrappedBaselineStyle);
                }
                
                // For other namespaces in comparison - show result + namespace
                for (int i = 1; i < data.namespaceModels.size(); i++) {
                    FlatNamespaceModel nsModel = data.namespaceModels.get(i);
                    String nsLabel = nsModel.getClusterName() + "/" + nsModel.getName();
                    Integer nsCol = namespaceColumnMap.get(nsLabel);
                    
                    if (nsCol != null) {
                        String compKey = baseline.getClusterName() + "/" + baseline.getName() + 
                                       "_vs_" + nsModel.getClusterName() + "/" + nsModel.getName();
                        NamespaceComparison comparison = data.comparisons.get(compKey);
                        
                        if (comparison != null) {
                            String status = getOverallStatusWithCount(comparison);
                            String cellValue = status + "\n(" + nsModel.getName() + ")";
                            CellStyle style = status.startsWith("MATCH") ? matchStyle : mismatchStyle;
                            CellStyle wrappedStyle = workbook.createCellStyle();
                            wrappedStyle.cloneStyleFrom(style);
                            wrappedStyle.setWrapText(true);
                            createCell(row, nsCol, cellValue, wrappedStyle);
                        }
                    }
                }
                
                // Mark namespaces not in this comparison as N/A
                CellStyle naStyle = createNAStyle(workbook);
                naStyle.setWrapText(true);
                for (String nsLabel : allNamespaces) {
                    if (!namespacesInComparison.contains(nsLabel)) {
                        Integer nsCol = namespaceColumnMap.get(nsLabel);
                        if (nsCol != null) {
                            createCell(row, nsCol, "N/A\n-", naStyle);
                        }
                    }
                }
            }
        }
        
        // Set row height for wrapped text and auto-size columns
        for (int i = 1; i <= rowNum; i++) {
            Row r = sheet.getRow(i);
            if (r != null) {
                r.setHeightInPoints(30); // Taller rows for 2-line cells
            }
        }
        
        sheet.setColumnWidth(0, 2000);  // STT
        sheet.setColumnWidth(1, 12000); // Comparison Name (wider for namespace info)
        for (int i = 2; i < headerRow.getLastCellNum(); i++) {
            sheet.setColumnWidth(i, 6000); // Wider for result + namespace
        }
    }
    
    /**
     * Generate individual comparison Summary sheet (object-level)
     * Same format as Summary sheet in individual reports
     */
    private void generateIndividualComparisonSheet(
            Workbook workbook,
            String requestName,
            RequestExecutionData data,
            CellStyle headerStyle,
            CellStyle baselineStyle,
            CellStyle matchStyle,
            CellStyle mismatchStyle,
            CellStyle missingStyle,
            CellStyle differentStyle) {
        
        String sheetName = sanitizeSheetName(requestName);
        Sheet sheet = workbook.createSheet(sheetName);
        generateObjectLevelSummary(sheet, data, headerStyle, baselineStyle, 
                matchStyle, mismatchStyle, missingStyle, differentStyle);
    }
    
    /**
     * Generate object-level Summary content (STT | Kind | Object Name | Namespace1 | Namespace2 | ...)
     * Same format as Summary sheet in ExcelReportGenerator
     */
    private void generateObjectLevelSummary(
            Sheet sheet,
            RequestExecutionData data,
            CellStyle headerStyle,
            CellStyle baselineStyle,
            CellStyle matchStyle,
            CellStyle mismatchStyle,
            CellStyle missingStyle,
            CellStyle differentStyle) {
        
        // Collect all unique objects
        Map<String, ObjectInfo> allObjects = new LinkedHashMap<>();
        for (FlatNamespaceModel model : data.namespaceModels) {
            for (Map.Entry<String, com.nfv.validator.model.FlatObjectModel> entry : model.getObjects().entrySet()) {
                String objectKey = entry.getKey();
                if (!allObjects.containsKey(objectKey)) {
                    ObjectInfo info = new ObjectInfo();
                    info.name = entry.getValue().getName();
                    info.kind = entry.getValue().getKind();
                    allObjects.put(objectKey, info);
                }
            }
        }
        
        // Create header
        Row headerRow = sheet.createRow(0);
        createCell(headerRow, 0, "STT", headerStyle);
        createCell(headerRow, 1, "Kind", headerStyle);
        createCell(headerRow, 2, "Object Name", headerStyle);
        
        for (int i = 0; i < data.namespaceModels.size(); i++) {
            FlatNamespaceModel model = data.namespaceModels.get(i);
            String label = model.getClusterName() + "/" + model.getName();
            createCell(headerRow, 3 + i, label, headerStyle);
        }
        
        // Data rows
        int rowNum = 1;
        FlatNamespaceModel baseline = data.namespaceModels.get(0);
        
        for (Map.Entry<String, ObjectInfo> entry : allObjects.entrySet()) {
            String objectKey = entry.getKey();
            ObjectInfo objInfo = entry.getValue();
            
            Row row = sheet.createRow(rowNum++);
            createCell(row, 0, String.valueOf(rowNum - 1), null);
            createCell(row, 1, objInfo.kind, null);
            createCell(row, 2, objInfo.name, null);
            
            // For each namespace
            for (int i = 0; i < data.namespaceModels.size(); i++) {
                FlatNamespaceModel model = data.namespaceModels.get(i);
                
                if (i == 0) {
                    // Baseline
                    createCell(row, 3 + i, "BASELINE", baselineStyle);
                } else {
                    // Compare with baseline
                    String compKey = baseline.getClusterName() + "/" + baseline.getName() +
                                   "_vs_" + model.getClusterName() + "/" + model.getName();
                    NamespaceComparison comparison = data.comparisons.get(compKey);
                    
                    if (comparison != null) {
                        ObjectComparison objComp = comparison.getObjectComparisons().get(objectKey);
                        String status = getObjectStatus(objComp, model.getObjects().containsKey(objectKey));
                        CellStyle style = getStatusStyle(status, matchStyle, mismatchStyle, 
                                missingStyle, differentStyle);
                        createCell(row, 3 + i, status, style);
                    } else {
                        // No comparison available - show N/A
                        createCell(row, 3 + i, "N/A", null);
                    }
                }
            }
        }
        
        // Auto-size columns
        sheet.setColumnWidth(0, 2000);
        sheet.setColumnWidth(1, 5000);
        sheet.setColumnWidth(2, 10000);
        for (int i = 0; i < data.namespaceModels.size(); i++) {
            sheet.setColumnWidth(3 + i, 6000);
        }
    }
    
    // Helper methods
    
    /**
     * Get overall status for Detail Summary sheet
     * Returns "MATCH" or "MISMATCH (count)" where count = DIFFERENT + MISSING objects
     */
    private String getOverallStatusWithCount(NamespaceComparison comparison) {
        NamespaceComparison.ComparisonSummary summary = comparison.getSummary();
        
        // Count total mismatches (DIFFERENT + MISSING)
        int mismatchCount = 0;
        
        // Count MISSING objects (only in left or only in right)
        mismatchCount += summary.getOnlyInLeft();
        mismatchCount += summary.getOnlyInRight();
        
        // Count DIFFERENT objects (objects with differences)
        mismatchCount += summary.getDifferencesCount();
        
        if (mismatchCount > 0) {
            return "MISMATCH (" + mismatchCount + ")";
        }
        
        return "MATCH";
    }
    
    private String getObjectStatus(ObjectComparison objComp, boolean existsInNamespace) {
        if (objComp == null) {
            return existsInNamespace ? "EXISTS" : "MISSING";
        }
        
        if (objComp.getDifferenceCount() == 0) {
            return "MATCH";
        }
        
        // Check if object exists only on one side
        boolean onlyInLeft = objComp.getItems().stream()
                .anyMatch(item -> item.getStatus() == ComparisonStatus.ONLY_IN_LEFT);
        boolean onlyInRight = objComp.getItems().stream()
                .anyMatch(item -> item.getStatus() == ComparisonStatus.ONLY_IN_RIGHT);
        
        if (onlyInLeft || onlyInRight) {
            return "MISSING";
        }
        
        return "DIFFERENT (" + objComp.getDifferenceCount() + ")";
    }
    
    private CellStyle getStatusStyle(String status, CellStyle matchStyle, CellStyle mismatchStyle,
                                     CellStyle missingStyle, CellStyle differentStyle) {
        switch (status) {
            case "MATCH":
                return matchStyle;
            case "MISMATCH":
                return mismatchStyle;
            case "MISSING":
                return missingStyle;
            case "DIFFERENT":
                return differentStyle;
            default:
                return differentStyle;
        }
    }
    
    private String sanitizeSheetName(String name) {
        // Excel sheet names must be <= 31 chars and cannot contain: \ / ? * [ ]
        String sanitized = name.replaceAll("[\\\\/:?*\\[\\]]", "-");
        if (sanitized.length() > 31) {
            sanitized = sanitized.substring(0, 31);
        }
        return sanitized;
    }
    
    private void createCell(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
    
    // Cell styles
    
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    private CellStyle createBaselineStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    private CellStyle createMatchStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    private CellStyle createMismatchStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    private CellStyle createMissingStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.RED.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    private CellStyle createDifferentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    
    // Helper class for object info
    private static class ObjectInfo {
        String name;
        String kind;
    }
    
    private CellStyle createNAStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font font = workbook.createFont();
        font.setItalic(true);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        style.setFont(font);
        return style;
    }
    
    /**
     * Data class to hold execution data for a request
     */
    public static class RequestExecutionData {
        public List<FlatNamespaceModel> namespaceModels;
        public Map<String, NamespaceComparison> comparisons;
        public ValidationRequest request;
        
        public RequestExecutionData(List<FlatNamespaceModel> namespaceModels,
                                   Map<String, NamespaceComparison> comparisons,
                                   ValidationRequest request) {
            this.namespaceModels = namespaceModels;
            this.comparisons = comparisons;
            this.request = request;
        }
    }
}