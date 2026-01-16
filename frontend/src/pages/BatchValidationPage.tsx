import { useState, useEffect, useRef } from 'react';
import { Button, Card, Table, Space, Typography, App as AntApp, Tag, Spin, Progress, Statistic, Row, Col, Input, Collapse, Modal, Radio, Alert, Upload } from 'antd';
import { PlayCircle, Trash2, CheckCircle, XCircle, Clock, BarChart3, Download, Edit2, Save, X, Settings, FileText, Upload as UploadIcon } from 'lucide-react';
import { NamespaceSearch } from '../components/NamespaceSearch';
import { ValidationResults } from '../components/ValidationResults';
import { ValidationConfigEditor } from '../components/ValidationConfigEditor';
import { FullValidationConfig } from '../components/FullValidationConfig';
import { validationApi } from '../services/api';
import type { ClusterNamespace, BatchValidationRequestItem, ValidationJobResponse, ValidationResultJson } from '../types';
import { useLocation } from 'react-router-dom';

const { Title, Text } = Typography;

export const BatchValidationPage = () => {
  const { message } = AntApp.useApp();
  const location = useLocation();
  const [items, setItems] = useState<BatchValidationRequestItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [jobStatus, setJobStatus] = useState<ValidationJobResponse | null>(null);
  const [individualJobs, setIndividualJobs] = useState<Map<string, { status: ValidationJobResponse, result?: ValidationResultJson }>>(new Map());
  const [activeTab, setActiveTab] = useState<string>('overview');
  const [ignoreFields, setIgnoreFields] = useState<string[]>([]);
  const resultsRef = useRef<HTMLDivElement>(null);
  const [fullConfigModalVisible, setFullConfigModalVisible] = useState(false);
  const [configViewMode, setConfigViewMode] = useState<'ui' | 'yaml'>('ui');
  const [yamlConfig, setYamlConfig] = useState('');
  const [viewMode, setViewMode] = useState<'form' | 'yaml'>('form');

  // Config form state
  const [configNamespaces, setConfigNamespaces] = useState<ClusterNamespace[]>([]);
  const [configName, setConfigName] = useState<string>('');
  const [searchKeyword, setSearchKeyword] = useState<string>('');
  const [hasSearchResults, setHasSearchResults] = useState<boolean>(false);
  const [editingIndex, setEditingIndex] = useState<number>(-1); // -1 means adding new, >= 0 means editing

  // Check if redirected from CNF Checklist page
  useEffect(() => {
    const state = location.state as { jobId?: string; fromCnfChecklist?: boolean };
    if (state?.jobId && state?.fromCnfChecklist) {
      // Load the job that was created by CNF Checklist
      message.info('Loading CNF Checklist results...');
      loadExistingJob(state.jobId);
    }
  }, [location]);

  const loadExistingJob = async (jobId: string) => {
    try {
      setLoading(true);
      const job = await validationApi.getJobStatus(jobId);
      setJobStatus(job);

      if (job.status === 'COMPLETED') {
        // Poll individual jobs
        await pollIndividualJobs(jobId, (job.successfulCount || 0) + (job.failedCount || 0));
      } else {
        // Continue polling if not completed
        const completedJob = await validationApi.pollJobStatus(jobId, (updatedJob) => {
          setJobStatus(updatedJob);
        });
        setJobStatus(completedJob);
        await pollIndividualJobs(jobId, (completedJob.successfulCount || 0) + (completedJob.failedCount || 0));
      }
    } catch (error: any) {
      console.error('Failed to load job:', error);
      message.error('Failed to load validation results');
    } finally {
      setLoading(false);
    }
  };

  // Auto-generate name from search keyword (preferred) or namespaces
  useEffect(() => {
    if (searchKeyword) {
      // Use search keyword as validation name
      setConfigName(searchKeyword);
    } else if (configNamespaces.length >= 2) {
      // Fallback to namespace names
      const namespaceParts = configNamespaces.map(ns => ns.namespace);
      setConfigName(namespaceParts.join(', '));
    } else {
      setConfigName('');
    }
  }, [configNamespaces, searchKeyword]);

  const handleSaveConfig = () => {
    if (configNamespaces.length < 2) {
      message.error('Please select at least 2 namespaces');
      return;
    }

    if (!configName.trim()) {
      message.error('Please enter a name for this validation');
      return;
    }

    const newItem: BatchValidationRequestItem = {
      name: configName.trim(),
      namespaces: configNamespaces.map(ns => `${ns.cluster}/${ns.namespace}`),
      verbose: false
    };

    if (editingIndex >= 0) {
      // Update existing item
      const newItems = [...items];
      newItems[editingIndex] = newItem;
      setItems(newItems);
      message.success('Updated batch item');
    } else {
      // Add new item
      setItems([...items, newItem]);
      message.success('Added batch item');
    }
    
    // Reset
    setConfigNamespaces([]);
    setEditingIndex(-1);
  };

  const handleCancelConfig = () => {
    setConfigNamespaces([]);
    setConfigName('');
    setSearchKeyword('');
    setEditingIndex(-1);
  };

  const handleEditItem = (index: number) => {
    const item = items[index];
    // Parse namespaces back to ClusterNamespace format
    const parsedNamespaces: ClusterNamespace[] = item.namespaces.map(ns => {
      const [cluster, namespace] = ns.split('/');
      return { cluster, namespace };
    });
    setConfigNamespaces(parsedNamespaces);
    setConfigName(item.name);
    setEditingIndex(index);
  };

  const handleDeleteItem = (index: number) => {
    const newItems = [...items];
    newItems.splice(index, 1);
    setItems(newItems);
  };

  const pollIndividualJobs = async (batchJobId: string, count: number) => {
    const jobIds: string[] = [];
    for (let i = 1; i <= count; i++) {
      jobIds.push(`${batchJobId}-${i}`);
    }

    // Poll each individual job
    const pollInterval = setInterval(async () => {
      const updates = new Map(individualJobs);
      let allCompleted = true;

      for (const individualJobId of jobIds) {
        try {
          const status = await validationApi.getJobStatus(individualJobId);
          
          // Check if this job just completed
          const prevData = individualJobs.get(individualJobId);
          const wasNotCompleted = !prevData || (prevData.status.status !== 'COMPLETED' && prevData.status.status !== 'FAILED');
          const isNowCompleted = status.status === 'COMPLETED' || status.status === 'FAILED';

          let result: ValidationResultJson | undefined;
          if (status.status === 'COMPLETED' && (!prevData || !prevData.result)) {
            try {
              result = await validationApi.getValidationResults(individualJobId);
              if (wasNotCompleted && isNowCompleted) {
                message.success(`Validation "${items[jobIds.indexOf(individualJobId)]?.name}" completed`);
              }
            } catch (e) {
              console.error(`Failed to fetch results for ${individualJobId}`, e);
            }
          } else if (prevData?.result) {
            result = prevData.result;
          }

          updates.set(individualJobId, { status, result });

          if (status.status === 'PENDING' || status.status === 'PROCESSING') {
            allCompleted = false;
          }
        } catch (error) {
          console.error(`Error polling job ${individualJobId}`, error);
        }
      }

      setIndividualJobs(updates);

      if (allCompleted) {
        clearInterval(pollInterval);
        setLoading(false);
        message.success('All batch validations completed!');
      }
    }, 2000);
  };

  const handleRunBatch = async () => {
    if (items.length === 0) {
      message.error('No items to validate');
      return;
    }

    setLoading(true);
    setIndividualJobs(new Map());
    setActiveTab('overview');
    
    try {
      const response = await validationApi.submitBatchValidation({
        requests: items,
        globalSettings: { parallel: true }
      });
      
      setJobStatus(response);
      message.success(`Batch job started: ${response.jobId}`);
      
      // Start polling batch job status
      const completedJob = await validationApi.pollJobStatus(
        response.jobId,
        (job) => {
          setJobStatus(job);
        }
      );

      setJobStatus(completedJob);
      
      // Now poll individual jobs
      pollIndividualJobs(response.jobId, items.length);
      
      // Auto-scroll to results when batch completes
      setTimeout(() => {
        resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }, 500);
      
    } catch (error) {
      console.error(error);
      message.error('Failed to start batch validation');
      setLoading(false);
    }
  };

  const generateYamlFromState = () => {
    const config = {
      items: items,
      ignoreFields: ignoreFields
    };
    return `# Batch Validation Configuration
# Generated: ${new Date().toLocaleString()}
#
# Instructions:
# 1. Define validation items first - each item represents a separate validation job
# 2. Configure ignore fields to exclude specific fields from comparison
# 3. Each validation item needs:
#    - name: A descriptive name for the validation
#    - namespaces: List of cluster/namespace pairs (format: "cluster/namespace")
#    - verbose: true/false for detailed output

# List of validation items to execute
validationItems:
${items.map(item => `  - name: "${item.name}"
    namespaces:
${item.namespaces.map(ns => `      - "${ns}"`).join('\n')}
    verbose: ${item.verbose || false}`).join('\n')}
# - name: "My Validation"
#   namespaces:
#     - "cluster1/namespaceA"
#     - "cluster2/namespaceB"

# Fields to ignore during comparison (e.g., timestamps, dynamic values)
ignoreFields:
${config.ignoreFields.map(f => `  - "${f}"`).join('\n')}`;
  };

  const parseYamlToState = (yaml: string) => {
    try {
      const lines = yaml.split('\n');
      const parsedItems: BatchValidationRequestItem[] = [];
      const ignoreFieldsList: string[] = [];
      let currentItem: Partial<BatchValidationRequestItem> = {};
      let inIgnoreFields = false;
      let inItems = false;
      let inNamespaces = false;
      
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('ignoreFields:')) {
          inIgnoreFields = true;
          inItems = false;
        } else if (trimmed.startsWith('validationItems:')) {
          inIgnoreFields = false;
          inItems = true;
        } else if (trimmed.startsWith('- name:') && inItems) {
          if (currentItem.name) {
            parsedItems.push(currentItem as BatchValidationRequestItem);
          }
          currentItem = { name: trimmed.split('"')[1], namespaces: [], verbose: false };
          inNamespaces = false;
        } else if (trimmed.startsWith('namespaces:') && inItems) {
          inNamespaces = true;
        } else if (trimmed.startsWith('- "') && inIgnoreFields) {
          ignoreFieldsList.push(trimmed.split('"')[1]);
        } else if (trimmed.startsWith('- "') && inNamespaces) {
          currentItem.namespaces = currentItem.namespaces || [];
          currentItem.namespaces.push(trimmed.split('"')[1]);
        } else if (trimmed.startsWith('verbose:') && inItems) {
          currentItem.verbose = trimmed.includes('true');
        }
      }
      if (currentItem.name) {
        parsedItems.push(currentItem as BatchValidationRequestItem);
      }
      
      setItems(parsedItems);
      setIgnoreFields(ignoreFieldsList);
      message.success(`Loaded ${parsedItems.length} validation items and ${ignoreFieldsList.length} ignore fields from YAML`);
    } catch (error) {
      console.error('Failed to parse YAML:', error);
      message.error('Failed to parse YAML configuration');
    }
  };

  const handleExportAll = async () => {
    if (!jobStatus) return;
    
    message.loading('Preparing download...', 0);
    
    try {
      const response = await fetch(`/kvalidator/api/validate/batch/${jobStatus.jobId}/export-zip`);
      
      message.destroy();
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || 'Failed to download ZIP');
      }
      
      // Get filename from Content-Disposition header or use default
      const contentDisposition = response.headers.get('Content-Disposition');
      let filename = `batch-results-${jobStatus.jobId}.zip`;
      if (contentDisposition) {
        const matches = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/.exec(contentDisposition);
        if (matches != null && matches[1]) {
          filename = matches[1].replace(/['"]/g, '');
        }
      }
      
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      
      message.success('Batch results downloaded successfully');
    } catch (error: any) {
      console.error('Export failed:', error);
      message.error(error.message || 'Failed to export batch results');
    }
  };

  const handleExportHTML = () => {
    if (!jobStatus) return;

    const htmlContent = generateHTMLReport();
    const blob = new Blob([htmlContent], { type: 'text/html' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `batch-validation-report-${jobStatus.jobId}.html`;
    document.body.appendChild(a);
    a.click();
    window.URL.revokeObjectURL(url);
    document.body.removeChild(a);
    
    message.success('HTML report downloaded successfully');
  };

  const generateHTMLReport = (): string => {
    const timestamp = new Date().toLocaleString();
    
    let totalCompleted = 0;
    let totalFailed = 0;
    let totalObjects = 0;
    let totalDifferences = 0;

    // Build navigation tree
    let navTree = '<div class="nav-section"><a href="#batch-overview" class="nav-item nav-level-1"><span class="nav-icon">📊</span><span class="nav-text">Batch Overview</span></a></div>';
    
    // Build validation sections with hierarchical structure
    const validationSections = items.map((item, index) => {
      const jobId = jobStatus ? `${jobStatus.jobId}-${index + 1}` : '';
      const jobData = individualJobs.get(jobId);
      const result = jobData?.result;
      
      const objectsCompared = result?.summary.totalObjects || 0;
      const differences = result?.summary.totalDifferences || 0;
      const onRate = objectsCompared > 0 ? ((objectsCompared - differences) / objectsCompared * 100) : 0;
      const isOk = jobData?.status.status === 'COMPLETED' && differences === 0;
      
      if (jobData?.status.status === 'COMPLETED') totalCompleted++;
      if (jobData?.status.status === 'FAILED') totalFailed++;
      totalObjects += objectsCompared;
      totalDifferences += differences;

      // Build navigation for this validation and its namespaces
      let validationNav = `
        <div class="nav-section">
          <a href="#validation-${index}" class="nav-item nav-level-1 ${isOk ? 'nav-ok' : 'nav-nok'}" onclick="toggleNav(this)">
            <span class="nav-icon">${isOk ? '✓' : '✗'}</span>
            <span class="nav-text">${item.name}</span>
            <span class="nav-toggle">▼</span>
          </a>
          <div class="nav-children">
      `;

      // Build object details with expandable rows
      let objectDetailsHTML = '';
      if (result?.comparisons) {
        Object.entries(result.comparisons).forEach(([nsKey, nsComp]) => {
          // Add namespace to navigation
          validationNav += `<a href="#ns-${index}-${nsKey.replace(/[^a-zA-Z0-9]/g, '-')}" class="nav-item nav-level-2"><span class="nav-text">${nsKey}</span></a>`;
          
          const objectRows = Object.entries(nsComp.objectComparisons).map(([, objComp], objIdx) => {
            const status = objComp.fullMatch ? 'OK' : 'NOK';
            const diffCount = objComp.differenceCount || 0;

            return `
              <tr class="object-row ${status === 'NOK' ? 'has-diff' : ''}">
                <td style="width: 40px;">${objIdx + 1}</td>
                <td>${objComp.objectType}</td>
                <td>${objComp.objectId}</td>
                <td style="text-align: center;">
                  <span class="status-badge ${status === 'OK' ? 'status-ok' : 'status-nok'}">${status}</span>
                </td>
                <td style="text-align: center;">${nsComp.leftNamespace} vs ${nsComp.rightNamespace}</td>
                <td style="text-align: center;">${diffCount}</td>
              </tr>
            `;
          }).join('');

          objectDetailsHTML += `
            <div id="ns-${index}-${nsKey.replace(/[^a-zA-Z0-9]/g, '-')}" class="namespace-group">
              <h4 class="namespace-title" onclick="toggleSection(this)">
                <span class="section-toggle">▼</span>
                ${nsKey} <span class="object-count">(${Object.keys(nsComp.objectComparisons).length} objects)</span>
              </h4>
              <div class="namespace-content">
                <table class="excel-table">
                  <thead>
                    <tr>
                      <th style="width: 40px;">STT</th>
                      <th>Kind</th>
                      <th>Object Name</th>
                      <th>Status</th>
                      <th>Namespaces</th>
                      <th>Differences</th>
                    </tr>
                  </thead>
                  <tbody>
                    ${objectRows}
                  </tbody>
                </table>
              </div>
            </div>
          `;
        });
      } else {
        objectDetailsHTML = '<p style="text-align: center; color: #999; padding: 20px;">No data available</p>';
      }

      validationNav += `</div></div>`;
      navTree += validationNav;

      return `
        <div id="validation-${index}" class="validation-section">
          <h2 class="section-title" onclick="toggleSection(this)">
            <span class="section-toggle">▼</span>
            ${item.name}
          </h2>
          <div class="section-content">
            <table class="summary-table excel-table">
              <tr>
                <th>Status</th>
                <th>Result</th>
                <th>Objects</th>
                <th>Differences</th>
                <th>ON Rate</th>
              </tr>
              <tr>
                <td><span class="status-badge ${jobData?.status.status === 'COMPLETED' ? 'status-ok' : 'status-failed'}">${jobData?.status.status || 'PENDING'}</span></td>
                <td><span class="status-badge ${isOk ? 'status-ok' : 'status-nok'}">${isOk ? 'OK' : 'NOK'}</span></td>
                <td>${objectsCompared}</td>
                <td style="color: ${differences > 0 ? '#ff4d4f' : '#52c41a'}">${differences}</td>
                <td style="color: ${onRate === 100 ? '#52c41a' : onRate >= 80 ? '#faad14' : '#ff4d4f'}">${onRate.toFixed(2)}%</td>
              </tr>
              <tr>
                <td colspan="5"><strong>Namespaces:</strong> ${item.namespaces.join(', ')}</td>
              </tr>
            </table>
            
            <h3>Object Details</h3>
            ${objectDetailsHTML}
          </div>
        </div>
      `;
    }).join('');

    const overallOnRate = totalObjects > 0 ? ((totalObjects - totalDifferences) / totalObjects * 100) : 0;

    return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Batch Validation Report - ${jobStatus?.jobId}</title>
  <style>
    * {
      margin: 0;
      padding: 0;
      box-sizing: border-box;
    }
    html {
      scroll-behavior: smooth;
    }
    body {
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
      line-height: 1.6;
      color: #333;
      background: #f5f5f5;
      display: flex;
      min-height: 100vh;
    }
    .sidebar-nav {
      width: 280px;
      background: white;
      padding: 20px;
      position: sticky;
      top: 0;
      height: 100vh;
      overflow-y: auto;
      border-right: 1px solid #e8e8e8;
      flex-shrink: 0;
    }
    .sidebar-nav h3 {
      color: #1890ff;
      margin-bottom: 16px;
      font-size: 16px;
    }
    .nav-item {
      display: flex;
      align-items: center;
      padding: 10px 12px;
      margin-bottom: 8px;
      border-radius: 6px;
      text-decoration: none;
      color: #333;
      transition: all 0.2s;
      border-left: 3px solid transparent;
    }
    .nav-item:hover {
      background: #f5f5f5;
    }
    .nav-item.nav-ok {
      border-left-color: #52c41a;
    }
    .nav-item.nav-nok {
      border-left-color: #ff4d4f;
    }
    .nav-icon {
      width: 20px;
      height: 20px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 10px;
      font-size: 12px;
      font-weight: bold;
      flex-shrink: 0;
    }
    .nav-ok .nav-icon {
      background: #f6ffed;
      color: #52c41a;
    }
    .nav-nok .nav-icon {
      background: #fff2e8;
      color: #ff4d4f;
    }
    .nav-text {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 14px;
    }
    .nav-toggle {
      margin-left: auto;
      font-size: 10px;
      transition: transform 0.2s;
    }
    .nav-section {
      margin-bottom: 4px;
    }
    .nav-children {
      max-height: 500px;
      overflow: hidden;
      transition: max-height 0.3s ease;
    }
    .nav-children.collapsed {
      max-height: 0;
    }
    .nav-level-2 {
      padding-left: 32px;
      font-size: 13px;
    }
    .main-content {
      flex: 1;
      background: white;
      padding: 40px;
      overflow-y: auto;
    }
    .container {
      max-width: 1200px;
      margin: 0 auto;
    }
    h1 {
      color: #1890ff;
      margin-bottom: 10px;
      font-size: 32px;
    }
    .meta {
      color: #666;
      margin-bottom: 30px;
      font-size: 14px;
    }
    .overview {
      background: #fafafa;
      padding: 24px;
      border-radius: 8px;
      margin-bottom: 30px;
    }
    .stats-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
      margin: 20px 0;
    }
    .stat-card {
      background: white;
      padding: 16px;
      border-radius: 6px;
      border: 1px solid #e8e8e8;
    }
    .stat-label {
      font-size: 12px;
      color: #666;
      text-transform: uppercase;
      margin-bottom: 8px;
    }
    .stat-value {
      font-size: 24px;
      font-weight: 600;
      color: #1890ff;
    }
    .validation-section {
      margin-bottom: 60px;
      padding: 32px;
      background: #fafafa;
      border-radius: 8px;
      border-left: 4px solid #1890ff;
      scroll-margin-top: 20px;
    }
    .validation-section h2 {
      color: #1890ff;
      margin-bottom: 24px;
      font-size: 24px;
    }
    .validation-section h3 {
      color: #333;
      margin: 24px 0 16px 0;
      font-size: 18px;
    }
    .comparisons-section {
      margin-top: 24px;
    }
    .namespace-section {
      margin-bottom: 20px;
    }
    .namespace-section summary {
      cursor: pointer;
      padding: 12px 16px;
      background: white;
      border-radius: 4px;
      margin-bottom: 12px;
      user-select: none;
      font-size: 14px;
      border: 1px solid #e8e8e8;
    }
    .namespace-section summary:hover {
      background: #f0f0f0;
    }
    .namespaces {
      margin: 16px 0;
      padding: 12px;
      background: white;
      border-radius: 4px;
      font-size: 14px;
    }
    .status-badge {
      display: inline-block;
      padding: 4px 12px;
      border-radius: 4px;
      font-size: 12px;
      font-weight: 600;
      text-transform: uppercase;
    }
    .status-ok {
      background: #f6ffed;
      color: #52c41a;
      border: 1px solid #b7eb8f;
    }
    .status-nok {
      background: #fff2e8;
      color: #fa8c16;
      border: 1px solid #ffd591;
    }
    .status-failed {
      background: #fff1f0;
      color: #ff4d4f;
      border: 1px solid #ffa39e;
    }
    .excel-table {
      width: 100%;
      border-collapse: collapse;
      background: white;
      font-size: 13px;
      border: 1px solid #d9d9d9;
    }
    .excel-table th,
    .excel-table td {
      padding: 8px 12px;
      border: 1px solid #d9d9d9;
      text-align: left;
    }
    .excel-table th {
      background: #f0f0f0;
      font-weight: 600;
      color: #262626;
      text-align: center;
    }
    .excel-table tr:nth-child(even) {
      background: #fafafa;
    }
    .excel-table tr:hover {
      background: #e6f7ff;
    }
    .summary-table {
      margin-bottom: 24px;
      width: auto;
      max-width: 800px;
    }
    .summary-table th {
      text-align: left;
      width: 20%;
    }
    .section-title {
      cursor: pointer;
      user-select: none;
      display: flex;
      align-items: center;
      gap: 8px;
    }
    .section-toggle {
      display: inline-block;
      transition: transform 0.2s;
      font-size: 14px;
    }
    .section-title.collapsed .section-toggle {
      transform: rotate(-90deg);
    }
    .section-content {
      max-height: 100000px;
      overflow: hidden;
      transition: max-height 0.3s ease;
    }
    .section-content.collapsed {
      max-height: 0;
    }
    .namespace-title {
      cursor: pointer;
      user-select: none;
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 12px 16px;
      background: #fafafa;
      border-radius: 4px;
      margin: 12px 0 8px 0;
    }
    .namespace-title:hover {
      background: #f0f0f0;
    }
    .object-count {
      color: #666;
      font-size: 14px;
      font-weight: normal;
    }
    .namespace-content {
      max-height: 100000px;
      overflow: hidden;
      transition: max-height 0.3s ease;
    }
    .namespace-content.collapsed {
      max-height: 0;
    }
    .object-row {
      cursor: pointer;
    }
    .object-row.has-diff:hover {
      background: #fffbe6 !important;
    }
    .expand-icon {
      display: inline-block;
      margin-right: 8px;
      transition: transform 0.2s;
      font-size: 10px;
    }
    .object-row.expanded .expand-icon {
      transform: rotate(90deg);
    }
    .diff-details {
      padding: 16px;
      background: #fafafa;
      border-radius: 4px;
      margin: 8px;
    }
    .diff-details table {
      width: 100%;
      border-collapse: collapse;
    }
    .diff-details code {
      background: #f5f5f5;
      padding: 2px 6px;
      border-radius: 3px;
      font-family: 'Courier New', monospace;
      font-size: 12px;
    }

    .table-container {
      overflow-x: auto;
      margin-top: 8px;
    }
    .footer {
      margin-top: 40px;
      padding-top: 20px;
      border-top: 2px solid #e8e8e8;
      text-align: center;
      color: #999;
      font-size: 12px;
    }
  </style>
  <script>
    function toggleNav(element) {
      event.preventDefault();
      const children = element.parentElement.querySelector('.nav-children');
      const toggle = element.querySelector('.nav-toggle');
      if (children) {
        children.classList.toggle('collapsed');
        if (toggle) {
          toggle.style.transform = children.classList.contains('collapsed') ? 'rotate(-90deg)' : 'rotate(0deg)';
        }
      }
    }

    function toggleSection(element) {
      const content = element.nextElementSibling;
      if (content && content.classList.contains('section-content') || content.classList.contains('namespace-content')) {
        content.classList.toggle('collapsed');
        element.classList.toggle('collapsed');
      }
    }

    function toggleObjectDetails(row) {
      if (!row.classList.contains('has-diff')) return;
      
      const nextRow = row.nextElementSibling;
      if (nextRow && nextRow.classList.contains('details-container')) {
        const isHidden = nextRow.style.display === 'none';
        nextRow.style.display = isHidden ? 'table-row' : 'none';
        row.classList.toggle('expanded');
      }
    }

    // Expand all navigation on load
    document.addEventListener('DOMContentLoaded', function() {
      document.querySelectorAll('.nav-children').forEach(el => {
        el.classList.remove('collapsed');
      });
    });
  </script>
</head>
<body>
  <aside class="sidebar-nav">
    <h3>Navigation</h3>
    ${navTree}
  </aside>
  <main class="main-content">
    <div class="container">
    <h1 id="batch-overview">Batch Validation Report</h1>
    <div class="meta">
      <div><strong>Batch Job ID:</strong> ${jobStatus?.jobId}</div>
      <div><strong>Generated:</strong> ${timestamp}</div>
      <div><strong>Status:</strong> <span class="status-badge ${jobStatus?.status === 'COMPLETED' ? 'status-ok' : 'status-failed'}">${jobStatus?.status}</span></div>
    </div>

    <div class="overview">
      <h2>Batch Overview</h2>
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-label">Total Validations</div>
          <div class="stat-value">${items.length}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Completed</div>
          <div class="stat-value" style="color: #52c41a">${totalCompleted}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Failed</div>
          <div class="stat-value" style="color: #ff4d4f">${totalFailed}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Total Objects</div>
          <div class="stat-value">${totalObjects}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Total Differences</div>
          <div class="stat-value" style="color: ${totalDifferences > 0 ? '#ff4d4f' : '#52c41a'}">${totalDifferences}</div>
        </div>
        <div class="stat-card">
          <div class="stat-label">Overall ON Rate</div>
          <div class="stat-value" style="color: ${overallOnRate === 100 ? '#52c41a' : overallOnRate >= 80 ? '#faad14' : '#ff4d4f'}">${overallOnRate.toFixed(2)}%</div>
        </div>
      </div>
    </div>

    <h2 style="margin: 40px 0 20px 0;">Validation Results</h2>
    ${validationSections}

    <div class="footer">
      <p>KValidator - Kubernetes Configuration Validation Tool</p>
      <p>Report generated on ${timestamp}</p>
    </div>
    </div>
  </main>
</body>
</html>`;
  };

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircle size={16} color="#52c41a" />;
      case 'FAILED':
        return <XCircle size={16} color="#ff4d4f" />;
      case 'PROCESSING':
        return <Spin size="small" />;
      default:
        return <Clock size={16} color="#999" />;
    }
  };

  const listColumns = [
    {
      title: 'Name',
      dataIndex: 'name',
      key: 'name',
      width: '30%',
    },
    {
      title: 'Namespaces',
      dataIndex: 'namespaces',
      key: 'namespaces',
      render: (namespaces: string[]) => (
        <>
          {namespaces.map(ns => (
            <Tag key={ns}>{ns}</Tag>
          ))}
        </>
      ),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 150,
      render: (_: any, __: any, index: number) => (
        <Space>
          <Button 
            type="link"
            size="small"
            icon={<Edit2 size={14} />}
            onClick={() => handleEditItem(index)}
            disabled={loading}
          >
            Edit
          </Button>
          <Button 
            type="link"
            danger 
            size="small"
            icon={<Trash2 size={14} />} 
            onClick={() => handleDeleteItem(index)}
            disabled={loading}
          >
            Delete
          </Button>
        </Space>
      ),
    },
  ];

  // Render results based on active tab
  const renderResults = () => {
    if (activeTab === 'overview') {
      // Calculate statistics
      let totalCompleted = 0;
      let totalFailed = 0;
      let totalProcessing = 0;
      let totalObjects = 0;
      let totalDifferences = 0;

      const summaryData = items.map((item, index) => {
        const jobId = jobStatus ? `${jobStatus.jobId}-${index + 1}` : '';
        const jobData = individualJobs.get(jobId);
        const result = jobData?.result;
        
        const objectsCompared = result?.summary.totalObjects || 0;
        const differences = result?.summary.totalDifferences || 0;
        const onRate = objectsCompared > 0 ? ((objectsCompared - differences) / objectsCompared * 100) : 0;
        const isOk = jobData?.status.status === 'COMPLETED' && differences === 0;
        
        if (jobData?.status.status === 'COMPLETED') totalCompleted++;
        if (jobData?.status.status === 'FAILED') totalFailed++;
        if (jobData?.status.status === 'PROCESSING') totalProcessing++;
        totalObjects += objectsCompared;
        totalDifferences += differences;

        return {
          key: index,
          name: item.name,
          status: jobData?.status.status || 'PENDING',
          okStatus: jobData?.status.status === 'COMPLETED' ? (isOk ? 'OK' : 'NOK') : '-',
          objectsCompared,
          differences,
          onRate: onRate.toFixed(2),
          namespaces: item.namespaces.length,
        };
      });

      const summaryColumns = [
        {
          title: 'Validation Name',
          dataIndex: 'name',
          key: 'name',
        },
        {
          title: 'Status',
          dataIndex: 'status',
          key: 'status',
          render: (status: string) => (
            <Tag color={status === 'COMPLETED' ? 'success' : status === 'FAILED' ? 'error' : 'processing'}>
              {status}
            </Tag>
          ),
        },
        {
          title: 'Result',
          dataIndex: 'okStatus',
          key: 'okStatus',
          render: (okStatus: string) => (
            <Tag color={okStatus === 'OK' ? 'success' : okStatus === 'NOK' ? 'error' : 'default'}>
              {okStatus}
            </Tag>
          ),
        },
        {
          title: 'Objects',
          dataIndex: 'objectsCompared',
          key: 'objectsCompared',
        },
        {
          title: 'Differences',
          dataIndex: 'differences',
          key: 'differences',
          render: (diff: number) => (
            <Text type={diff > 0 ? 'danger' : 'success'}>{diff}</Text>
          ),
        },
        {
          title: 'ON Rate (%)',
          dataIndex: 'onRate',
          key: 'onRate',
          render: (rate: string) => {
            const rateNum = parseFloat(rate);
            return (
              <Text type={rateNum === 100 ? 'success' : rateNum >= 80 ? 'warning' : 'danger'}>
                {rate}%
              </Text>
            );
          },
        },
        {
          title: 'Namespaces',
          dataIndex: 'namespaces',
          key: 'namespaces',
        },
      ];

      const overallOnRate = totalObjects > 0 ? ((totalObjects - totalDifferences) / totalObjects * 100) : 0;

      return (
        <>
          {/* Summary Statistics */}
          <Row gutter={[16, 16]}>
            <Col span={4}>
              <Card size="small">
                <Statistic 
                  title="Total Validations" 
                  value={items.length}
                  valueStyle={{ color: '#1890ff' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card size="small">
                <Statistic 
                  title="Completed" 
                  value={totalCompleted} 
                  valueStyle={{ color: '#3f8600' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card size="small">
                <Statistic 
                  title="Processing" 
                  value={totalProcessing} 
                  valueStyle={{ color: '#faad14' }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card size="small">
                <Statistic 
                  title="Failed" 
                  value={totalFailed} 
                  valueStyle={{ color: totalFailed > 0 ? '#cf1322' : undefined }}
                />
              </Card>
            </Col>
            <Col span={4}>
              <Card size="small">
                <Statistic title="Total Objects" value={totalObjects} />
              </Card>
            </Col>
            <Col span={4}>
              <Card size="small">
                <Statistic 
                  title="Total Differences" 
                  value={totalDifferences} 
                  valueStyle={{ color: totalDifferences > 0 ? '#cf1322' : '#3f8600' }}
                />
              </Card>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={24}>
              <Card size="small">
                <Statistic 
                  title="Overall ON Rate" 
                  value={overallOnRate.toFixed(2)} 
                  suffix="%" 
                  valueStyle={{ 
                    color: overallOnRate === 100 ? '#3f8600' : overallOnRate >= 80 ? '#faad14' : '#cf1322',
                    fontSize: '32px'
                  }}
                />
              </Card>
            </Col>
          </Row>

          {/* Validation Items Table */}
          <div style={{ marginTop: '24px' }}>
            <Title level={5}>Validation Items</Title>
            <Table 
              dataSource={summaryData} 
              columns={summaryColumns} 
              pagination={false}
            />
          </div>
        </>
      );
    }

    // Individual validation result
    const index = parseInt(activeTab);
    if (isNaN(index) || index < 0 || index >= items.length) {
      return null;
    }

    const jobId = jobStatus ? `${jobStatus.jobId}-${index + 1}` : '';
    const jobData = individualJobs.get(jobId);

    if (!jobData) {
      return (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Text type="secondary">Waiting to start...</Text>
        </div>
      );
    }

    if (jobData.status.status === 'COMPLETED' && jobData.result) {
      return <ValidationResults result={jobData.result} />;
    }

    if (jobData.status.status === 'FAILED') {
      return (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <XCircle size={48} color="#ff4d4f" />
          <h3>Validation Failed</h3>
          <Text type="danger">{jobData.status.message || 'Unknown error'}</Text>
        </div>
      );
    }

    return (
      <div style={{ textAlign: 'center', padding: 40 }}>
        <Spin size="large" />
        <h3>Processing...</h3>
        <Text type="secondary">Job ID: {jobId}</Text>
        {jobData.status.progress && (
          <>
            <Progress 
              percent={Math.round(jobData.status.progress.percentage)} 
              status="active"
              style={{ marginTop: 16, maxWidth: 400, margin: '16px auto' }}
            />
            <Text type="secondary">{jobData.status.progress.currentStep}</Text>
          </>
        )}
      </div>
    );
  };

  return (
    <div style={{ padding: '24px 50px' }}>
      <div style={{ maxWidth: 1400, margin: '0 auto' }}>
        <Card
          title="Batch Validation"
          extra={
            <Radio.Group 
              value={viewMode} 
              onChange={(e) => {
                const newMode = e.target.value;
                
                // Convert data when switching modes
                if (newMode === 'yaml' && viewMode === 'form') {
                  // Form → YAML
                  const yaml = generateYamlFromState();
                  setYamlConfig(yaml);
                  message.success('Switched to YAML view');
                } else if (newMode === 'form' && viewMode === 'yaml') {
                  // YAML → Form
                  parseYamlToState(yamlConfig);
                  message.success('Switched to Form view');
                }
                
                setViewMode(newMode);
              }}
              // size="large"
            >
              <Radio.Button value="form">
                <Settings size={14} style={{ marginRight: 6, verticalAlign: 'middle' }} />
                Form
              </Radio.Button>
              <Radio.Button value="yaml">
                <FileText size={14} style={{ marginRight: 6, verticalAlign: 'middle' }} />
                Yaml
              </Radio.Button>
            </Radio.Group>
          }
        >
          <Space direction="vertical" style={{ width: '100%' }} size="large">
          {viewMode === 'form' ? (
            <>
              <Alert
                message="Batch Validation"
                description={
                  <div>
                    <p>Execute multiple validation jobs in parallel across different namespaces.</p>
                    <p><strong>How it works:</strong></p>
                    <ol style={{ marginBottom: 0, paddingLeft: 20 }}>
                      <li><strong>Add Items:</strong> Configure validation items with names and namespaces</li>
                      <li><strong>Configure:</strong> Set ignore rules and verbose options per item</li>
                      <li><strong>Execute:</strong> Run all validations in parallel and view aggregated results</li>
                    </ol>
                  </div>
                }
                type="info"
                showIcon
              />

        {/* Config Section - Always visible */}
        <Card 
          title={editingIndex >= 0 ? 'Edit Validation Item' : 'Add Validation Item'}
          extra={
            configNamespaces.length > 0 && (
              <Button 
                type="link"
                icon={<X size={16} />}
                onClick={handleCancelConfig}
              >
                Clear
              </Button>
            )
          }
        >
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <div>
              <Text strong style={{ display: 'block', marginBottom: 8 }}>
                Select Namespaces (minimum 2 required):
              </Text>
              <NamespaceSearch 
                onSearchKeywordChange={setSearchKeyword}
                selectedNamespaces={configNamespaces}
                onNamespacesChange={setConfigNamespaces}
                onSearchResultsChange={setHasSearchResults}
              />
            </div>
            
            {hasSearchResults && (
              <div>
                <Text strong style={{ display: 'block', marginBottom: 8 }}>
                  Validation Name:
                </Text>
                <Input
                  size="large"
                  placeholder="e.g. Dev vs Staging"
                  value={configName}
                  onChange={(e) => setConfigName(e.target.value)}
                  disabled={configNamespaces.length < 2}
                />
                {configNamespaces.length >= 2 && (
                  <Text type="secondary" style={{ fontSize: '12px', marginTop: 4, display: 'block' }}>
                    Auto-generated from selected namespaces. You can customize it.
                  </Text>
                )}
              </div>
            )}
            
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', paddingTop: 8 }}>
              <Text type="secondary">
                {configNamespaces.length >= 2 ? (
                  <span style={{ color: '#52c41a' }}>
                    ✓ Ready to add
                  </span>
                ) : (
                  `Selected: ${configNamespaces.length} namespace${configNamespaces.length !== 1 ? 's' : ''}`
                )}
              </Text>
              <Space>
                {editingIndex >= 0 && (
                  <Button onClick={handleCancelConfig}>
                    Cancel
                  </Button>
                )}
                <Button 
                  type="primary"
                  icon={<Save size={16} />}
                  onClick={handleSaveConfig}
                  disabled={configNamespaces.length < 2}
                >
                  {editingIndex >= 0 ? 'Update' : 'Add to Batch'}
                </Button>
              </Space>
            </div>
          </Space>
        </Card>

        {/* Validation Configuration */}
        <div style={{ marginBottom: 24 }}>
          <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <strong>Configuration View:</strong>
            <Radio.Group 
              value={configViewMode} 
              onChange={(e) => {
                const newMode = e.target.value;
                
                if (newMode === 'yaml' && configViewMode === 'ui') {
                  const yaml = generateYamlFromState();
                  setYamlConfig(yaml);
                  message.success('Converted to YAML view');
                } else if (newMode === 'ui' && configViewMode === 'yaml') {
                  parseYamlToState(yamlConfig);
                }
                
                setConfigViewMode(newMode);
              }}
              size="small"
            >
              <Radio.Button value="ui">
                <Settings size={14} style={{ marginRight: 6, verticalAlign: 'middle' }} />
                UI View
              </Radio.Button>
              <Radio.Button value="yaml">
                <FileText size={14} style={{ marginRight: 6, verticalAlign: 'middle' }} />
                YAML View
              </Radio.Button>
            </Radio.Group>
          </div>

          {configViewMode === 'ui' ? (
            <Collapse
              items={[{
                key: 'config',
                label: (
                  <span>
                    <Settings size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
                    Ignore Rules Configuration (Optional)
                  </span>
                ),
                children: <ValidationConfigEditor showTitle={false} onConfigChange={(config) => setIgnoreFields(config.ignoreFields)} />
              }]}
            />
          ) : (
            <Card title="YAML Configuration" size="small">
              <Alert
                message="YAML Configuration Editor"
                description="Edit the complete batch validation configuration in YAML format. Changes will be applied when switching back to UI view."
                type="info"
                showIcon
                style={{ marginBottom: 16 }}
              />
              <Input.TextArea
                value={yamlConfig}
                onChange={(e) => setYamlConfig(e.target.value)}
                rows={20}
                style={{ fontFamily: 'monospace', fontSize: 12 }}
                placeholder="YAML configuration will appear here..."
              />
            </Card>
          )}
        </div>

        {/* Batch Items List */}
        {items.length === 0 ? (
          <Card style={{ textAlign: 'center', padding: '20px' }}>
            <Text type="secondary">No batch items yet. Configure and add items above.</Text>
          </Card>
        ) : (
          <Card 
            title={`Batch Items (${items.length})`}
            extra={
              <Button 
                type="primary" 
                icon={<PlayCircle size={16} />} 
                onClick={handleRunBatch}
                loading={loading}
                disabled={items.length === 0}
                size="large"
              >
                Run Batch Validation
              </Button>
            }
          >
            <Table 
              dataSource={items} 
              columns={listColumns} 
              rowKey={(_, index) => index?.toString() || '0'} 
              pagination={false} 
            />
          </Card>
        )}

        {/* Batch Status */}
        {jobStatus && (
          <Card title="Batch Execution Status">
            <Space direction="vertical" style={{ width: '100%' }}>
              <div>
                <Text strong>Batch Job ID:</Text> <Text code>{jobStatus.jobId}</Text>
              </div>
              <div>
                <Text strong>Status:</Text> <Tag color={jobStatus.status === 'COMPLETED' ? 'success' : jobStatus.status === 'FAILED' ? 'error' : 'processing'}>{jobStatus.status}</Tag>
              </div>
              {jobStatus.progress && (
                <Progress percent={Math.round(jobStatus.progress.percentage)} status="active" />
              )}
            </Space>
          </Card>
        )}

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
                    icon={<BarChart3 size={14} />}
                    style={{ 
                      justifyContent: 'flex-start',
                      height: 'auto',
                      padding: '8px 12px'
                    }}
                  >
                    Overview
                  </Button>
                  {items.map((item, index) => {
                    const jobId = jobStatus ? `${jobStatus.jobId}-${index + 1}` : '';
                    const jobData = individualJobs.get(jobId);
                    
                    return (
                      <Button
                        key={index}
                        type={activeTab === String(index) ? 'primary' : 'text'}
                        size="small"
                        block
                        onClick={() => {
                          setActiveTab(String(index));
                          setTimeout(() => {
                            resultsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
                          }, 100);
                        }}
                        icon={jobData && getStatusIcon(jobData.status.status)}
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
                          {item.name}
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
              title={activeTab === 'overview' ? 'Batch Overview' : items[parseInt(activeTab)]?.name || 'Validation Result'}
              extra={activeTab === 'overview' && jobStatus?.status === 'COMPLETED' ? (
                <Space>
                  <Button 
                    icon={<Download size={16} />}
                    onClick={handleExportHTML}
                  >
                    Export HTML
                  </Button>
                  <Button 
                    type="primary" 
                    icon={<Download size={16} />}
                    onClick={handleExportAll}
                  >
                    Export ZIP
                  </Button>
                </Space>
              ) : null}
              style={{ scrollMarginTop: '24px' }}
            >
              {renderResults()}
            </Card>
          </div>
        )}
          </>
        ) : (
          <>
            <Alert
              message="YAML Configuration Editor"
              description="Edit the complete batch validation configuration in YAML format. Switch back to Form view to use the visual editor."
              type="info"
              showIcon
              style={{ marginBottom: 16 }}
            />
            
            <div style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
              <Button 
                icon={<Download size={16} />}
                onClick={() => {
                  const blob = new Blob([yamlConfig], { type: 'text/yaml' });
                  const url = window.URL.createObjectURL(blob);
                  const a = document.createElement('a');
                  a.href = url;
                  a.download = `batch-validation-config-${new Date().toISOString().split('T')[0]}.yaml`;
                  document.body.appendChild(a);
                  a.click();
                  window.URL.revokeObjectURL(url);
                  document.body.removeChild(a);
                  message.success('Configuration exported');
                }}
              >
                Export YAML
              </Button>
              <Upload
                accept=".yaml,.yml"
                beforeUpload={(file) => {
                  const reader = new FileReader();
                  reader.onload = (e) => {
                    const content = e.target?.result as string;
                    setYamlConfig(content);
                    message.success('YAML imported successfully');
                  };
                  reader.readAsText(file);
                  return false;
                }}
                showUploadList={false}
              >
                <Button icon={<UploadIcon size={16} />}>Import YAML</Button>
              </Upload>
            </div>

            <Input.TextArea
              value={yamlConfig}
              onChange={(e) => setYamlConfig(e.target.value)}
              rows={25}
              style={{ fontFamily: 'monospace', fontSize: 12 }}
              placeholder="YAML configuration will appear here..."
            />

            <div style={{ marginTop: 16 }}>
              <Button
                type="primary"
                size="large"
                block
                icon={<PlayCircle size={20} />}
                onClick={() => {
                  parseYamlToState(yamlConfig);
                  if (items.length > 0) {
                    handleRunBatch();
                  } else {
                    message.error('Please provide valid YAML configuration with validation items');
                  }
                }}
                loading={loading}
                style={{ height: 50, fontSize: 16, fontWeight: 600 }}
              >
                Run Batch Validation
              </Button>
            </div>
          </>
        )}
        </Space>
      </Card>

      {/* Full Configuration Modal */}
      <Modal
        title="Full Batch Configuration"
        open={fullConfigModalVisible}
        onCancel={() => setFullConfigModalVisible(false)}
        footer={null}
        width={800}
      >
        <FullValidationConfig 
          selectedNamespaces={items.flatMap(item => 
            item.namespaces.map(ns => {
              const parts = ns.split('/');
              return { cluster: parts[0], namespace: parts[1] || parts[0] };
            })
          )}
          ignoreFields={ignoreFields}
          onImport={(_data) => {
            message.info('Full config import for batch validation - feature coming soon');
            setFullConfigModalVisible(false);
          }}
        />
      </Modal>
      </div>
    </div>
  );
};
