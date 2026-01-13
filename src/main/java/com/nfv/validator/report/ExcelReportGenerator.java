package com.nfv.validator.report;

import com.nfv.validator.config.ValidationConfig;
import com.nfv.validator.model.FlatNamespaceModel;
import com.nfv.validator.model.FlatObjectModel;
import com.nfv.validator.model.comparison.NamespaceComparison;
import com.nfv.validator.model.comparison.ObjectComparison;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Generates Excel reports from namespace comparison results
 */
@Slf4j
public class ExcelReportGenerator {

    private static final String SHEET_SUMMARY = "Summary";
    private static final String SHEET_DETAILS = "Details";
    
    /**
     * Generate Excel report from multiple namespace comparisons
     * 
     * @param namespaceModels List of namespace models being compared
     * @param comparisons Map of comparison results (key: "ns1_vs_ns2")
     * @param outputPath Path to output Excel file
     * @param validationConfig Validation config for field filtering
     */
    public void generateReport(List<FlatNamespaceModel> namespaceModels,
                               Map<String, NamespaceComparison> comparisons,
                               String outputPath,
                               ValidationConfig validationConfig) throws IOException {
        
        log.info("Generating Excel report to: {}", outputPath);
        
        try (Workbook workbook = new XSSFWorkbook()) {
            // Create styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle matchStyle = createMatchStyle(workbook);
            CellStyle differentStyle = createDifferentStyle(workbook);
            CellStyle missingStyle = createMissingStyle(workbook);
            CellStyle baselineStyle = createBaselineStyle(workbook);
            
            // Generate Summary Sheet
            generateSummarySheet(workbook, namespaceModels, comparisons, 
                               headerStyle, matchStyle, differentStyle, missingStyle, baselineStyle);
            
            // Generate Details Sheet
            generateDetailsSheet(workbook, namespaceModels, comparisons,
                               headerStyle, matchStyle, differentStyle, missingStyle, baselineStyle, validationConfig);
            
            // Write to file
            try (FileOutputStream fileOut = new FileOutputStream(outputPath)) {
                workbook.write(fileOut);
                log.info("Excel report generated successfully: {}", outputPath);
            }
        }
    }
    
    /**
     * Generate Summary Sheet
     * Columns: STT | Kind | Object Name | Site1 | Site2 | Site3 | ...
     */
    private void generateSummarySheet(Workbook workbook,
                                     List<FlatNamespaceModel> namespaceModels,
                                     Map<String, NamespaceComparison> comparisons,
                                     CellStyle headerStyle,
                                     CellStyle matchStyle,
                                     CellStyle differentStyle,
                                     CellStyle missingStyle,
                                     CellStyle baselineStyle) {
        
        Sheet sheet = workbook.createSheet(SHEET_SUMMARY);
        
        // Collect all unique objects across all namespaces
        Map<String, ObjectInfo> allObjects = collectAllObjects(namespaceModels);
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        createCell(headerRow, 0, "STT", headerStyle);
        createCell(headerRow, 1, "Kind", headerStyle);
        createCell(headerRow, 2, "Object Name", headerStyle);
        
        // Namespace columns (baseline is first)
        for (int i = 0; i < namespaceModels.size(); i++) {
            FlatNamespaceModel ns = namespaceModels.get(i);
            String label = ns.getClusterName() + "/" + ns.getName();
            createCell(headerRow, 3 + i, label, headerStyle);
        }
        
        // Data rows
        int rowNum = 1;
        FlatNamespaceModel baseline = namespaceModels.get(0);
        
        for (Map.Entry<String, ObjectInfo> entry : allObjects.entrySet()) {
            String objectKey = entry.getKey();
            ObjectInfo objInfo = entry.getValue();
            
            Row row = sheet.createRow(rowNum++);
            createCell(row, 0, String.valueOf(rowNum - 1), null);
            createCell(row, 1, objInfo.kind, null);
            createCell(row, 2, objInfo.name, null);
            
            // For each namespace, determine comparison status
            for (int i = 0; i < namespaceModels.size(); i++) {
                FlatNamespaceModel ns = namespaceModels.get(i);
                
                if (i == 0) {
                    // Baseline - always show as "BASELINE"
                    createCell(row, 3 + i, "BASELINE", baselineStyle);
                } else {
                    // Compare with baseline
                    String compKey = getComparisonKey(baseline, ns);
                    NamespaceComparison comp = comparisons.get(compKey);
                    
                    if (comp != null) {
                        ObjectComparison objComp = comp.getObjectComparisons().get(objectKey);
                        String status = getObjectStatus(objComp, ns.getObjects().containsKey(objectKey));
                        CellStyle style = getStatusStyle(status, matchStyle, differentStyle, missingStyle);
                        createCell(row, 3 + i, status, style);
                    } else {
                        createCell(row, 3 + i, "N/A", null);
                    }
                }
            }
        }
        
        // Set column widths manually (to avoid AWT dependency in headless mode)
        sheet.setColumnWidth(0, 2000);  // STT
        sheet.setColumnWidth(1, 5000);  // Kind
        sheet.setColumnWidth(2, 10000); // Object Name
        // Namespace columns
        for (int i = 0; i < namespaceModels.size(); i++) {
            sheet.setColumnWidth(3 + i, 6000);
        }
    }
    
    /**
     * Generate Details Sheet
     * Columns: STT | Kind | Object Name | Field Key | Site1 Value | Site2 Value | ...
     */
    private void generateDetailsSheet(Workbook workbook,
                                     List<FlatNamespaceModel> namespaceModels,
                                     Map<String, NamespaceComparison> comparisons,
                                     CellStyle headerStyle,
                                     CellStyle matchStyle,
                                     CellStyle differentStyle,
                                     CellStyle missingStyle,
                                     CellStyle baselineStyle,
                                     ValidationConfig validationConfig) {
        
        Sheet sheet = workbook.createSheet(SHEET_DETAILS);
        
        // Create header row
        Row headerRow = sheet.createRow(0);
        createCell(headerRow, 0, "STT", headerStyle);
        createCell(headerRow, 1, "Kind", headerStyle);
        createCell(headerRow, 2, "Object Name", headerStyle);
        createCell(headerRow, 3, "Field Key", headerStyle);
        
        for (int i = 0; i < namespaceModels.size(); i++) {
            FlatNamespaceModel ns = namespaceModels.get(i);
            String label = ns.getClusterName() + "/" + ns.getName();
            createCell(headerRow, 4 + i, label, headerStyle);
        }
        
        // Collect all field differences
        int rowNum = 1;
        Map<String, ObjectInfo> allObjects = collectAllObjects(namespaceModels);
        
        for (Map.Entry<String, ObjectInfo> entry : allObjects.entrySet()) {
            String objectKey = entry.getKey();
            ObjectInfo objInfo = entry.getValue();
            
            // Collect all field values across namespaces
            Map<String, List<String>> fieldValues = new LinkedHashMap<>();
            
            for (FlatNamespaceModel ns : namespaceModels) {
                FlatObjectModel obj = ns.getObjects().get(objectKey);
                if (obj != null) {
                    Map<String, String> fields = obj.getAllFieldsFiltered(validationConfig);
                    for (Map.Entry<String, String> field : fields.entrySet()) {
                        fieldValues.computeIfAbsent(field.getKey(), k -> new ArrayList<>());
                    }
                }
            }
            
            // For each field, collect values from all namespaces
            for (String fieldKey : fieldValues.keySet()) {
                List<String> values = new ArrayList<>();
                boolean hasDifference = false;
                String firstValue = null;
                
                for (FlatNamespaceModel ns : namespaceModels) {
                    FlatObjectModel obj = ns.getObjects().get(objectKey);
                    String value = obj != null ? obj.getAllFieldsFiltered(validationConfig).get(fieldKey) : null;
                    values.add(value != null ? value : "");
                    
                    if (firstValue == null && value != null) {
                        firstValue = value;
                    } else if (firstValue != null && !firstValue.equals(value)) {
                        hasDifference = true;
                    }
                }
                
                // Only include fields with differences
                if (hasDifference) {
                    Row row = sheet.createRow(rowNum++);
                    createCell(row, 0, String.valueOf(rowNum - 1), null);
                    createCell(row, 1, objInfo.kind, null);
                    createCell(row, 2, objInfo.name, null);
                    createCell(row, 3, fieldKey, null);
                    
                    // Add values for each namespace
                    String baselineValue = values.get(0);
                    for (int i = 0; i < values.size(); i++) {
                        String value = values.get(i);
                        CellStyle style = null;
                        
                        if (value.isEmpty()) {
                            style = missingStyle;
                        } else if (value.equals(baselineValue)) {
                            style = matchStyle;
                        } else {
                            style = differentStyle;
                        }
                        
                        createCell(row, 4 + i, value, style);
                    }
                }
            }
        }
        
        // Set column widths manually (to avoid AWT dependency in headless mode)
        sheet.setColumnWidth(0, 2000);  // STT
        sheet.setColumnWidth(1, 5000);  // Kind
        sheet.setColumnWidth(2, 10000); // Object Name
        sheet.setColumnWidth(3, 12000); // Field Key
        // Namespace value columns
        for (int i = 0; i < namespaceModels.size(); i++) {
            sheet.setColumnWidth(4 + i, 8000);
        }
    }
    
    /**
     * Collect all unique objects from all namespaces
     */
    private Map<String, ObjectInfo> collectAllObjects(List<FlatNamespaceModel> namespaceModels) {
        Map<String, ObjectInfo> allObjects = new LinkedHashMap<>();
        
        for (FlatNamespaceModel ns : namespaceModels) {
            for (Map.Entry<String, FlatObjectModel> entry : ns.getObjects().entrySet()) {
                String key = entry.getKey();
                FlatObjectModel obj = entry.getValue();
                
                if (!allObjects.containsKey(key)) {
                    allObjects.put(key, new ObjectInfo(obj.getKind(), obj.getName()));
                }
            }
        }
        
        return allObjects;
    }
    
    /**
     * Get comparison key for two namespaces
     */
    private String getComparisonKey(FlatNamespaceModel ns1, FlatNamespaceModel ns2) {
        return ns1.getClusterName() + "/" + ns1.getName() + 
               "_vs_" + ns2.getClusterName() + "/" + ns2.getName();
    }
    
    /**
     * Get object status string
     */
    private String getObjectStatus(ObjectComparison objComp, boolean existsInNamespace) {
        if (!existsInNamespace) {
            return "MISSING";
        }
        if (objComp == null) {
            return "N/A";
        }
        if (objComp.isFullMatch()) {
            return "MATCH";
        }
        return "DIFFERENT (" + objComp.getDifferenceCount() + ")";
    }
    
    /**
     * Get cell style based on status
     */
    private CellStyle getStatusStyle(String status, CellStyle matchStyle, 
                                    CellStyle differentStyle, CellStyle missingStyle) {
        if (status.equals("MATCH")) {
            return matchStyle;
        } else if (status.startsWith("DIFFERENT")) {
            return differentStyle;
        } else if (status.equals("MISSING")) {
            return missingStyle;
        }
        return null;
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
     * Create header cell style
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create MATCH cell style (green)
     */
    private CellStyle createMatchStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create DIFFERENT cell style (orange)
     */
    private CellStyle createDifferentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create MISSING cell style (red)
     */
    private CellStyle createMissingStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(IndexedColors.CORAL.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Create BASELINE cell style (light blue)
     */
    private CellStyle createBaselineStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }
    
    /**
     * Helper class to store object information
     */
    private static class ObjectInfo {
        String kind;
        String name;
        
        ObjectInfo(String kind, String name) {
            this.kind = kind;
            this.name = name;
        }
    }
}
