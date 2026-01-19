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
  // 'metadata.creationTimestamp.*',
  // 'metadata.generation.*',
  // 'metadata.resourceVersion.*',
  // 'metadata.uid.*',
  // 'metadata.selfLink.*',
  // 'metadata.managedFields.*',
  'metadata.namespace',
  // 'metadata.annotations.*',
  'status',
  // 'spec.template.metadata.creationTimestamp.*',
  // 'spec.clusterIP.*',
  // 'spec.clusterIPs.*',
  // 'spec.ipFamilies.*',
  // 'spec.ipFamilyPolicy.*',
  // 'spec.template.spec.nodeName.*',
  // 'spec.template.spec.restartPolicy.*',
  // 'spec.template.spec.dnsPolicy.*',
  // 'spec.template.spec.schedulerName.*',
  // 'spec.template.spec.securityContext.*',
  // 'spec.template.spec.enableServiceLinks.*',
];

type ViewMode = 'table' | 'yaml';

export const ValidationConfigEditor = ({ onConfigChange, showTitle = true, onFieldsChange }: ValidationConfigEditorProps) => {
  const [config, setConfig] = useState<ValidationConfig>({ ignoreFields: [] });
  const [newField, setNewField] = useState('');
  const [loading, setLoading] = useState(false);
  const [viewMode, setViewMode] = useState<ViewMode>('table');
  const [yamlContent, setYamlContent] = useState('');
  const [selectedRowKeys, setSelectedRowKeys] = useState<string[]>([]);

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
      console.error('Failed to load config from API, using defaults:', error);
      // If API fails, use default ignore fields
      const defaultConfig = { ignoreFields: [...DEFAULT_IGNORE_FIELDS] };
      setConfig(defaultConfig);
      onConfigChange?.(defaultConfig);
      message.info('Using default ignore rules (API not available)');
    } finally {
      setLoading(false);
    }
  };

  const addIgnoreField = () => {
    const fieldPath = newField.trim();
    if (!fieldPath) {
      message.warning('Please enter a field path');
      return;
    }

    if (config.ignoreFields.includes(fieldPath)) {
      message.warning('This field is already in the ignore list');
      return;
    }

    // Update state only - no API call
    // Ignore fields will be sent in validation request payload
    const updatedConfig = {
      ...config,
      ignoreFields: [...config.ignoreFields, fieldPath].sort(),
    };
    setConfig(updatedConfig);
    onConfigChange?.(updatedConfig);
    setNewField('');
    message.success(`Added ignore field: ${fieldPath}`);
  };

  const removeIgnoreField = (fieldPath: string) => {
    // Update state only - no API call
    // Ignore fields will be sent in validation request payload
    const updatedConfig = {
      ...config,
      ignoreFields: config.ignoreFields.filter(f => f !== fieldPath),
    };
    setConfig(updatedConfig);
    onConfigChange?.(updatedConfig);
    message.success(`Removed ignore field: ${fieldPath}`);
  };

  const removeMultipleFields = (fieldPaths: string[]) => {
    const updatedConfig = {
      ...config,
      ignoreFields: config.ignoreFields.filter(f => !fieldPaths.includes(f)),
    };
    setConfig(updatedConfig);
    onConfigChange?.(updatedConfig);
    setSelectedRowKeys([]);
    message.success(`Removed ${fieldPaths.length} ignore field(s)`);
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

  const resetToDefault = () => {
    // Reset to default ignore fields without API call
    const defaultConfig = { ignoreFields: [...DEFAULT_IGNORE_FIELDS] };
    setConfig(defaultConfig);
    onConfigChange?.(defaultConfig);
    message.success('Config reset to default ignore rules');
  };

  const commonIgnoreFields = [
    { value: 'spec.replicas', description: 'Replica count' },
    { value: 'spec.template.spec.containers[0].image', description: 'Container image' },
    { value: 'spec.template.spec.containers[0].resources', description: 'Resource limits' },
    { value: 'metadata.labels', description: 'All labels (prefix match)' },
    { value: 'spec.template.spec.serviceAccountName', description: 'Service account' },
  ];

  // Merged table columns (default + custom)
  const tableColumns = [
    {
      title: 'Field Path',
      dataIndex: 'field',
      key: 'field',
      render: (text: string, record: any) => (
        <span style={{ 
          fontFamily: 'monospace', 
          fontSize: 12,
          color: record.isDefault ? '#666' : '#000'
        }}>
          {text}
        </span>
      ),
    },
    {
      title: 'Type',
      key: 'type',
      width: 100,
      render: (_: any, record: any) => (
        <Tag color={record.isDefault ? 'blue' : 'green'}>
          {record.isDefault ? 'Default' : 'Custom'}
        </Tag>
      ),
    },
    {
      title: 'Action',
      key: 'action',
      width: 80,
      render: (_: any, record: any) => (
        record.isDefault ? (
          <Tag color="blue">Read-only</Tag>
        ) : (
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
        )
      ),
    },
  ];

  const renderTableView = () => {
    // Merge default and custom fields with metadata
    const allFieldsData = [
      ...DEFAULT_IGNORE_FIELDS.map(field => ({ field, isDefault: true })),
      ...customFields.map(field => ({ field, isDefault: false }))
    ].sort((a, b) => a.field.localeCompare(b.field));

    // Only custom fields can be selected for deletion
    const selectableData = allFieldsData.filter(item => !item.isDefault);
    
    const rowSelection = {
      selectedRowKeys,
      onChange: (selectedKeys: React.Key[]) => {
        setSelectedRowKeys(selectedKeys as string[]);
      },
      getCheckboxProps: (record: any) => ({
        disabled: record.isDefault, // Default fields cannot be selected
      }),
    };

    const hasSelected = selectedRowKeys.length > 0;

    return (
      <div>
        {/* Add new field section */}
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
            Add Rule
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

        {/* Bulk actions */}
        {hasSelected && (
          <div style={{ marginBottom: 16 }}>
            <Space>
              <span style={{ fontSize: 12, color: '#666' }}>
                Selected: {selectedRowKeys.length} rule(s)
              </span>
              <Popconfirm
                title={`Remove ${selectedRowKeys.length} selected rule(s)?`}
                onConfirm={() => removeMultipleFields(selectedRowKeys)}
                okText="Remove"
                cancelText="Cancel"
              >
                <Button 
                  type="primary" 
                  danger 
                  size="small"
                  icon={<Trash2 size={14} />}
                >
                  Remove Selected
                </Button>
              </Popconfirm>
              <Button 
                size="small"
                onClick={() => setSelectedRowKeys([])}
              >
                Clear Selection
              </Button>
              <Button 
                size="small"
                onClick={() => setSelectedRowKeys(selectableData.map(item => item.field))}
              >
                Select All Custom Rules
              </Button>
            </Space>
          </div>
        )}

        {/* Merged table */}
        <Table
          size="small"
          rowSelection={rowSelection}
          dataSource={allFieldsData}
          columns={tableColumns}
          pagination={{ pageSize: 15, size: 'small', showSizeChanger: true, showTotal: (total) => `Total ${total} rules` }}
          rowKey="field"
        />
      </div>
    );
  };

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
