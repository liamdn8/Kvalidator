import { useState, useEffect } from 'react';
import { Card, Button, Input, Space, message, Upload, Tag, Popconfirm, Radio, Table, Alert, Divider } from 'antd';
import { Plus, Trash2, Download, Upload as UploadIcon, RotateCcw, FileCode, TableIcon } from 'lucide-react';

const { TextArea } = Input;

interface ValidationConfig {
  ignoreFields: string[];
}

interface ValidationConfigEditorProps {
  onConfigChange?: (config: ValidationConfig) => void;
  showTitle?: boolean;
  onFieldsChange?: (fields: string[]) => void;
}

// Default ignore fields from validation-config.yaml
const DEFAULT_IGNORE_FIELDS = [
  'metadata.creationTimestamp',
  'metadata.generation',
  'metadata.resourceVersion',
  'metadata.uid',
  'metadata.selfLink',
  'metadata.managedFields',
  'metadata.namespace',
  'metadata.annotations',
  'status',
  'spec.template.metadata.creationTimestamp',
  'spec.clusterIP',
  'spec.clusterIPs',
  'spec.ipFamilies',
  'spec.ipFamilyPolicy',
  'spec.template.spec.nodeName',
  'spec.template.spec.restartPolicy',
  'spec.template.spec.dnsPolicy',
  'spec.template.spec.schedulerName',
  'spec.template.spec.securityContext',
  'spec.template.spec.enableServiceLinks',
];

type ViewMode = 'table' | 'yaml';

export const ValidationConfigEditor = ({ onConfigChange, showTitle = true, onFieldsChange }: ValidationConfigEditorProps) => {
  const [config, setConfig] = useState<ValidationConfig>({ ignoreFields: [] });
  const [newField, setNewField] = useState('');
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>('table');
  const [yamlContent, setYamlContent] = useState('');

  useEffect(() => {
    loadConfig();
  }, []);

  useEffect(() => {
    // Update YAML content when config changes
    const yamlText = `ignoreFields:\n${config.ignoreFields.sort().map(f => `  - "${f}"`).join('\n')}`;
    setYamlContent(yamlText);
    onFieldsChange?.(config.ignoreFields);
  }, [config, onFieldsChange]);

  // Compute custom fields (fields not in DEFAULT_IGNORE_FIELDS)
  const customFields = config.ignoreFields.filter(f => !DEFAULT_IGNORE_FIELDS.includes(f));

  const loadConfig = async () => {
    try {
      setLoading(true);
      const response = await fetch('/kvalidator/api/config');
      if (!response.ok) throw new Error('Failed to load config');
      
      const data = await response.json();
      setConfig(data);
      onConfigChange?.(data);
    } catch (error) {
      console.error('Failed to load config:', error);
      message.error('Failed to load validation config');
    } finally {
      setLoading(false);
    }
  };

  const addIgnoreField = async () => {
    const fieldPath = newField.trim();
    if (!fieldPath) {
      message.warning('Please enter a field path');
      return;
    }

    if (config.ignoreFields.includes(fieldPath)) {
      message.warning('This field is already in the ignore list');
      return;
    }

    try {
      setLoading(true);
      const response = await fetch('/kvalidator/api/config/ignore-field', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ fieldPath }),
      });

      if (!response.ok) throw new Error('Failed to add field');

      const updatedConfig = {
        ...config,
        ignoreFields: [...config.ignoreFields, fieldPath].sort(),
      };
      setConfig(updatedConfig);
      onConfigChange?.(updatedConfig);
      setNewField('');
      message.success(`Added ignore field: ${fieldPath}`);
    } catch (error) {
      console.error('Failed to add field:', error);
      message.error('Failed to add ignore field');
    } finally {
      setLoading(false);
    }
  };

  const removeIgnoreField = async (fieldPath: string) => {
    try {
      setLoading(true);
      const response = await fetch(`/kvalidator/api/config/ignore-field?fieldPath=${encodeURIComponent(fieldPath)}`, {
        method: 'DELETE',
      });

      if (!response.ok) throw new Error('Failed to remove field');

      const updatedConfig = {
        ...config,
        ignoreFields: config.ignoreFields.filter(f => f !== fieldPath),
      };
      setConfig(updatedConfig);
      onConfigChange?.(updatedConfig);
      message.success(`Removed ignore field: ${fieldPath}`);
    } catch (error) {
      console.error('Failed to remove field:', error);
      message.error('Failed to remove ignore field');
    } finally {
      setLoading(false);
    }
  };

  const exportConfig = async () => {
    try {
      const response = await fetch('/kvalidator/api/config/export');
      if (!response.ok) throw new Error('Failed to export config');

      const yamlContent = await response.text();
      const blob = new Blob([yamlContent], { type: 'application/x-yaml' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'validation-config.yaml';
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      
      message.success('Config exported successfully');
    } catch (error) {
      console.error('Failed to export config:', error);
      message.error('Failed to export config');
    }
  };

  const importConfig = async (file: File) => {
    try {
      setLoading(true);
      const yamlContent = await file.text();
      
      const response = await fetch('/kvalidator/api/config/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-yaml' },
        body: yamlContent,
      });

      if (!response.ok) {
        const error = await response.json();
        throw new Error(error.error || 'Failed to import config');
      }

      await loadConfig();
      message.success('Config imported successfully');
    } catch (error: any) {
      console.error('Failed to import config:', error);
      message.error(`Failed to import config: ${error.message}`);
    } finally {
      setLoading(false);
    }
    
    return false; // Prevent default upload behavior
  };

  const resetToDefault = async () => {
    try {
      setLoading(true);
      const response = await fetch('/kvalidator/api/config/reset', {
        method: 'POST',
      });

      if (!response.ok) throw new Error('Failed to reset config');

      await loadConfig();
      message.success('Config reset to default');
    } catch (error) {
      console.error('Failed to reset config:', error);
      message.error('Failed to reset config');
    } finally {
      setLoading(false);
    }
  };

  const commonIgnoreFields = [
    { value: 'spec.replicas', description: 'Replica count' },
    { value: 'spec.template.spec.containers[0].image', description: 'Container image' },
    { value: 'spec.template.spec.containers[0].resources', description: 'Resource limits' },
    { value: 'metadata.labels', description: 'All labels (prefix match)' },
    { value: 'spec.template.spec.serviceAccountName', description: 'Service account' },
  ];

  const defaultTableColumns = [
    {
      title: 'Field Path',
      dataIndex: 'field',
      key: 'field',
      render: (text: string) => (
        <span style={{ fontFamily: 'monospace', fontSize: 12, color: '#666' }}>{text}</span>
      ),
    },
    {
      title: 'Type',
      key: 'type',
      width: 100,
      render: () => <Tag color="blue">Default</Tag>,
    },
  ];

  const customTableColumns = [
    {
      title: 'Field Path',
      dataIndex: 'field',
      key: 'field',
      render: (text: string) => (
        <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{text}</span>
      ),
    },
    {
      title: 'Type',
      key: 'type',
      width: 100,
      render: () => <Tag color="green">Custom</Tag>,
    },
    {
      title: 'Action',
      key: 'action',
      width: 80,
      render: (_: any, record: any) => (
        <Popconfirm
          title="Remove this ignore rule?"
          onConfirm={() => removeIgnoreField(record.field)}
          okText="Remove"
          cancelText="Cancel"
        >
          <Button 
            type="text" 
            danger 
            size="small"
            icon={<Trash2 size={14} />}
            disabled={loading}
          />
        </Popconfirm>
      ),
    },
  ];

  const renderTableView = () => (
    <div>
      {/* Default Rules (Read-only) */}
      <div style={{ marginBottom: 24 }}>
        <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 500 }}>Default Rules ({DEFAULT_IGNORE_FIELDS.length})</span>
          <Tag color="blue">Read-only</Tag>
        </div>
        <Table
          size="small"
          dataSource={DEFAULT_IGNORE_FIELDS.sort().map(field => ({ field }))}
          columns={defaultTableColumns}
          pagination={{ pageSize: 10, size: 'small' }}
          rowKey="field"
        />
      </div>

      {/* Custom Rules (Editable) */}
      <div>
        <div style={{ marginBottom: 12, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontWeight: 500 }}>Custom Rules ({customFields.length})</span>
          <Tag color="green">Editable</Tag>
        </div>
        
        {/* Add new field */}
        <Space.Compact style={{ width: '100%', marginBottom: 12 }}>
          <Input
            placeholder="e.g., spec.replicas, metadata.labels.app"
            value={newField}
            onChange={(e) => setNewField(e.target.value)}
            onPressEnter={addIgnoreField}
            disabled={loading}
          />
          <Button 
            type="primary" 
            icon={<Plus size={16} />}
            onClick={addIgnoreField}
            loading={loading}
          >
            Add
          </Button>
        </Space.Compact>

        <Divider orientation={'left' as any} plain style={{ marginTop: 12, marginBottom: 12, fontSize: 12 }}>
          Quick Add (click to add)
        </Divider>
        <Space wrap style={{ marginBottom: 16 }}>
          {commonIgnoreFields.map((field) => (
            <Tag
              key={field.value}
              color={config.ignoreFields.includes(field.value) ? 'success' : 'default'}
              style={{ cursor: 'pointer' }}
              onClick={() => {
                if (!config.ignoreFields.includes(field.value)) {
                  setNewField(field.value);
                }
              }}
            >
              {field.value}
            </Tag>
          ))}
        </Space>

        <Table
          size="small"
          dataSource={customFields.map(field => ({ field }))}
          columns={customTableColumns}
          pagination={false}
          rowKey="field"
          locale={{ emptyText: 'No custom rules. Add some rules above.' }}
        />
      </div>
    </div>
  );

  const renderYamlView = () => (
    <div>
      <Alert
        message="YAML Preview"
        description="This is a read-only preview. Use Table view to edit or Import to load from file."
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />
      <TextArea
        value={yamlContent}
        readOnly
        rows={20}
        style={{ fontFamily: 'monospace', fontSize: 12 }}
      />
    </div>
  );

  return (
    <Card 
      title={showTitle ? "Validation Configuration - Ignore Rules" : null}
      extra={
        <Space>
          <Button 
            icon={<Download size={16} />} 
            onClick={exportConfig}
            size="small"
          >
            Export
          </Button>
          <Upload
            accept=".yaml,.yml"
            beforeUpload={importConfig}
            showUploadList={false}
            maxCount={1}
          >
            <Button icon={<UploadIcon size={16} />} size="small">
              Import
            </Button>
          </Upload>
          <Popconfirm
            title="Reset to default config?"
            description="This will restore all default ignore rules. Your custom rules will be lost."
            onConfirm={resetToDefault}
            okText="Reset"
            cancelText="Cancel"
          >
            <Button icon={<RotateCcw size={16} />} size="small" danger>
              Reset
            </Button>
          </Popconfirm>
        </Space>
      }
      size="small"
    >
      <Alert
        message="Field Filtering"
        description={
          <div>
            <p style={{ margin: '0 0 8px 0' }}>
              These fields will be ignored during comparison. Use prefix matching (e.g., 'metadata.annotations' matches all annotation fields).
            </p>
            <ul style={{ margin: 0, paddingLeft: 20 }}>
              <li><strong>Default Rules:</strong> Pre-configured fields that are commonly ignored (read-only)</li>
              <li><strong>Custom Rules:</strong> Your additional ignore rules (editable)</li>
            </ul>
          </div>
        }
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      {/* View Mode Selector */}
      <div style={{ marginBottom: 16 }}>
        <Radio.Group value={viewMode} onChange={(e) => setViewMode(e.target.value)}>
          <Space>
            <Radio.Button value="table">
              <TableIcon size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
              Table View
            </Radio.Button>
            <Radio.Button value="yaml">
              <FileCode size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
              YAML View
            </Radio.Button>
          </Space>
        </Radio.Group>
        <span style={{ marginLeft: 16, color: '#999', fontSize: 12 }}>
          Total: {config.ignoreFields.length} rules ({DEFAULT_IGNORE_FIELDS.length} default + {customFields.length} custom)
        </span>
      </div>

      {viewMode === 'table' ? renderTableView() : renderYamlView()}
    </Card>
  );
};
