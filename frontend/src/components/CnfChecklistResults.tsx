import React, { useEffect, useState, useRef } from 'react';
import { useParams } from 'react-router-dom';
import { Button, Card, Col, Row, Statistic, Space, Alert, Tag, Spin, Typography, Table, Modal } from 'antd';
import { DownloadOutlined, LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined, BarChartOutlined } from '@ant-design/icons';
import * as XLSX from 'xlsx';
import { CnfValidationResultJson, ValidationJobResponse } from '../types';
import { validationApi } from '../services/api';
import { CnfValidationResults as CnfValidationResultsDisplay } from './CnfValidationResults';

interface CnfChecklistResultsProps {
  jobId?: string;
}

interface JobStatusWithResult {
  status: ValidationJobResponse;
  result: CnfValidationResultJson | null;
}

export const CnfChecklistResults: React.FC<CnfChecklistResultsProps> = ({ jobId: propJobId }) => {
  const { jobId: paramJobId } = useParams<{ jobId: string }>();
  const jobId = propJobId || paramJobId!;

  const [batchJob, setBatchJob] = useState<ValidationJobResponse | null>(null);
  const [individualJobs, setIndividualJobs] = useState<Map<string, JobStatusWithResult>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<string>('overview');
  const resultsRef = useRef<HTMLDivElement>(null);
  const [pollingTimeoutModal, setPollingTimeoutModal] = useState(false);
  const pollingStartTimeRef = useRef<number>(0);

  const fetchResults = async () => {
    try {
      const batchStatus = await validationApi.getJobStatus(jobId);
      setBatchJob(batchStatus);

      if (batchStatus.status === 'COMPLETED') {
        const jobsMap = new Map<string, JobStatusWithResult>();
        
        try {
          // Get individual jobs for this batch (each namespace is a separate job)
          const batchInfo = await validationApi.getBatchIndividualJobs(jobId);
          console.log('Individual jobs for batch:', batchInfo);
          
          // Fetch results for each individual job
          for (const individualJobId of batchInfo.individualJobs) {
            try {
              const result = await validationApi.getCnfValidationResults(individualJobId);
              const individualStatus = await validationApi.getJobStatus(individualJobId);
              jobsMap.set(individualJobId, { status: individualStatus, result });
              console.log('Got CNF results for job:', individualJobId);
            } catch (err) {
              console.error(`Failed to fetch result for individual job ${individualJobId}:`, err);
              const individualStatus = await validationApi.getJobStatus(individualJobId);
              jobsMap.set(individualJobId, { status: individualStatus, result: null });
            }
          }
        } catch (err) {
          console.warn('Failed to get individual jobs, treating as single job:', err);
          // Fallback: treat as single job
          try {
            const result = await validationApi.getCnfValidationResults(jobId);
            jobsMap.set(jobId, { status: batchStatus, result });
          } catch (fallbackErr) {
            console.error(`Failed to fetch result for single job ${jobId}:`, fallbackErr);
            jobsMap.set(jobId, { status: batchStatus, result: null });
          }
        }
        
        setIndividualJobs(jobsMap);
      } else {
        const jobsMap = new Map();
        jobsMap.set(jobId, { status: batchStatus, result: null });
        setIndividualJobs(jobsMap);
      }
    } catch (err) {
      console.error('Error fetching job results:', err);
      setError(err instanceof Error ? err.message : 'Unknown error occurred');
    }
  };

  useEffect(() => {
    let mounted = true;
    // @ts-ignore
    let pollInterval: NodeJS.Timeout | null = null;
    
    const loadResults = async () => {
      setLoading(true);
      pollingStartTimeRef.current = Date.now();
      
      try {
        const batchStatus = await validationApi.getJobStatus(jobId);
        setBatchJob(batchStatus);

        if (batchStatus.status === 'COMPLETED' || batchStatus.status === 'FAILED') {
          // Job completed - fetch all results
          await fetchResults();
          if (mounted) setLoading(false);
        } else {
          // Job still running - start polling
          pollInterval = setInterval(async () => {
            if (!mounted) {
              if (pollInterval) clearInterval(pollInterval);
              return;
            }
            
            // Check timeout
            const elapsed = Date.now() - pollingStartTimeRef.current;
            console.log('CNF Checklist polling elapsed time:', elapsed, 'ms');
            if (elapsed > 1800000) { // 1800 seconds = 30 minutes
              console.log('Timeout reached! Showing modal...');
              if (pollInterval) clearInterval(pollInterval);
              setPollingTimeoutModal(true);
              setLoading(false);
              return;
            }
            
            try {
              const updatedStatus = await validationApi.getJobStatus(jobId);
              setBatchJob(updatedStatus);
              
              // Check if completed
              if (updatedStatus.status === 'COMPLETED' || updatedStatus.status === 'FAILED') {
                if (pollInterval) clearInterval(pollInterval);
                await fetchResults();
                if (mounted) setLoading(false);
              }
            } catch (err) {
              console.error('Error polling job status:', err);
            }
          }, 5000); // Poll every 5 seconds
        }
      } catch (err) {
        console.error('Error loading results:', err);
        setError(err instanceof Error ? err.message : 'Unknown error occurred');
        if (mounted) setLoading(false);
      }
    };

    loadResults();
    
    return () => { 
      mounted = false;
      if (pollInterval) clearInterval(pollInterval);
    };
  }, [jobId]); // Remove continuePolling from dependencies

  const exportToExcel = () => {
    const workbook = XLSX.utils.book_new();
    
    // Calculate overall statistics
    let totalFields = 0;
    let totalMatches = 0;
    let totalIgnored = 0;
    let totalDifferences = 0;
    let totalMissing = 0;
    let totalObjects = 0;
    let totalMatchedObjects = 0;
    
    // Collect all validation results for summary and details
    const summaryResults: any[] = [];
    const allMismatchItems: any[] = [];
    const allMatchItems: any[] = [];
    
    individualJobs.forEach((jobData, jobIdKey) => {
      const result = jobData.result;
      if (result) {
        const fieldCount = result.summary.totalFields;
        const matchCount = result.summary.totalMatches;
        const diffCount = result.summary.totalDifferences;
        const ignoredCount = result.summary.totalIgnored || 0;
        totalFields += fieldCount;
        totalMatches += matchCount;
        totalDifferences += diffCount;
        totalIgnored += ignoredCount;
        
        // Calculate object-level statistics
        // An object is considered "matched" only if ALL its fields match or are ignored
        // Formula: Object OK when (MATCH + IGNORED) = total fields
        const objectsSet = new Set<string>();
        const objectFieldCounts = new Map<string, { total: number; matched: number; ignored: number }>();
        
        result.results.forEach((nsResult: any) => {
          const nsLabel = `${nsResult.vimName}/${nsResult.namespace}`;
          
          nsResult.items.forEach((item: any) => {
            const objectKey = `${item.kind}/${item.objectName}`;
            objectsSet.add(objectKey);
            
            // Track field counts per object
            if (!objectFieldCounts.has(objectKey)) {
              objectFieldCounts.set(objectKey, { total: 0, matched: 0, ignored: 0 });
            }
            const counts = objectFieldCounts.get(objectKey)!;
            counts.total++;
            
            if (item.status === 'MATCH') {
              counts.matched++;
            } else if (item.status === 'IGNORED') {
              counts.ignored++;
            }
            
            // Count missing items
            if (item.status === 'MISSING_IN_RUNTIME') {
              totalMissing++;
            }
            
            // Add to mismatch items (only non-MATCH and non-IGNORED)
            if (item.status !== 'MATCH' && item.status !== 'IGNORED') {
              allMismatchItems.push({
                namespace: nsLabel,
                kind: item.kind || '',
                objectName: item.objectName,
                fieldKey: item.fieldKey,
                expected: item.baselineValue || '',
                actual: item.actualValue || '',
                status: item.status,
                message: item.message || ''
              });
            }
            
            // Add to match items (MATCH and IGNORED)
            if (item.status === 'MATCH' || item.status === 'IGNORED') {
              allMatchItems.push({
                namespace: nsLabel,
                kind: item.kind || '',
                objectName: item.objectName,
                fieldKey: item.fieldKey,
                expected: item.baselineValue || '',
                actual: item.actualValue || '',
                status: item.status,
                message: item.status === 'IGNORED' ? 'Field ignored by configuration' : 'Values match'
              });
            }
          });
        });
        
        // Count objects where (MATCH + IGNORED) = total fields
        const nsObjects = objectsSet.size;
        const nsMatchedObjects = Array.from(objectFieldCounts.values())
          .filter(counts => (counts.matched + counts.ignored) === counts.total && counts.total > 0)
          .length;
        const objectMatchRate = nsObjects > 0 ? parseFloat(((nsMatchedObjects / nsObjects) * 100).toFixed(1)) : 0;
        // Field Match Rate: MATCH / (total - ignored)
        const fieldMatchRate = (fieldCount - ignoredCount) > 0 ? parseFloat(((matchCount / (fieldCount - ignoredCount)) * 100).toFixed(1)) : 0;
        
        totalObjects += nsObjects;
        totalMatchedObjects += nsMatchedObjects;
        
        const namespaceName = result.results.length > 0 
          ? `${result.results[0].vimName}/${result.results[0].namespace}`
          : jobData.status.validationName || jobIdKey;
        
        const status = fieldMatchRate === 100 ? 'PASS' : 'FAIL';
        const resultText = `${matchCount}/${fieldCount} fields matched`;
        
        summaryResults.push({
          name: namespaceName,
          status: status,
          result: resultText,
          objects: nsObjects,
          objMatches: nsMatchedObjects,
          objMatchRate: objectMatchRate,
          fields: fieldCount,
          fieldMatches: matchCount,
          ignored: ignoredCount,
          fieldMatchRate: fieldMatchRate
        });
      }
    });
    
    const passRate = totalFields > 0 ? parseFloat(((totalMatches / (totalFields - totalIgnored)) * 100).toFixed(1)) : 0;
    const objectMatchRate = totalObjects > 0 ? parseFloat(((totalMatchedObjects / totalObjects) * 100).toFixed(1)) : 0;
    
    // Sheet 1: Summary - Per-namespace validation results
    const summaryData = [
      ['CNF CHECKLIST VALIDATION SUMMARY'],
      [],
      ['OVERALL STATISTICS'],
      ['Total Namespaces:', individualJobs.size],
      ['Total Objects:', totalObjects],
      ['Total Object Matches:', totalMatchedObjects],
      ['Overall Object Match Rate:', `${objectMatchRate}%`],
      ['Total Fields Validated:', totalFields],
      ['Total Field Matches:', totalMatches],
      ['Total Ignored Fields:', totalIgnored],
      ['Overall Field Match Rate:', `${passRate}%`],
      [],
      [],
      ['VALIDATION RESULTS'],
      ['Validation Name', 'Status', 'Result', 'Objects', 'Obj Matches', 'Obj Match Rate (%)', 'Fields', 'Field Matches', 'Ignored', 'Field Match Rate (%)']
    ];
    
    // Add data rows
    summaryResults.forEach((row) => {
      summaryData.push([
        row.name,
        row.status,
        row.result,
        row.objects,
        row.objMatches,
        row.objMatchRate,
        row.fields,
        row.fieldMatches,
        row.ignored || 0,
        row.fieldMatchRate
      ]);
    });
    
    const summarySheet = XLSX.utils.aoa_to_sheet(summaryData);
    
    // Add background color to header row
    const headerRow = 14;
    const headerCells = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J'];
    headerCells.forEach(col => {
      const cellRef = `${col}${headerRow + 1}`;
      if (!summarySheet[cellRef]) summarySheet[cellRef] = {};
      summarySheet[cellRef].s = {
        fill: { fgColor: { rgb: "4472C4" } },
        font: { bold: true, color: { rgb: "FFFFFF" } },
        alignment: { horizontal: 'center', vertical: 'center' }
      };
    });
    
    // Add auto-filter to the table (header is at row 14, 0-indexed because we added one more row)
    const tableStartRow = 14;
    const tableEndRow = tableStartRow + summaryResults.length;
    summarySheet['!autofilter'] = { 
      ref: `A${tableStartRow + 1}:J${tableEndRow + 1}` 
    };
    
    // Set column widths for Summary sheet
    summarySheet['!cols'] = [
      { wch: 30 }, // Validation Name
      { wch: 10 }, // Status
      { wch: 25 }, // Result
      { wch: 10 }, // Objects
      { wch: 12 }, // Obj Matches
      { wch: 18 }, // Obj Match Rate (%)
      { wch: 10 }, // Fields
      { wch: 14 }, // Field Matches
      { wch: 10 }, // Ignored
      { wch: 20 }  // Field Match Rate (%)
    ];
    
    XLSX.utils.book_append_sheet(workbook, summarySheet, 'Summary');
    
    // Sheet 2: Details - Mismatch (failed items only)
    if (allMismatchItems.length > 0) {
      const mismatchData = [
        ['VALIDATION DETAILS - MISMATCHES ONLY'],
        [],
        ['VIM/Namespace', 'Kind', 'Object Name', 'Field Key', 'Expected Value', 'Actual Value', 'Status', 'Message']
      ];
      
      // Add all mismatch items
      allMismatchItems.forEach((item) => {
        mismatchData.push([
          item.namespace,
          item.kind,
          item.objectName,
          item.fieldKey,
          item.expected,
          item.actual,
          item.status,
          item.message
        ]);
      });
      
      const mismatchSheet = XLSX.utils.aoa_to_sheet(mismatchData);
      
      // Add background color to header row
      const mismatchHeaderRow = 2;
      const mismatchHeaderCells = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
      mismatchHeaderCells.forEach(col => {
        const cellRef = `${col}${mismatchHeaderRow + 1}`;
        if (!mismatchSheet[cellRef]) mismatchSheet[cellRef] = {};
        mismatchSheet[cellRef].s = {
          fill: { fgColor: { rgb: "C65911" } },
          font: { bold: true, color: { rgb: "FFFFFF" } },
          alignment: { horizontal: 'center', vertical: 'center' }
        };
      });
      
      // Add auto-filter to the table (header is at row 2, 0-indexed)
      const mismatchTableStartRow = 2;
      const mismatchTableEndRow = mismatchTableStartRow + allMismatchItems.length;
      mismatchSheet['!autofilter'] = { 
        ref: `A${mismatchTableStartRow + 1}:H${mismatchTableEndRow + 1}` 
      };
      
      // Set column widths for Mismatch sheet
      mismatchSheet['!cols'] = [
        { wch: 25 }, // VIM/Namespace
        { wch: 20 }, // Kind
        { wch: 30 }, // Object Name
        { wch: 40 }, // Field Key
        { wch: 30 }, // Expected Value
        { wch: 30 }, // Actual Value
        { wch: 20 }, // Status
        { wch: 50 }  // Message
      ];
      
      XLSX.utils.book_append_sheet(workbook, mismatchSheet, 'Details - Mismatch');
    }
    
    // Sheet 3: Details - Match (matched and ignored items)
    if (allMatchItems.length > 0) {
      const matchData = [
        ['VALIDATION DETAILS - MATCHES ONLY'],
        [],
        ['VIM/Namespace', 'Kind', 'Object Name', 'Field Key', 'Expected Value', 'Actual Value', 'Status', 'Message']
      ];
      
      // Add all match items
      allMatchItems.forEach((item) => {
        matchData.push([
          item.namespace,
          item.kind,
          item.objectName,
          item.fieldKey,
          item.expected,
          item.actual,
          item.status,
          item.message
        ]);
      });
      
      const matchSheet = XLSX.utils.aoa_to_sheet(matchData);
      
      // Add background color to header row
      const matchHeaderRow = 2;
      const matchHeaderCells = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'];
      matchHeaderCells.forEach(col => {
        const cellRef = `${col}${matchHeaderRow + 1}`;
        if (!matchSheet[cellRef]) matchSheet[cellRef] = {};
        matchSheet[cellRef].s = {
          fill: { fgColor: { rgb: "70AD47" } },
          font: { bold: true, color: { rgb: "FFFFFF" } },
          alignment: { horizontal: 'center', vertical: 'center' }
        };
      });
      
      // Add auto-filter to the table
      const matchTableStartRow = 2;
      const matchTableEndRow = matchTableStartRow + allMatchItems.length;
      matchSheet['!autofilter'] = { 
        ref: `A${matchTableStartRow + 1}:H${matchTableEndRow + 1}` 
      };
      
      // Set column widths for Match sheet
      matchSheet['!cols'] = [
        { wch: 25 }, // VIM/Namespace
        { wch: 20 }, // Kind
        { wch: 30 }, // Object Name
        { wch: 40 }, // Field Key
        { wch: 30 }, // Expected Value
        { wch: 30 }, // Actual Value
        { wch: 15 }, // Status
        { wch: 50 }  // Message
      ];
      
      XLSX.utils.book_append_sheet(workbook, matchSheet, 'Details - Match');
    }

    XLSX.writeFile(workbook, `cnf-checklist-results-${jobId}.xlsx`, { cellStyles: true, bookSST: true });
  };

  const handleExportHTML = () => {
    const htmlContent = generateHTMLReport();
    const blob = new Blob([htmlContent], { type: 'text/html' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `cnf-checklist-report-${jobId}.html`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
  };

  const generateHTMLReport = (): string => {
    const timestamp = new Date().toLocaleString();
    
    let totalFields = 0;
    let totalMatches = 0;
    let totalIgnored = 0;
    let totalDifferences = 0;
    let completedJobs = 0;

    // Build navigation tree
    let navTree = '<div class="nav-section"><a href="#cnf-overview" class="nav-item nav-level-1"><span class="nav-icon">📊</span><span class="nav-text">CNF Overview</span></a></div>';
    
    // Build CNF validation sections
    const validationSections = Array.from(individualJobs.entries()).map(([jobIdKey, jobData], index) => {
      const result = jobData.result;
      if (!result) return '';
      
      const fieldCount = result.summary.totalFields;
      const matchCount = result.summary.totalMatches;
      const diffCount = result.summary.totalDifferences;
      const ignoredCount = result.summary.totalIgnored || 0;
      const isOk = diffCount === 0;
      const okRate = fieldCount > 0 ? ((matchCount / fieldCount) * 100) : 0;
      
      if (jobData.status.status === 'COMPLETED') completedJobs++;
      totalFields += fieldCount;
      totalMatches += matchCount;
      totalDifferences += diffCount;
      totalIgnored += (result.summary.totalIgnored || 0);
      // Get namespace name
      const namespaceName = result.results.length > 0 
        ? `${result.results[0].vimName}/${result.results[0].namespace}`
        : jobData.status.validationName || jobIdKey;

      // Build navigation
      let validationNav = `
        <div class="nav-section">
          <a href="#cnf-validation-${index}" class="nav-item nav-level-1 ${isOk ? 'nav-ok' : 'nav-nok'}">
            <span class="nav-icon">${isOk ? '✓' : '✗'}</span>
            <span class="nav-text">${namespaceName}</span>
          </a>
        </div>
      `;
      navTree += validationNav;

      // Build field details
      let fieldDetailsHTML = '';
      result.results.forEach((nsResult: any) => {
        const fieldRows = nsResult.items.map((item: any, itemIdx: number) => {
          const status = item.status === 'MATCH' ? 'OK' : 'NOK';
          
          return `
            <tr class="${status === 'NOK' ? 'has-diff' : ''}">
              <td style="width: 40px;">${itemIdx + 1}</td>
              <td>${item.kind || 'N/A'}</td>
              <td>${item.objectName}</td>
              <td><code style="font-size: 11px;">${item.fieldKey}</code></td>
              <td><code style="font-size: 11px; background: #e6f7ff; padding: 2px 6px; border-radius: 3px;">${item.baselineValue || ''}</code></td>
              <td><code style="font-size: 11px; background: ${item.status === 'MATCH' ? '#f6ffed' : '#fff1f0'}; padding: 2px 6px; border-radius: 3px;">${item.actualValue || 'N/A'}</code></td>
              <td style="text-align: center;">
                <span class="status-badge ${status === 'OK' ? 'status-ok' : 'status-nok'}">${status}</span>
              </td>
            </tr>
          `;
        }).join('');

        fieldDetailsHTML = `
          <table class="excel-table">
            <thead>
              <tr>
                <th style="width: 40px;">STT</th>
                <th>Kind</th>
                <th>Object</th>
                <th>Field Key</th>
                <th>Expected Value</th>
                <th>Actual Value</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              ${fieldRows}
            </tbody>
          </table>
        `;
      });

      return `
        <div id="cnf-validation-${index}" class="validation-section">
          <h2 class="section-title">
            ${namespaceName}
          </h2>
          <div class="section-content">
            <table class="summary-table excel-table">
              <tr>
                <th>Status</th>
                <th>Result</th>
                <th>Fields</th>
                <th>Matches</th>
                <th>Ignored</th>
                <th>Differences</th>
                <th>Match Rate</th>
              </tr>
              <tr>
                <td><span class="status-badge ${jobData.status.status === 'COMPLETED' ? 'status-ok' : 'status-failed'}">${jobData.status.status}</span></td>
                <td><span class="status-badge ${isOk ? 'status-ok' : 'status-nok'}">${isOk ? 'OK' : 'NOK'}</span></td>
                <td>${fieldCount}</td>
                <td style="color: #52c41a">${matchCount}</td>
                <td style="color: rgb(75, 152, 252)">${ignoredCount}</td>
                <td style="color: ${diffCount > 0 ? '#ff4d4f' : '#52c41a'}">${diffCount}</td>
                <td style="color: ${okRate === 100 ? '#52c41a' : okRate >= 80 ? '#faad14' : '#ff4d4f'}">${okRate.toFixed(2)}%</td>
              </tr>
            </table>
            
            <h3>Field Details</h3>
            ${fieldDetailsHTML}
          </div>
        </div>
      `;
    }).filter(s => s !== '').join('');

    const overallMatchRate = totalFields > 0 ? ((totalMatches / (totalFields - totalIgnored))) * 100 : 0;

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>CNF Checklist Validation Report - ${jobId}</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    html { scroll-behavior: smooth; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
      line-height: 1.6; color: #333; background: #f5f5f5; display: flex; min-height: 100vh;
    }
    .sidebar-nav {
      width: 280px; background: white; padding: 20px; position: sticky; top: 0; height: 100vh;
      overflow-y: auto; border-right: 1px solid #e8e8e8; flex-shrink: 0;
    }
    .sidebar-nav h3 { color: #1890ff; margin-bottom: 16px; font-size: 16px; }
    .nav-item {
      display: flex; align-items: center; padding: 10px 12px; margin-bottom: 8px;
      border-radius: 6px; text-decoration: none; color: #333; transition: all 0.2s;
      border-left: 3px solid transparent;
    }
    .nav-item:hover { background: #f5f5f5; }
    .nav-item.nav-ok { border-left-color: #52c41a; }
    .nav-item.nav-nok { border-left-color: #ff4d4f; }
    .nav-icon {
      width: 20px; height: 20px; border-radius: 50%; display: flex; align-items: center;
      justify-content: center; margin-right: 10px; font-size: 12px; font-weight: bold; flex-shrink: 0;
    }
    .nav-ok .nav-icon { background: #f6ffed; color: #52c41a; }
    .nav-nok .nav-icon { background: #fff2e8; color: #ff4d4f; }
    .nav-text {
      flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px;
    }
    .nav-section { margin-bottom: 4px; }
    .main-content { flex: 1; background: white; padding: 40px; overflow-y: auto; }
    .container { max-width: 1200px; margin: 0 auto; }
    h1 { color: #1890ff; margin-bottom: 10px; font-size: 32px; }
    .meta { color: #666; margin-bottom: 30px; font-size: 14px; }
    .overview { background: #fafafa; padding: 24px; border-radius: 8px; margin-bottom: 30px; }
    .stats-grid {
      display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 16px; margin: 20px 0;
    }
    .stat-card { background: white; padding: 16px; border-radius: 6px; border: 1px solid #e8e8e8; }
    .stat-label { font-size: 12px; color: #666; text-transform: uppercase; margin-bottom: 8px; }
    .stat-value { font-size: 24px; font-weight: 600; color: #1890ff; }
    .validation-section {
      margin-bottom: 60px; padding: 32px; background: #fafafa; border-radius: 8px;
      border-left: 4px solid #1890ff; scroll-margin-top: 20px;
    }
    .validation-section h2 { color: #1890ff; margin-bottom: 24px; font-size: 24px; }
    .validation-section h3 { color: #333; margin: 24px 0 16px 0; font-size: 18px; }
    .status-badge {
      display: inline-block; padding: 4px 12px; border-radius: 4px; font-size: 12px;
      font-weight: 600; text-transform: uppercase;
    }
    .status-ok { background: #f6ffed; color: #52c41a; border: 1px solid #b7eb8f; }
    .status-nok { background: #fff2e8; color: #fa8c16; border: 1px solid #ffd591; }
    .status-failed { background: #fff1f0; color: #ff4d4f; border: 1px solid #ffa39e; }
    .excel-table {
      width: 100%; border-collapse: collapse; background: white; font-size: 13px; border: 1px solid #d9d9d9;
    }
    .excel-table th, .excel-table td {
      padding: 8px 12px; border: 1px solid #d9d9d9; text-align: left;
    }
    .excel-table th {
      background: #f0f0f0; font-weight: 600; color: #262626; text-align: center;
    }
    .excel-table tr:nth-child(even) { background: #fafafa; }
    .excel-table tr:hover { background: #e6f7ff; }
    .summary-table { margin-bottom: 24px; width: auto; max-width: 800px; }
    .summary-table th { text-align: left; width: 16.66%; }
    .section-title { display: flex; align-items: center; gap: 8px; }
    .section-content { margin-top: 16px; }
    .has-diff:hover { background: #fffbe6 !important; }
    .footer {
      margin-top: 40px; padding-top: 20px; border-top: 2px solid #e8e8e8;
      text-align: center; color: #999; font-size: 12px;
    }
  </style>
</head>
<body>
  <aside class="sidebar-nav">
    <h3>Navigation</h3>
    ${navTree}
  </aside>
  <main class="main-content">
    <div class="container">
    <h1 id="cnf-overview">CNF Checklist Validation Report</h1>
    <div class="meta">
      <div><strong>Job ID:</strong> ${jobId}</div>
      <div><strong>Generated:</strong> ${timestamp}</div>
      <div><strong>Status:</strong> <span class="status-badge ${batchJob?.status === 'COMPLETED' ? 'status-ok' : 'status-failed'}">${batchJob?.status}</span></div>
    </div>

    <div class="overview">
      <h2>CNF Checklist Overview</h2>
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-label">Total Namespaces</div>
          <div class="stat-value">${individualJobs.size}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Completed</div>
          <div class="stat-value" style="color: #52c41a">${completedJobs}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Total Fields</div>
          <div class="stat-value">${totalFields}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Matches</div>
          <div class="stat-value" style="color: #52c41a">${totalMatches}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Ignored</div>
          <div class="stat-value" style="color: rgb(75, 152, 252)">${totalIgnored}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Differences</div>
          <div class="stat-value" style="color: ${totalDifferences > 0 ? '#ff4d4f' : '#52c41a'}">${totalDifferences}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Overall Match Rate</div>
          <div class="stat-value" style="color: ${overallMatchRate === 100 ? '#52c41a' : overallMatchRate >= 80 ? '#faad14' : '#ff4d4f'}">${overallMatchRate.toFixed(2)}%</div>
        </div>
      </div>
    </div>

    <h2 style="margin: 40px 0 20px 0;">Validation Results by Namespace</h2>
    ${validationSections}

    <div class="footer">
      <p>KValidator - CNF Checklist Validation Tool</p>
      <p>Report generated on ${timestamp}</p>
    </div>
    </div>
  </main>
</body>
</html>`;
  };

  const getStatusIcon = (status: string, hasErrors: boolean) => {
    if (status === 'COMPLETED' && !hasErrors) {
      return <CheckCircleOutlined style={{ color: 'green', fontSize: 14 }} />;
    } else if (status === 'COMPLETED' && hasErrors) {
      return <CloseCircleOutlined style={{ color: 'red', fontSize: 14 }} />;
    } else if (status === 'PROCESSING') {
      return <Spin size="small" />;
    }
    return null;
  };

  const renderResults = () => {
    if (activeTab === 'overview') {
      // Calculate statistics
      let totalFields = 0;
      let totalMatches = 0;
      let totalIgnored = 0;
      let totalDifferences = 0;
      let completedJobs = 0;
      let totalObjects = 0;
      let totalMatchedObjects = 0;

      const summaryData: any[] = [];

      individualJobs.forEach((jobData, jobIdKey) => {
        const result = jobData.result;
        if (!result) return;

        const fieldCount = result.summary.totalFields;
        const matchCount = result.summary.totalMatches;
        const ignoredCount = result.summary.totalIgnored || 0;
        const diffCount = result.summary.totalDifferences;
        
        // Count ignored from items if summary doesn't have it
        let actualIgnoredCount = ignoredCount;
        if (ignoredCount === 0) {
          actualIgnoredCount = 0;
          result.results.forEach((nsResult: any) => {
            nsResult.items.forEach((item: any) => {
              if (item.status === 'IGNORED') {
                actualIgnoredCount++;
              }
            });
          });
        }
        const matchRate = fieldCount > 0 ? ((matchCount / (fieldCount - actualIgnoredCount)) * 100).toFixed(1) : '0';
        
        // Calculate object-level statistics
        // An object is considered "matched" only if ALL its fields match or are ignored
        // Formula: Object OK when (MATCH + IGNORED) = total fields
        const objectsSet = new Set<string>();
        const objectFieldCounts = new Map<string, { total: number; matched: number; ignored: number }>();
        
        result.results.forEach((nsResult: any) => {
          nsResult.items.forEach((item: any) => {
            const objectKey = `${item.kind}/${item.objectName}`;
            objectsSet.add(objectKey);
            
            // Track field counts per object
            if (!objectFieldCounts.has(objectKey)) {
              objectFieldCounts.set(objectKey, { total: 0, matched: 0, ignored: 0 });
            }
            const counts = objectFieldCounts.get(objectKey)!;
            counts.total++;
            
            if (item.status === 'MATCH') {
              counts.matched++;
            } else if (item.status === 'IGNORED') {
              counts.ignored++;
            }
          });
        });
        
        // Count objects where (MATCH + IGNORED) = total fields
        const nsObjects = objectsSet.size;
        const nsMatchedObjects = Array.from(objectFieldCounts.values())
          .filter(counts => (counts.matched + counts.ignored) === counts.total && counts.total > 0)
          .length;
        const objectMatchRate = nsObjects > 0 ? ((nsMatchedObjects / nsObjects) * 100).toFixed(1) : '0';
        
        if (jobData.status.status === 'COMPLETED') completedJobs++;
        totalFields += fieldCount;
        totalMatches += matchCount;
        totalDifferences += diffCount;
        totalIgnored += actualIgnoredCount;
        totalMatchedObjects += nsMatchedObjects;

        // Get namespace name from result
        const namespaceName = result.results.length > 0 
          ? `${result.results[0].vimName}/${result.results[0].namespace}`
          : jobData.status.validationName || jobIdKey;

        summaryData.push({
          key: jobIdKey,
          namespace: namespaceName,
          status: jobData.status.status,
          totalObjects: nsObjects,
          matchedObjects: nsMatchedObjects,
          objectMatchRate: parseFloat(objectMatchRate),
          fields: fieldCount,
          matches: matchCount,
          ignored: actualIgnoredCount,
          differences: diffCount,
          matchRate: parseFloat(matchRate)
        });
      });

      const overallMatchRate = totalFields > 0 ? ((totalMatches / (totalFields - totalIgnored)) * 100).toFixed(1) : '0';

      return (
        <>
          {/* Summary Statistics - Improved Layout */}
          <Row gutter={[16, 16]} style={{ marginBottom: 24 }}>
            {/* Job Statistics Group */}
            <Col xs={24} sm={12} lg={8}>
              <Card 
                size="small" 
                title={<span style={{ fontSize: 13, fontWeight: 600, color: '#595959' }}>Job Statistics</span>}
                headStyle={{ minHeight: 40, padding: '8px 12px', background: '#fafafa' }}
                bodyStyle={{ padding: 16 }}
              >
                <Row gutter={[12, 12]}>
                  <Col span={12}>
                    <Statistic 
                      title="Total Namespaces" 
                      value={individualJobs.size}
                      valueStyle={{ color: '#1890ff', fontSize: 20 }}
                    />
                  </Col>
                  <Col span={12}>
                    <Statistic 
                      title="Completed" 
                      value={completedJobs} 
                      valueStyle={{ color: '#3f8600', fontSize: 20 }}
                    />
                  </Col>
                </Row>
              </Card>
            </Col>

            {/* Object Statistics Group */}
            <Col xs={24} sm={12} lg={8}>
              <Card 
                size="small" 
                title={<span style={{ fontSize: 13, fontWeight: 600, color: '#595959' }}>Object Statistics</span>}
                headStyle={{ minHeight: 40, padding: '8px 12px', background: '#fafafa' }}
                bodyStyle={{ padding: 16 }}
              >
                <Row gutter={[12, 12]}>
                  <Col span={12}>
                    <Statistic 
                      title="Total Objects" 
                      value={totalObjects}
                      valueStyle={{ color: '#1890ff', fontSize: 20 }}
                    />
                  </Col>
                  <Col span={12}>
                    <Statistic 
                      title="Object Matches" 
                      value={totalMatchedObjects}
                      valueStyle={{ color: '#3f8600', fontSize: 20 }}
                    />
                  </Col>
                </Row>
              </Card>
            </Col>

            {/* Field Statistics Group */}
            <Col xs={24} sm={24} lg={8}>
              <Card 
                size="small" 
                title={<span style={{ fontSize: 13, fontWeight: 600, color: '#595959' }}>Field Statistics</span>}
                headStyle={{ minHeight: 40, padding: '8px 12px', background: '#fafafa' }}
                bodyStyle={{ padding: 16 }}
              >
                <Row gutter={[12, 12]}>
                  <Col span={8}>
                    <Statistic 
                      title="Total" 
                      value={totalFields}
                      valueStyle={{ color: '#1890ff', fontSize: 20 }}
                    />
                  </Col>
                  <Col span={8}>
                    <Statistic 
                      title="Matched" 
                      value={totalMatches}
                      valueStyle={{ color: '#3f8600', fontSize: 20 }}
                    />
                  </Col>
                  <Col span={8}>
                    <Statistic
                      title="Ignored" 
                      value={totalIgnored}
                      valueStyle={{ color: '#4b98fc', fontSize: 20 }}
                    />
                  </Col>
                </Row>
              </Card>
            </Col>
          </Row>

          {/* Overall Match Rate - Highlighted */}
          <Row style={{ marginBottom: 24 }}>
            <Col span={24}>
              <Card 
                size="small"
                style={{ 
                  background: parseFloat(overallMatchRate) >= 80 
                    ? 'linear-gradient(135deg, #f6ffed 0%, #f0f9ff 100%)' 
                    : parseFloat(overallMatchRate) >= 50 
                    ? 'linear-gradient(135deg, #fffbf0 0%, #fff7e6 100%)' 
                    : 'linear-gradient(135deg, #fff1f0 0%, #ffebee 100%)',
                  border: `2px solid ${parseFloat(overallMatchRate) >= 80 ? '#b7eb8f' : parseFloat(overallMatchRate) >= 50 ? '#ffd591' : '#ffa39e'}`
                }}
              >
                <Statistic 
                  title={<span style={{ fontSize: 14, fontWeight: 600 }}>Overall Match Rate</span>}
                  value={overallMatchRate}
                  suffix="%"
                  valueStyle={{ 
                    color: parseFloat(overallMatchRate) >= 80 ? '#3f8600' : parseFloat(overallMatchRate) >= 50 ? '#faad14' : '#cf1322',
                    fontSize: '36px',
                    fontWeight: 'bold'
                  }}
                />
              </Card>
            </Col>
          </Row>

          {/* Namespace Summary Table */}
          <div style={{ marginTop: '24px' }}>
            <Typography.Title level={5}>Validation Items</Typography.Title>
            <Table
              dataSource={summaryData}
              pagination={false}
              size="small"
              style={{ marginTop: 16 }}
              onRow={(_record, index) => ({
                onClick: () => {
                  setActiveTab(String(index));
                  setTimeout(() => {
                    resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                  }, 100);
                },
                style: { cursor: 'pointer' }
              })}
              columns={[
                {
                  title: 'Validation Name',
                  dataIndex: 'namespace',
                  key: 'namespace',
                  render: (text: string) => <strong>{text}</strong>
                },
                {
                  title: 'Status',
                  dataIndex: 'status',
                  key: 'status',
                  width: 120,
                  render: (status: string) => (
                    <Tag color={status === 'COMPLETED' ? 'success' : 'processing'}>
                      {status}
                    </Tag>
                  )
                },
                {
                  title: 'Result',
                  key: 'result',
                  width: 100,
                  render: (_, record: any) => {
                    const isOK = record.objectMatchRate === 100 && record.matchRate === 100;
                    return (
                      <Tag color={isOK ? 'success' : 'error'}>
                        {isOK ? 'OK' : 'NOK'}
                      </Tag>
                    );
                  }
                },
                {
                  title: 'Objects',
                  dataIndex: 'totalObjects',
                  key: 'totalObjects',
                  width: 100,
                  align: 'center' as const
                },
                {
                  title: 'Obj Matches',
                  dataIndex: 'matchedObjects',
                  key: 'matchedObjects',
                  width: 120,
                  align: 'center' as const,
                  render: (value: number) => (
                    <span style={{ color: '#3f8600' }}>{value}</span>
                  )
                },
                {
                  title: 'Obj Match Rate (%)',
                  dataIndex: 'objectMatchRate',
                  key: 'objectMatchRate',
                  width: 150,
                  align: 'center' as const,
                  render: (rate: number) => (
                    <Typography.Text
                      type={rate >= 80 ? 'success' : rate >= 50 ? 'warning' : 'danger'}
                    >
                      {rate.toFixed(1)}%
                    </Typography.Text>
                  )
                },
                {
                  title: 'Fields',
                  dataIndex: 'fields',
                  key: 'fields',
                  width: 100,
                  align: 'center' as const
                },
                {
                  title: 'Field Matches',
                  dataIndex: 'matches',
                  key: 'matches',
                  width: 120,
                  align: 'center' as const,
                  render: (value: number) => (
                    <span style={{ color: '#3f8600' }}>{value}</span>
                  )
                },
                {
                  title: 'Ignored',
                  dataIndex: 'ignored',
                  key: 'ignored',
                  width: 100,
                  align: 'center' as const,
                  render: (value: number) => (
                    <span style={{ color: 'rgb(75, 152, 252)' }}>{value || 0}</span>
                  )
                },
                {
                  title: 'Field Match Rate (%)',
                  dataIndex: 'matchRate',
                  key: 'matchRate',
                  width: 160,
                  align: 'center' as const,
                  render: (rate: number) => (
                    <Typography.Text
                      type={rate >= 80 ? 'success' : rate >= 50 ? 'warning' : 'danger'}
                    >
                      {rate.toFixed(1)}%
                    </Typography.Text>
                  )
                }
              ]}
            />
          </div>
        </>
      );
    }

    // Individual namespace result
    const index = parseInt(activeTab);
    const jobEntries = Array.from(individualJobs.entries());
    if (isNaN(index) || index < 0 || index >= jobEntries.length) {
      return null;
    }

    const [, jobData] = jobEntries[index];

    if (!jobData.result) {
      return (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Alert
            message="No Results"
            description="Validation results are not available for this namespace."
            type="warning"
            showIcon
          />
        </div>
      );
    }

    // Display CNF validation results using the reusable component
    return <CnfValidationResultsDisplay result={jobData.result} />;
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '64px' }}>
        <Space>
          <LoadingOutlined style={{ fontSize: 24 }} spin />
          Loading CNF checklist results...
        </Space>
      </div>
    );
  }

  if (error) {
    return (
      <Alert
        message="Error"
        description={`Error loading results: ${error}`}
        type="error"
        showIcon
      />
    );
  }

  if (!batchJob) {
    return (
      <Alert
        message="Error"
        description="Batch job not found"
        type="error"
        showIcon
      />
    );
  }

  // Get namespace names for navigation
  const namespaceNames = Array.from(individualJobs.entries()).map(([jobIdKey, jobData]) => {
    if (jobData.result && jobData.result.results.length > 0) {
      return `${jobData.result.results[0].vimName}/${jobData.result.results[0].namespace}`;
    }
    return jobData.status.validationName || jobIdKey;
  });

  return (
    <>
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {/* Results Section with Fixed Right Sidebar */}
        {individualJobs.size > 0 && (
          <div style={{ position: 'relative' }}>
            {/* Floating Navigation Sidebar - Right Side */}
            <div style={{ 
              position: 'fixed',
              right: '24px',
              top: '120px',
              width: '240px',
              maxHeight: 'calc(100vh - 160px)',
              overflowY: 'auto',
              zIndex: 100,
              background: 'white',
              borderRadius: '8px',
              boxShadow: '0 2px 12px rgba(0,0,0,0.08)',
            }}>
              <Card 
                size="small" 
                title="Navigation" 
                bodyStyle={{ padding: '12px' }}
                headStyle={{ 
                  borderBottom: '1px solid #f0f0f0',
                  padding: '12px 16px',
                  minHeight: 'auto'
                }}
              >
                <Space direction="vertical" style={{ width: '100%' }} size="small">
                  <Button
                    type={activeTab === 'overview' ? 'primary' : 'text'}
                    size="small"
                    block
                    onClick={() => {
                      setActiveTab('overview');
                      setTimeout(() => {
                        resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                      }, 100);
                    }}
                    icon={<BarChartOutlined />}
                    style={{ 
                      justifyContent: 'flex-start',
                      height: 'auto',
                      padding: '8px 12px'
                    }}
                  >
                    Overview
                  </Button>
                  {Array.from(individualJobs.entries()).map(([jobIdKey, jobData], index) => {
                    const namespaceName = namespaceNames[index];
                    const hasErrors = jobData.result ? jobData.result.summary.totalDifferences > 0 : false;
                    
                    return (
                      <Button
                        key={jobIdKey}
                        type={activeTab === String(index) ? 'primary' : 'text'}
                        size="small"
                        block
                        onClick={() => {
                          setActiveTab(String(index));
                          setTimeout(() => {
                            resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                          }, 100);
                        }}
                        icon={getStatusIcon(jobData.status.status, hasErrors)}
                        style={{ 
                          justifyContent: 'flex-start',
                          height: 'auto',
                          padding: '8px 12px'
                        }}
                      >
                        <div style={{ 
                          overflow: 'hidden', 
                          textOverflow: 'ellipsis', 
                          whiteSpace: 'nowrap',
                          flex: 1,
                          textAlign: 'left',
                          fontSize: '13px'
                        }}>
                          {namespaceName}
                        </div>
                      </Button>
                    );
                  })}
                </Space>
              </Card>
            </div>

            {/* Main Content - Wrapped in Card for consistent styling */}
            <Card 
              ref={resultsRef}
              id="validation-results"
              title={activeTab === 'overview' ? 'CNF Checklist Overview' : namespaceNames[parseInt(activeTab)] || 'Validation Result'}
              style={{ scrollMarginTop: '24px' }}
              extra={
                <Space>
                  <Button 
                    icon={<DownloadOutlined />}
                    onClick={handleExportHTML}
                    disabled={individualJobs.size === 0}
                  >
                    Export HTML
                  </Button>
                  <Button 
                    type="primary"
                    icon={<DownloadOutlined />}
                    onClick={exportToExcel}
                    disabled={individualJobs.size === 0}
                  >
                    Export Excel
                  </Button>
                </Space>
              }
            >
              {renderResults()}
            </Card>
          </div>
        )}
      </Space>
    
      {/* Polling Timeout Modal - Always render outside conditional */}
      <Modal
      title="Validation Timeout"
      open={pollingTimeoutModal}
      onOk={() => {
        pollingStartTimeRef.current = Date.now();
        setPollingTimeoutModal(false);
        setLoading(true);
        // Restart polling by re-triggering the effect
        window.location.reload();
      }}
      onCancel={() => {
        setPollingTimeoutModal(false);
        setLoading(false);
      }}
      okText="Continue Waiting (5 more minutes)"
      cancelText="Stop"
    >
      <Alert
        message="The validation has been running for more than 5 minutes."
        description="Do you want to continue waiting for another 5 minutes?"
        type="warning"
        showIcon
      />
    </Modal>
    </>
  );
};

export default CnfChecklistResults;
