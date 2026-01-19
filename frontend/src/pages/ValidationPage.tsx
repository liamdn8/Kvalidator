import { useState } from 'react';
import { Card, Button, Spin, App, Alert, Progress, Collapse, Modal, Radio, Input, Upload, Space } from 'antd';
import { PlayCircle, Settings, FileText, Download, Upload as UploadIcon } from 'lucide-react';
import { NamespaceSearch } from '../components/NamespaceSearch';
import { BaselineSelector } from '../components/BaselineSelector';
import { ValidationResults } from '../components/ValidationResults';
import { ValidationConfigEditor } from '../components/ValidationConfigEditor';
import { FullValidationConfig } from '../components/FullValidationConfig';
import { validationApi } from '../services/api';
import type { ClusterNamespace, ValidationRequest, ValidationResultJson, ValidationJobResponse } from '../types';

// Default ignore fields (fallback if API config fails to load)
const DEFAULT_IGNORE_FIELDS = [
  // 'metadata.creationTimestamp',
  // 'metadata.generation',
  // 'metadata.resourceVersion',
  // 'metadata.uid',
  // 'metadata.selfLink',
  // 'metadata.managedFields',
  'metadata.namespace',
  // 'metadata.annotations',
  'status',
  // 'spec.template.metadata.creationTimestamp',
  // 'spec.clusterIP',
  // 'spec.clusterIPs',
  // 'spec.ipFamilies',
  // 'spec.ipFamilyPolicy',
  // 'spec.template.spec.nodeName',
  // 'spec.template.spec.restartPolicy',
  // 'spec.template.spec.dnsPolicy',
  // 'spec.template.spec.schedulerName',
  // 'spec.template.spec.securityContext',
  // 'spec.template.spec.enableServiceLinks',
];


export const ValidationPage = () => {
  const { message } = App.useApp();
  const [selectedNamespaces, setSelectedNamespaces] = useState<ClusterNamespace[]>([]);
  const [ignoreFields, setIgnoreFields] = useState<string[]>(DEFAULT_IGNORE_FIELDS);
  const [baseline, setBaseline] = useState<{
    type: 'yaml' | 'namespace';
    yamlContent?: string;
    selectedNamespaceIndex?: number;
  }>({ type: 'yaml' });
  const [result, setResult] = useState<ValidationResultJson | null>(null);
  const [loading, setLoading] = useState(false);
  const [jobStatus, setJobStatus] = useState<ValidationJobResponse | null>(null);
  const [fullConfigModalVisible, setFullConfigModalVisible] = useState(false);
  const [configViewMode, setConfigViewMode] = useState<'ui' | 'yaml'>('ui');
  const [yamlConfig, setYamlConfig] = useState('');
  const [viewMode, setViewMode] = useState<'form' | 'yaml'>('form');

  const handleImportFullConfig = (config: { namespaces: string[]; ignoreFields: string[] }) => {
    // Parse namespaces from "cluster/namespace" format
    const namespaces: ClusterNamespace[] = config.namespaces.map(ns => {
      const parts = ns.split('/');
      return {
        cluster: parts[0],
        namespace: parts[1] || parts[0], // fallback if no cluster specified
      };
    });
    
    setSelectedNamespaces(namespaces);
    message.success(`Imported configuration with ${namespaces.length} namespaces`);
  };

  const generateYamlFromState = () => {
    const config = {
      namespaces: selectedNamespaces.map(ns => `${ns.cluster}/${ns.namespace}`),
      ignoreFields: ignoreFields,
      baseline: baseline
    };
    
    let yamlString = `# Single Validation Configuration
# Generated: ${new Date().toLocaleString()}
#
# Instructions:
# 1. Define namespaces to validate (format: "cluster/namespace")
# 2. Configure baseline for comparison (can be YAML content or reference namespace)
# 3. Set ignore fields to exclude from comparison
# 4. Baseline options:
#    - type: "yaml" - Compare against provided YAML content
#    - type: "namespace" - Compare against a reference namespace (index-based)

# Namespaces to validate

namespaces:
${config.namespaces.map(ns => `  - "${ns}"`).join('\n')}
#   - "cluster1/namespaceA"
#   - "cluster2/namespaceB"

# Fields to ignore during comparison

ignoreFields:
${config.ignoreFields.map(f => `  - "${f}"`).join('\n')}

# Baseline configuration for comparison
baseline:
  type: "${baseline.type}"`;

    if (baseline.type === 'yaml' && baseline.yamlContent) {
      yamlString += `\n  yamlContent: |\n${baseline.yamlContent.split('\n').map(line => `    ${line}`).join('\n')}`;
    } else if (baseline.type === 'namespace' && baseline.selectedNamespaceIndex !== undefined) {
      yamlString += `\n  selectedNamespaceIndex: ${baseline.selectedNamespaceIndex}`;
    }
    
    return yamlString;
  };

  const parseYamlToState = (yaml: string) => {
    try {
      const lines = yaml.split('\n');
      const namespaces: string[] = [];
      const ignoreFieldsList: string[] = [];
      let inNamespaces = false;
      let inIgnoreFields = false;
      
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('namespaces:')) {
          inNamespaces = true;
          inIgnoreFields = false;
        } else if (trimmed.startsWith('ignoreFields:')) {
          inNamespaces = false;
          inIgnoreFields = true;
        } else if (trimmed.startsWith('baseline:')) {
          inNamespaces = false;
          inIgnoreFields = false;
        } else if (trimmed.startsWith('- "') && inNamespaces) {
          namespaces.push(trimmed.split('"')[1]);
        } else if (trimmed.startsWith('- "') && inIgnoreFields) {
          ignoreFieldsList.push(trimmed.split('"')[1]);
        }
      }
      
      const parsedNamespaces: ClusterNamespace[] = namespaces.map(ns => {
        const parts = ns.split('/');
        return { cluster: parts[0], namespace: parts[1] || parts[0] };
      });
      
      setSelectedNamespaces(parsedNamespaces);
      setIgnoreFields(ignoreFieldsList);
      message.success(`Loaded ${parsedNamespaces.length} namespaces and ${ignoreFieldsList.length} ignore fields from YAML`);
    } catch (error) {
      console.error('Failed to parse YAML:', error);
      message.error('Failed to parse YAML configuration');
    }
  };

  const handleValidate = async () => {
    console.log('=== Validation Debug ===');
    console.log('Selected Namespaces:', selectedNamespaces);
    console.log('Baseline:', baseline);
    console.log('Ignore Fields:', ignoreFields);
    console.log('Ignore Fields Count:', ignoreFields.length);

    // Validation
    if (selectedNamespaces.length === 0) {
      message.error('Please select at least one namespace');
      return;
    }

    if (baseline.type === 'yaml' && !baseline.yamlContent?.trim()) {
      message.error('Please provide YAML content or switch to namespace baseline');
      return;
    }

    if (baseline.type === 'namespace' && baseline.selectedNamespaceIndex === undefined) {
      message.error('Please select a baseline namespace');
      return;
    }

    // Build request
    const request: ValidationRequest = {
      namespaces: [],
      exportExcel: true,
      description: `Validation: ${new Date().toLocaleString()}`,
      ignoreFields: ignoreFields.length > 0 ? ignoreFields : undefined, // Include ignore fields
    };

    if (baseline.type === 'yaml') {
      // YAML baseline mode: all selected namespaces are targets
      request.baselineYamlContent = baseline.yamlContent;
      request.namespaces = selectedNamespaces.map(ns => `${ns.cluster}/${ns.namespace}`);
      
      if (request.namespaces.length === 0) {
        message.error('Please select at least one namespace to compare with YAML baseline');
        return;
      }
    } else {
      // Namespace baseline mode: move selected namespace to position 0
      const baselineNs = selectedNamespaces[baseline.selectedNamespaceIndex!];
      const otherNamespaces = selectedNamespaces.filter((_, idx) => idx !== baseline.selectedNamespaceIndex);
      
      // Baseline namespace goes first, then others
      request.namespaces = [
        `${baselineNs.cluster}/${baselineNs.namespace}`,
        ...otherNamespaces.map(ns => `${ns.cluster}/${ns.namespace}`)
      ];

      if (request.namespaces.length < 2) {
        message.error('Please select at least 2 namespaces (1 baseline + 1 target) for comparison');
        return;
      }
    }

    console.log('Request payload:', request);

    try {
      setLoading(true);
      setResult(null);
      setJobStatus(null);

      // Step 1: Submit validation job
      console.log('Submitting validation job...');
      const jobResponse = await validationApi.submitValidation(request);
      console.log('Job submitted:', jobResponse);
      
      setJobStatus(jobResponse);
      message.success(`Validation job started: ${jobResponse.jobId}`);

      // Step 2: Poll job status until completed
      console.log('Polling job status...');
      const completedJob = await validationApi.pollJobStatus(
        jobResponse.jobId,
        (job) => {
          console.log('Job status update:', job);
          setJobStatus(job);
        }
      );

      console.log('Job completed:', completedJob);

      // Step 3: Check if job succeeded
      if (completedJob.status === 'FAILED') {
        message.error(`Validation failed: ${completedJob.message || 'Unknown error'}`);
        setLoading(false);
        return;
      }

      // Step 4: Fetch validation results
      console.log('Fetching validation results...');
      const validationResults = await validationApi.getValidationResults(completedJob.jobId);
      console.log('Results received:', validationResults);
      console.log('Summary:', validationResults.summary);
      console.log('Comparisons:', validationResults.comparisons);
      console.log('Comparisons type:', typeof validationResults.comparisons);
      console.log('Comparisons keys:', validationResults.comparisons ? Object.keys(validationResults.comparisons) : 'null');
      
      setResult(validationResults);
      message.success('Validation completed successfully');
      
    } catch (error: any) {
      console.error('Validation error:', error);
      const errorMsg = error.response?.data?.error || error.message || 'Validation failed';
      message.error(errorMsg);
    } finally {
      setLoading(false);
      setJobStatus(null);
    }
  };

  return (
    <div style={{ padding: '24px 50px' }}>
      <div style={{ maxWidth: 1200, margin: '0 auto' }}>
        <Card
          title="Resource Validation"
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
                message="Resource Validation"
                description={
                  <div>
                    <p>Search and select Kubernetes namespaces to compare, then choose a baseline.</p>
                    <p><strong>How it works:</strong></p>
                    <ol style={{ marginBottom: 0, paddingLeft: 20 }}>
                      <li><strong>Search Namespaces:</strong> Find namespaces across all clusters and add them to your selection</li>
                      <li><strong>Select Baseline:</strong> Choose YAML content or pick one namespace as baseline</li>
                      <li><strong>Validate:</strong> Compare all selected namespaces against the baseline</li>
                    </ol>
                  </div>
                }
                type="info"
                showIcon
              />

        <Card title="1. Search & Select Namespaces">
            <NamespaceSearch 
              selectedNamespaces={selectedNamespaces}
              onNamespacesChange={setSelectedNamespaces}
            />
          </Card>

          <div>
            <BaselineSelector 
              selectedNamespaces={selectedNamespaces}
              onBaselineChange={setBaseline}
            />
          </div>

          <div>
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
                  description="Edit the complete validation configuration in YAML format. Changes will be applied when switching back to UI view."
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

          <div>
            <Button
              type="primary"
              size="large"
              block
              icon={<PlayCircle size={20} />}
              onClick={handleValidate}
              loading={loading}
              disabled={selectedNamespaces.length === 0}
              style={{ height: 50, fontSize: 16, fontWeight: 600 }}
            >
              {selectedNamespaces.length === 0 
                ? 'Select namespaces to start'
                : `Start Validation (${selectedNamespaces.length} namespace${selectedNamespaces.length > 1 ? 's' : ''})`
              }
            </Button>
          </div>
            </>
          ) : (
            <>
              <Alert
                message="YAML Configuration Editor"
                description="Edit the complete validation configuration in YAML format. Switch back to Form view to use the visual editor."
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
                    a.download = `validation-config-${new Date().toISOString().split('T')[0]}.yaml`;
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
                    handleValidate();
                  }}
                  loading={loading}
                  style={{ height: 50, fontSize: 16, fontWeight: 600 }}
                >
                  Start Validation
                </Button>
              </div>
            </>
          )}

          {loading && jobStatus && (
            <Card style={{ marginBottom: 24 }}>
              <div style={{ textAlign: 'center', padding: '20px 0' }}>
                <Spin size="large" />
                <div style={{ marginTop: 16 }}>
                  <h3>Validation in Progress</h3>
                  <p style={{ color: '#64748b' }}>Job ID: {jobStatus.jobId}</p>
                  <p style={{ color: '#64748b', marginBottom: 16 }}>
                    Status: <strong>{jobStatus.status}</strong>
                  </p>
                  {jobStatus.progress && (
                    <>
                      <Progress 
                        percent={Math.round(jobStatus.progress.percentage)} 
                        status="active"
                        style={{ maxWidth: 400, margin: '0 auto 12px' }}
                      />
                      <p style={{ color: '#64748b', fontSize: 13 }}>
                        {jobStatus.progress.currentStep}
                      </p>
                    </>
                  )}
                </div>
              </div>
            </Card>
          )}

          {!loading && result && <ValidationResults result={result} />}
          </Space>
        </Card>

        {/* Full Configuration Modal */}
        <Modal
          title="Full Validation Configuration"
          open={fullConfigModalVisible}
          onCancel={() => setFullConfigModalVisible(false)}
          footer={null}
          width={800}
        >
          <FullValidationConfig 
            selectedNamespaces={selectedNamespaces}
            ignoreFields={ignoreFields}
            onImport={(config) => {
              handleImportFullConfig(config);
              setFullConfigModalVisible(false);
            }}
          />
        </Modal>
      </div>
    </div>
  );
};
