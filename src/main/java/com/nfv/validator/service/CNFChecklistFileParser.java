package com.nfv.validator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nfv.validator.model.cnf.CNFChecklistItem;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for parsing CNF Checklist files (JSON and Excel)
 */
@Slf4j
@ApplicationScoped
public class CNFChecklistFileParser {

    @Inject
    ObjectMapper objectMapper;

    /**
     * Parse JSON file to CNFChecklistItem list
     */
    public List<CNFChecklistItem> parseJsonFile(byte[] fileContent) throws IOException {
        log.info("Parsing JSON file, size: {} bytes", fileContent.length);
        
        try {
            CNFChecklistItem[] items = objectMapper.readValue(fileContent, CNFChecklistItem[].class);
            List<CNFChecklistItem> result = new ArrayList<>();
            
            for (CNFChecklistItem item : items) {
                validateItem(item);
                result.add(item);
            }
            
            log.info("Successfully parsed {} items from JSON file", result.size());
            return result;
        } catch (Exception e) {
            log.error("Failed to parse JSON file", e);
            throw new IOException("Invalid JSON format: " + e.getMessage(), e);
        }
    }

    /**
     * Parse Excel file to CNFChecklistItem list
     * Expected format:
     * - Row 1: Headers (VIM Name, Namespace, Kind, Object Name, Field Key, Expected Value)
     * - Row 2+: Data rows
     */
    public List<CNFChecklistItem> parseExcelFile(byte[] fileContent) throws IOException {
        log.info("Parsing Excel file, size: {} bytes", fileContent.length);
        
        List<CNFChecklistItem> items = new ArrayList<>();
        
        try (InputStream is = new ByteArrayInputStream(fileContent);
             Workbook workbook = new XSSFWorkbook(is)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            
            // Validate header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new IOException("Excel file is empty or missing header row");
            }
            
            // Parse data rows (starting from row 1, row 0 is header)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }
                
                try {
                    CNFChecklistItem item = parseExcelRow(row, i);
                    validateItem(item);
                    items.add(item);
                } catch (Exception e) {
                    log.warn("Failed to parse row {}: {}", i + 1, e.getMessage());
                    throw new IOException("Error at row " + (i + 1) + ": " + e.getMessage(), e);
                }
            }
            
            log.info("Successfully parsed {} items from Excel file", items.size());
            return items;
            
        } catch (IOException e) {
            log.error("Failed to parse Excel file", e);
            throw new IOException("Invalid Excel format: " + e.getMessage(), e);
        }
    }

    /**
     * Parse a single Excel row to CNFChecklistItem
     */
    private CNFChecklistItem parseExcelRow(Row row, int rowIndex) {
        CNFChecklistItem item = new CNFChecklistItem();
        
        // Column 0: VIM Name
        item.setVimName(getCellValueAsString(row.getCell(0)));
        
        // Column 1: Namespace
        item.setNamespace(getCellValueAsString(row.getCell(1)));
        
        // Column 2: Kind
        item.setKind(getCellValueAsString(row.getCell(2)));
        
        // Column 3: Object Name
        item.setObjectName(getCellValueAsString(row.getCell(3)));
        
        // Column 4: Field Key
        item.setFieldKey(getCellValueAsString(row.getCell(4)));
        
        // Column 5: Expected Value (MANO Value)
        item.setManoValue(getCellValueAsString(row.getCell(5)));
        
        return item;
    }

    /**
     * Get cell value as string, handling different cell types
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // Handle numeric values (including dates)
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    // Format as string without decimal for whole numbers
                    double numValue = cell.getNumericCellValue();
                    if (numValue == (long) numValue) {
                        return String.valueOf((long) numValue);
                    } else {
                        return String.valueOf(numValue);
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                // Try to get cached formula result
                try {
                    return getCellValueAsString(cell);
                } catch (Exception e) {
                    return cell.getCellFormula();
                }
            case BLANK:
                return "";
            default:
                return "";
        }
    }

    /**
     * Check if a row is empty
     */
    private boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }
        
        for (int i = 0; i < 6; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * Validate CNFChecklistItem
     */
    private void validateItem(CNFChecklistItem item) throws IllegalArgumentException {
        if (item.getVimName() == null || item.getVimName().trim().isEmpty()) {
            throw new IllegalArgumentException("VIM Name is required");
        }
        if (item.getNamespace() == null || item.getNamespace().trim().isEmpty()) {
            throw new IllegalArgumentException("Namespace is required");
        }
        if (item.getKind() == null || item.getKind().trim().isEmpty()) {
            throw new IllegalArgumentException("Kind is required");
        }
        if (item.getObjectName() == null || item.getObjectName().trim().isEmpty()) {
            throw new IllegalArgumentException("Object Name is required");
        }
        if (item.getFieldKey() == null || item.getFieldKey().trim().isEmpty()) {
            throw new IllegalArgumentException("Field Key is required");
        }
        if (item.getManoValue() == null || item.getManoValue().trim().isEmpty()) {
            throw new IllegalArgumentException("Expected Value (MANO Value) is required");
        }
    }

    /**
     * Generate Excel template for download
     */
    public byte[] generateExcelTemplate() throws IOException {
        log.info("Generating Excel template");
        
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("CNF Checklist");
            
            // Create header row
            Row headerRow = sheet.createRow(0);
            CellStyle headerStyle = createHeaderStyle(workbook);
            
            String[] headers = {
                "VIM Name",
                "Namespace",
                "Kind",
                "Object Name",
                "Field Key",
                "Expected Value (MANO)"
            };
            
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 5000); // Set column width
            }
            
            // Add sample data rows
            addSampleRow(sheet, 1, "vim-hanoi", "default", "Deployment", "abm_01", 
                        "spec.template.spec.containers[0].image", "harbor.local/vmano/webmano:1.2.3");
            addSampleRow(sheet, 2, "vim-hanoi", "default", "Deployment", "abm_01",
                        "spec.template.spec.containers[1].terminationMessagePath", "/dev/termination-log");
            addSampleRow(sheet, 3, "vim-hanoi", "default", "ConfigMap", "abm-config",
                        "data.ACTUAL_VERSION", "v1.0.0");
            addSampleRow(sheet, 4, "vim-hcm", "production", "Deployment", "web-app",
                        "spec.replicas", "3");
            
            // Write to byte array
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            workbook.write(outputStream);
            
            log.info("Excel template generated successfully");
            return outputStream.toByteArray();
            
        } catch (Exception e) {
            log.error("Failed to generate Excel template", e);
            throw new IOException("Failed to generate Excel template: " + e.getMessage(), e);
        }
    }

    /**
     * Create header cell style
     */
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

    /**
     * Add a sample data row
     */
    private void addSampleRow(Sheet sheet, int rowNum, String vimName, String namespace,
                             String kind, String objectName, String fieldKey, String manoValue) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(vimName);
        row.createCell(1).setCellValue(namespace);
        row.createCell(2).setCellValue(kind);
        row.createCell(3).setCellValue(objectName);
        row.createCell(4).setCellValue(fieldKey);
        row.createCell(5).setCellValue(manoValue);
    }
}
