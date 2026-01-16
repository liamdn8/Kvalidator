import { useState } from 'react';
import { 
  Card, 
  Button, 
  Radio, 
  Space, 
  Input, 
  Table, 
  Form, 
  App, 
  Spin,
  Alert,
  Progress,
  Popconfirm,
  Collapse,
  Modal,
  Upload,
  Tooltip
} from 'antd';
import { 
  PlayCircle, 
  Plus, 
  Trash2, 
  FileJson, 
  TableIcon,
  CheckCircle,
  Download,
  Settings,
  FileText,
  Upload as UploadIcon
} from 'lucide-react';
import type { CNFChecklistItem, CNFChecklistRequest } from '../types/cnf';
import type { ValidationJobResponse } from '../types';
import { validationApi } from '../services/api';
import { CnfChecklistResults } from '../components/CnfChecklistResults';
import { ValidationConfigEditor } from '../components/ValidationConfigEditor';
import { FullValidationConfig } from '../components/FullValidationConfig';

const { TextArea } = Input;

type InputMode = 'json' | 'table';

export const CNFChecklistPage = () => {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  
  const [inputMode, setInputMode] = useState<InputMode>('json');
  const [jsonInput, setJsonInput] = useState('');
  const [tableData, setTableData] = useState<CNFChecklistItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [jobStatus, setJobStatus] = useState<ValidationJobResponse | null>(null);
  const [completedJobId, setCompletedJobId] = useState<string | null>(null);
  const [ignoreFields, setIgnoreFields] = useState<string[]>([]);
  const [fullConfigModalVisible, setFullConfigModalVisible] = useState(false);
  const [configViewMode, setConfigViewMode] = useState<'ui' | 'yaml'>('ui');
  const [yamlConfig, setYamlConfig] = useState('');
  const [viewMode, setViewMode] = useState<'form' | 'yaml'>('form');
  const [matchingStrategy, setMatchingStrategy] = useState<'exact' | 'value' | 'identity'>('value');

  // Sample JSON template
  const sampleJson = `[
  {
    "vimName": "vim-hanoi",
    "namespace": "default",
    "kind": "Deployment",
    "objectName": "abm_01",
    "fieldKey": "spec.template.spec.containers[0].image",
    "manoValue": "harbor.local/vmano/webmano:1.2.3"
  },
  {
    "vimName": "vim-hanoi",
    "namespace": "default",
    "kind": "Deployment",
    "objectName": "abm_01",
    "fieldKey": "spec.template.spec.containers[1].terminationMessagePath",
    "manoValue": "/dev/termination-log"
  },
  {
    "vimName": "vim-hanoi",
    "namespace": "default",
    "kind": "ConfigMap",
    "objectName": "abm-config",
    "fieldKey": "data.ACTUAL_VERSION",
    "manoValue": "v1.0.0"
  },
  {
    "vimName": "vim-hcm",
    "namespace": "production",
    "kind": "Deployment",
    "objectName": "web-app",
    "fieldKey": "spec.replicas",
    "manoValue": "3"
  }
]`;

  const handleCellEdit = (index: number, field: keyof CNFChecklistItem, value: string) => {
    const newData = [...tableData];
    newData[index] = { ...newData[index], [field]: value };
    setTableData(newData);
  };

  const generateYamlFromState = () => {
    const config = {
      namespaces: tableData.map(item => `${item.vimName}/${item.namespace}`),
      ignoreFields: ignoreFields,
      cnfChecklist: tableData
    };
    return `# CNF Checklist Configuration
# Generated: ${new Date().toLocaleString()}
#
# Instructions:
# 1. Define CNF checklist items - these are the baseline values to compare against
# 2. Specify namespaces to validate (format: "vimName/namespace")
# 3. Configure ignore fields to exclude from comparison
# 4. Each checklist item needs:
#    - vimName: VIM/Cluster identifier
#    - namespace: Kubernetes namespace
#    - kind: Resource type (Pod, Deployment, Service, etc.)
#    - objectName: Name of the Kubernetes object
#    - fieldKey: JSON path to the field (e.g., spec.replicas)
#    - manoValue: Expected/baseline value

# CNF checklist items (baseline values)
cnfChecklist:
${tableData.map(item => `  - vimName: "${item.vimName}"
    namespace: "${item.namespace}"
    kind: "${item.kind}"
    objectName: "${item.objectName}"
    fieldKey: "${item.fieldKey}"
    manoValue: "${item.manoValue}"`).join('\n')}

# Namespaces to validate (extracted from checklist items)
namespaces:
${config.namespaces.map(ns => `  - "${ns}"`).join('\n')}

# Fields to ignore during comparison
ignoreFields:
${config.ignoreFields.map(f => `  - "${f}"`).join('\n')}`;
  };

  const parseYamlToState = (yaml: string) => {
    try {
      const lines = yaml.split('\n');
      const cnfItems: CNFChecklistItem[] = [];
      let currentItem: Partial<CNFChecklistItem> = {};
      
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('- vimName:')) {
          if (Object.keys(currentItem).length > 0) {
            cnfItems.push(currentItem as CNFChecklistItem);
          }
          currentItem = { vimName: trimmed.split('"')[1] };
        } else if (trimmed.startsWith('namespace:')) {
          currentItem.namespace = trimmed.split('"')[1];
        } else if (trimmed.startsWith('kind:')) {
          currentItem.kind = trimmed.split('"')[1];
        } else if (trimmed.startsWith('objectName:')) {
          currentItem.objectName = trimmed.split('"')[1];
        } else if (trimmed.startsWith('fieldKey:')) {
          currentItem.fieldKey = trimmed.split('"')[1];
        } else if (trimmed.startsWith('manoValue:')) {
          currentItem.manoValue = trimmed.split('"')[1];
        }
      }
      if (Object.keys(currentItem).length > 0) {
        cnfItems.push(currentItem as CNFChecklistItem);
      }
      
      return cnfItems;
    } catch (error) {
      console.error('Failed to parse YAML:', error);
      return [];
    }
  };

  const tableColumns = [
    {
      title: 'VIM Name',
      dataIndex: 'vimName',
      key: 'vimName',
      width: 120,
      render: (text: string, _: CNFChecklistItem, index: number) => (
        <Input 
          value={text} 
          onChange={(e) => handleCellEdit(index, 'vimName', e.target.value)}
          size="small"
        />
      ),
    },
    {
      title: 'Namespace',
      dataIndex: 'namespace',
      key: 'namespace',
      width: 120,
      render: (text: string, _: CNFChecklistItem, index: number) => (
        <Input 
          value={text} 
          onChange={(e) => handleCellEdit(index, 'namespace', e.target.value)}
          size="small"
        />
      ),
    },
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      width: 120,
      render: (text: string, _: CNFChecklistItem, index: number) => (
        <Input 
          value={text} 
          onChange={(e) => handleCellEdit(index, 'kind', e.target.value)}
          size="small"
        />
      ),
    },
    {
      title: 'Object Name',
      dataIndex: 'objectName',
      key: 'objectName',
      width: 150,
      render: (text: string, _: CNFChecklistItem, index: number) => (
        <Input 
          value={text} 
          onChange={(e) => handleCellEdit(index, 'objectName', e.target.value)}
          size="small"
        />
      ),
    },
    {
      title: 'Field Key',
      dataIndex: 'fieldKey',
      key: 'fieldKey',
      width: 250,
      render: (text: string, _: CNFChecklistItem, index: number) => (
        <Input 
          value={text} 
          onChange={(e) => handleCellEdit(index, 'fieldKey', e.target.value)}
          size="small"
        />
      ),
    },
    {
      title: 'Expected Value (MANO)',
      dataIndex: 'manoValue',
      key: 'manoValue',
      width: 200,
      render: (text: string, _: CNFChecklistItem, index: number) => (
        <Input 
          value={text} 
          onChange={(e) => handleCellEdit(index, 'manoValue', e.target.value)}
          size="small"
        />
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 80,
      render: (_: any, _record: CNFChecklistItem, index: number) => (
        <Popconfirm
          title="Delete this item?"
          onConfirm={() => handleDeleteRow(index)}
          okText="Yes"
          cancelText="No"
        >
          <Button type="link" danger icon={<Trash2 size={16} />} />
        </Popconfirm>
      ),
    },
  ];

  const handleAddRow = () => {
    const values = form.getFieldsValue();
    
    if (!values.vimName || !values.namespace || !values.kind || !values.objectName || !values.fieldKey || !values.manoValue) {
      message.error('Please fill all fields before adding');
      return;
    }

    const newItem: CNFChecklistItem = {
      vimName: values.vimName,
      namespace: values.namespace,
      kind: values.kind,
      objectName: values.objectName,
      fieldKey: values.fieldKey,
      manoValue: values.manoValue,
    };

    setTableData([...tableData, newItem]);
    form.resetFields();
    message.success('Item added');
  };

  const handleDeleteRow = (index: number) => {
    const newData = tableData.filter((_, i) => i !== index);
    setTableData(newData);
    message.success('Item deleted');
  };

  const handleLoadSample = () => {
    setJsonInput(sampleJson);
    message.success('Sample JSON loaded');
  };

  const parseJsonInput = (): CNFChecklistItem[] | null => {
    try {
      const parsed = JSON.parse(jsonInput);
      if (!Array.isArray(parsed)) {
        message.error('JSON must be an array of checklist items');
        return null;
      }
      
      // Validate structure
      for (const item of parsed) {
        if (!item.vimName || !item.namespace || !item.kind || !item.objectName || !item.fieldKey || !item.manoValue) {
          message.error('Each item must have: vimName, namespace, kind, objectName, fieldKey, manoValue');
          return null;
        }
      }
      
      return parsed;
    } catch (error) {
      message.error('Invalid JSON format');
      return null;
    }
  };

  const handleValidate = async () => {
    let items: CNFChecklistItem[];

    if (inputMode === 'json') {
      const parsed = parseJsonInput();
      if (!parsed) return;
      items = parsed;
    } else {
      if (tableData.length === 0) {
        message.error('Please add at least one checklist item');
        return;
      }
      items = tableData;
    }

    const request: CNFChecklistRequest = {
      items,
      description: `CNF Checklist Validation - ${new Date().toLocaleString()}`,
      matchingStrategy: matchingStrategy,
    };

    console.log('CNF Checklist Request:', request);
    console.log('Matching Strategy:', matchingStrategy);

    try {
      setLoading(true);
      setJobStatus(null);
      setCompletedJobId(null);

      // Submit validation job (now using batch validation backend)
      const jobResponse = await validationApi.submitCNFChecklistValidation(request);
      console.log('CNF Checklist job submitted (as batch):', jobResponse);
      
      setJobStatus(jobResponse);
      message.success(`CNF Checklist validation started: ${jobResponse.jobId}`);

      // Poll job status
      const completedJob = await validationApi.pollJobStatus(
        jobResponse.jobId,
        (job) => {
          console.log('Job status update:', job);
          setJobStatus(job);
        }
      );

      console.log('Job completed:', completedJob);
      setJobStatus(completedJob);

      if (completedJob.status === 'COMPLETED') {
        message.success('CNF Checklist validation completed!');
        console.log('Setting completedJobId to:', completedJob.jobId);
        setCompletedJobId(completedJob.jobId);
      } else if (completedJob.status === 'FAILED') {
        message.error(`Validation failed: ${completedJob.message || 'Unknown error'}`);
      }
    } catch (error: any) {
      console.error('Validation error:', error);
      message.error(error.message || 'Failed to validate CNF checklist');
    } finally {
      setLoading(false);
    }
  };

  const handleDownloadReport = () => {
    if (jobStatus?.downloadUrl) {
      window.open(jobStatus.downloadUrl, '_blank');
    }
  };

  const renderJsonInput = () => (
    <Card title="JSON Input" className="mb-4">
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <div>
          <Button 
            icon={<FileJson size={16} />} 
            onClick={handleLoadSample}
            style={{ marginBottom: 8 }}
          >
            Load Sample JSON
          </Button>
        </div>
        <TextArea
          value={jsonInput}
          onChange={(e) => setJsonInput(e.target.value)}
          placeholder="Paste your CNF checklist JSON here..."
          rows={15}
          style={{ fontFamily: 'monospace', fontSize: '13px' }}
        />
        <Alert
          message="JSON Format"
          description={
            <div>
              Each item should have:
              <ul style={{ marginBottom: 0, paddingLeft: 20 }}>
                <li><code>vimName</code>: VIM/Cluster site (e.g., vim-hanoi, vim-hcm)</li>
                <li><code>namespace</code>: Kubernetes namespace</li>
                <li><code>kind</code>: Resource kind (Deployment, ConfigMap, etc.)</li>
                <li><code>objectName</code>: Name of the Kubernetes object</li>
                <li><code>fieldKey</code>: Field path to validate</li>
                <li><code>manoValue</code>: Expected value from MANO/design</li>
              </ul>
            </div>
          }
          type="info"
          showIcon
        />
      </Space>
    </Card>
  );

  const renderTableInput = () => (
    <Card title="Table Input" className="mb-4">
      <Space direction="vertical" style={{ width: '100%' }} size="middle">
        <Card size="small" title="Add New Item" type="inner">
          <Form form={form} layout="inline" style={{ flexWrap: 'wrap', gap: '8px' }}>
            <Form.Item name="vimName" style={{ marginBottom: 8 }}>
              <Input placeholder="VIM Name" style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="namespace" style={{ marginBottom: 8 }}>
              <Input placeholder="Namespace" style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="kind" style={{ marginBottom: 8 }}>
              <Input placeholder="Kind" style={{ width: 120 }} />
            </Form.Item>
            <Form.Item name="objectName" style={{ marginBottom: 8 }}>
              <Input placeholder="Object Name" style={{ width: 150 }} />
            </Form.Item>
            <Form.Item name="fieldKey" style={{ marginBottom: 8 }}>
              <Input placeholder="Field Key" style={{ width: 250 }} />
            </Form.Item>
            <Form.Item name="manoValue" style={{ marginBottom: 8 }}>
              <Input placeholder="Expected Value" style={{ width: 200 }} />
            </Form.Item>
            <Form.Item style={{ marginBottom: 8 }}>
              <Button type="primary" icon={<Plus size={16} />} onClick={handleAddRow}>
                Add
              </Button>
            </Form.Item>
          </Form>
        </Card>

        <Table
          columns={tableColumns}
          dataSource={tableData}
          rowKey={(_, index) => index?.toString() || '0'}
          pagination={{ pageSize: 10 }}
          size="small"
          scroll={{ x: 'max-content' }}
          locale={{ emptyText: 'No items added yet. Use the form above to add items.' }}
        />
      </Space>
    </Card>
  );

  return (
    <div style={{ padding: '24px', maxWidth: '1400px', margin: '0 auto' }}>
      <Card 
        title="CNF Checklist Validation" 
        className="mb-4"
        extra={
          <Radio.Group 
            value={viewMode} 
            onChange={(e) => {
              const newMode = e.target.value;
              if (newMode === 'yaml' && viewMode === 'form') {
                const yaml = generateYamlFromState();
                setYamlConfig(yaml);
                message.success('Switched to YAML view');
              } else if (newMode === 'form' && viewMode === 'yaml') {
                const items = parseYamlToState(yamlConfig);
                if (items.length > 0) {
                  setTableData(items);
                }
                message.success('Switched to Form view');
              }
              setViewMode(newMode);
            }}
          >
            <Radio.Button value="form">
              <TableIcon size={14} style={{ marginRight: 6, verticalAlign: 'middle' }} />
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
                message="CNF Checklist Validation"
                description={
                  <div>
                    <p>Validate Kubernetes configurations against your CNF checklist.</p>
                    <p><strong>How it works:</strong></p>
                    <ul style={{ marginBottom: 0, paddingLeft: 20 }}>
                      <li>Input fields you specify become the <strong>baseline (expected values)</strong></li>
                      <li>System reads the same fields from <strong>runtime clusters</strong></li>
                      <li>Compares baseline vs actual values and highlights differences</li>
                      <li>Supports multiple VIM/Cluster sites in one validation</li>
                    </ul>
                  </div>
                }
                type="info"
                showIcon
              />

          <div>
            <div style={{ marginBottom: 16 }}>
              <strong>Input Mode:</strong>
            </div>
            <Radio.Group value={inputMode} onChange={(e) => {
              const newMode = e.target.value;
              
              // Convert data when switching modes
              if (newMode === 'table' && inputMode === 'json' && jsonInput.trim()) {
                // JSON → Table
                const parsed = parseJsonInput();
                if (parsed) {
                  setTableData(parsed);
                  message.success(`Loaded ${parsed.length} items from JSON to table`);
                }
              } else if (newMode === 'json' && inputMode === 'table' && tableData.length > 0) {
                // Table → JSON
                setJsonInput(JSON.stringify(tableData, null, 2));
                message.success(`Converted ${tableData.length} items from table to JSON`);
              }
              
              setInputMode(newMode);
            }}>
              <Radio.Button value="json">
                <FileJson size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
                JSON Paste
              </Radio.Button>
              <Radio.Button value="table">
                <TableIcon size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
                Table Input
              </Radio.Button>
            </Radio.Group>
          </div>

          {inputMode === 'json' ? renderJsonInput() : renderTableInput()}

          {/* Matching Strategy */}
          <Card title="Comparison Strategy" size="small" style={{ marginBottom: 16 }}>
            <Space direction="vertical" style={{ width: '100%' }} size="small">
              <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                <span style={{ fontWeight: 500 }}>How to match fields:</span>
                <Radio.Group
                  value={matchingStrategy}
                  onChange={(e) => setMatchingStrategy(e.target.value)}
                  buttonStyle="solid"
                >
                  <Radio.Button value="exact">
                    <Tooltip title="Exact index: containers[0] only matches containers[0] (V1 engine, fastest)">
                      Exact Index
                    </Tooltip>
                  </Radio.Button>
                  <Radio.Button value="value">
                    <Tooltip title="Value search: containers[1] searches all items for matching value (V1 flexible)">
                      Value Search
                    </Tooltip>
                  </Radio.Button>
                  <Radio.Button value="identity">
                    <Tooltip title="Identity match: containers[app] uses semantic/name-based matching (V2 engine)">
                      Identity Match
                    </Tooltip>
                  </Radio.Button>
                </Radio.Group>
              </div>
              <Alert
                message={
                  matchingStrategy === 'exact' 
                    ? '📌 Exact: Fastest, strict index matching'
                    : matchingStrategy === 'value'
                    ? '🔍 Value: Flexible search across list items'
                    : '🎯 Identity: Semantic comparison (order-independent)'
                }
                type="info"
                showIcon={false}
                style={{ padding: '6px 12px', fontSize: '12px' }}
              />
            </Space>
          </Card>

          {/* Validate Button for Form/Table mode */}
          <div style={{ display: 'flex', gap: '12px', marginBottom: 16 }}>
            <Button
              type="primary"
              size="large"
              icon={<PlayCircle size={20} />}
              onClick={handleValidate}
              loading={loading}
              disabled={loading || (inputMode === 'table' && tableData.length === 0) || (inputMode === 'json' && !jsonInput.trim())}
            >
              Start Validation
            </Button>
            {jobStatus?.downloadUrl && (
              <Button
                type="default"
                size="large"
                icon={<Download size={20} />}
                onClick={handleDownloadReport}
              >
                Download Excel Report
              </Button>
            )}
          </div>

          <div>
            <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <strong>Configuration View:</strong>
              <Radio.Group 
                value={configViewMode} 
                onChange={(e) => {
                  const newMode = e.target.value;
                  
                  // Convert data when switching modes
                  if (newMode === 'yaml' && configViewMode === 'ui') {
                    // UI → YAML
                    const yaml = generateYamlFromState();
                    setYamlConfig(yaml);
                    message.success('Converted to YAML view');
                  } else if (newMode === 'ui' && configViewMode === 'yaml') {
                    // YAML → UI
                    const items = parseYamlToState(yamlConfig);
                    if (items.length > 0) {
                      setTableData(items);
                      message.success(`Loaded ${items.length} items from YAML`);
                    }
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
              <div style={{ marginBottom: 24 }}>
                <Collapse
                  items={[{
                    key: 'config',
                    label: (
                      <span>
                        <Settings size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
                        Ignore Rules Configuration
                      </span>
                    ),
                    children: (
                      <ValidationConfigEditor 
                        showTitle={false}
                        onFieldsChange={(fields) => setIgnoreFields(fields)}
                      />
                    )
                  }]}
                />
              </div>
            ) : (
              <Card title="YAML Configuration" size="small" style={{ marginBottom: 24 }}>
                <Alert
                  message="YAML Configuration Editor"
                  description="Edit the complete configuration in YAML format. Changes will be applied when switching back to UI view."
                  type="info"
                  showIcon
                  style={{ marginBottom: 16 }}
                />
                <TextArea
                  value={yamlConfig}
                  onChange={(e) => setYamlConfig(e.target.value)}
                  rows={20}
                  style={{ fontFamily: 'monospace', fontSize: 12 }}
                  placeholder="YAML configuration will appear here..."
                />
              </Card>
            )}
          </div>
            </>
          ) : (
            <>
              <Alert
                message="YAML Configuration Editor"
                description="Edit the complete CNF checklist configuration in YAML format. Switch back to Form view to use the visual editor."
                type="info"
                showIcon
              />
              
              <div style={{ marginBottom: 16, display: 'flex', gap: 8 }}>
                <Button 
                  icon={<Download size={16} />}
                  onClick={() => {
                    const blob = new Blob([yamlConfig], { type: 'text/yaml' });
                    const url = window.URL.createObjectURL(blob);
                    const a = document.createElement('a');
                    a.href = url;
                    a.download = `cnf-checklist-config-${new Date().toISOString().split('T')[0]}.yaml`;
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

              <div style={{ display: 'flex', gap: '12px', marginTop: 16 }}>
                <Button
                  type="primary"
                  size="large"
                  icon={<PlayCircle size={20} />}
                  onClick={() => {
                    const items = parseYamlToState(yamlConfig);
                    if (items.length > 0) {
                      setTableData(items);
                      handleValidate();
                    } else {
                      message.error('Please provide valid YAML configuration');
                    }
                  }}
                  loading={loading}
                  disabled={loading}
                >
                  Start Validation
                </Button>
                {jobStatus?.downloadUrl && (
                  <Button
                    type="default"
                    size="large"
                    icon={<Download size={20} />}
                    onClick={handleDownloadReport}
                  >
                    Download Excel Report
                  </Button>
                )}
              </div>
            </>
          )}
        </Space>
      </Card>

      {/* Job Status */}
      {jobStatus && (
        <Card 
          title="Validation Status" 
          className="mb-4"
          extra={
            jobStatus.status === 'COMPLETED' ? (
              <CheckCircle size={20} color="green" />
            ) : null
          }
        >
          <Space direction="vertical" style={{ width: '100%' }}>
            <div>
              <strong>Job ID:</strong> {jobStatus.jobId}
            </div>
            <div>
              <strong>Status:</strong> {jobStatus.status}
            </div>
            {jobStatus.progress && (
              <>
                <div>
                  <strong>Progress:</strong> {jobStatus.progress.currentStep}
                </div>
                <Progress percent={jobStatus.progress.percentage} status="active" />
              </>
            )}
            {jobStatus.status === 'COMPLETED' && (
              <Alert 
                message="Validation completed! View results below." 
                type="success"
                icon={<CheckCircle size={16} />}
                showIcon
              />
            )}
            {jobStatus.message && jobStatus.status === 'FAILED' && (
              <Alert message={jobStatus.message} type="error" />
            )}
          </Space>
        </Card>
      )}

      {/* Loading indicator */}
      {loading && !jobStatus && (
        <Card>
          <div style={{ textAlign: 'center', padding: '40px' }}>
            <Spin size="large" />
            <p style={{ marginTop: '16px' }}>Submitting CNF Checklist validation...</p>
          </div>
        </Card>
      )}

      {/* Results */}
      {completedJobId && (
        <div>
          <div style={{padding: '10px', background: '#f0f0f0', margin: '10px 0'}}>
            <strong>Debug:</strong> Showing results for job ID: {completedJobId}
          </div>
          <CnfChecklistResults jobId={completedJobId} />
        </div>
      )}
      {!completedJobId && jobStatus?.status === 'COMPLETED' && (
        <Alert 
          message="Job completed but no results to show. This might be a loading issue." 
          type="warning" 
        />
      )}

      {/* Full Configuration Modal */}
      <Modal
        title="Full CNF Configuration"
        open={fullConfigModalVisible}
        onCancel={() => setFullConfigModalVisible(false)}
        footer={null}
        width={800}
      >
        <FullValidationConfig
          selectedNamespaces={tableData.map(item => ({
            namespace: item.namespace,
            cluster: item.vimName
          }))}
          ignoreFields={ignoreFields}
          onImport={(data) => {
            if (data.namespaces && data.namespaces.length > 0) {
              const cnfItems: CNFChecklistItem[] = data.namespaces.map((ns: string, index: number) => {
                const [cluster, namespace] = ns.split('/');
                return {
                  vimName: cluster || 'default',
                  namespace: namespace || ns,
                  kind: 'Deployment',
                  objectName: `object-${index}`,
                  fieldKey: 'spec.replicas',
                  manoValue: '1'
                };
              });
              setTableData(cnfItems);
              message.success(`Imported ${cnfItems.length} CNF checklist items`);
            }
            if (data.ignoreFields) {
              setIgnoreFields(data.ignoreFields);
            }
            setFullConfigModalVisible(false);
          }}
        />
      </Modal>
    </div>
  );
};
