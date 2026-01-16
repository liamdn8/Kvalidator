import { useState } from 'react';
import { Radio, Card, Input, Select, Form, Upload, Button, Space } from 'antd';
import { FileText, Database, Upload as UploadIcon } from 'lucide-react';
import type { ClusterNamespace } from '../types';

const { TextArea } = Input;

interface BaselineSelectorProps {
  selectedNamespaces: ClusterNamespace[];
  onBaselineChange: (baseline: {
    type: 'yaml' | 'namespace';
    yamlContent?: string;
    selectedNamespaceIndex?: number;
  }) => void;
}

export const BaselineSelector = ({ selectedNamespaces, onBaselineChange }: BaselineSelectorProps) => {
  const [type, setType] = useState<'yaml' | 'namespace'>('namespace');
  const [yamlContent, setYamlContent] = useState('');
  const [selectedIndex, setSelectedIndex] = useState<number>();

  const handleTypeChange = (value: 'yaml' | 'namespace') => {
    setType(value);
    onBaselineChange({
      type: value,
      yamlContent: value === 'yaml' ? yamlContent : undefined,
      selectedNamespaceIndex: value === 'namespace' ? selectedIndex : undefined,
    });
  };

  const handleYamlChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const content = e.target.value;
    setYamlContent(content);
    onBaselineChange({
      type: 'yaml',
      yamlContent: content,
    });
  };

  const handleNamespaceSelect = (index: number) => {
    setSelectedIndex(index);
    onBaselineChange({
      type: 'namespace',
      selectedNamespaceIndex: index,
    });
  };

  const handleFileUpload = (file: File) => {
    const reader = new FileReader();
    reader.onload = (e) => {
      const content = e.target?.result as string;
      setYamlContent(content);
      onBaselineChange({
        type: 'yaml',
        yamlContent: content,
      });
    };
    reader.readAsText(file);
    return false; // Prevent default upload
  };

  return (
    <Card title="Baseline Configuration" size="small">
      <Form.Item label="Baseline Type">
        <Radio.Group value={type} onChange={(e) => handleTypeChange(e.target.value)}>
          <Space style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
            <Radio value="yaml">
              <Space>
                <FileText size={16} />
                YAML Content
              </Space>
            </Radio>
            <Radio value="namespace">
              <Space>
                <Database size={16} />
                Select from Namespaces
              </Space>
            </Radio>
          </Space>
        </Radio.Group>
      </Form.Item>

      {type === 'yaml' ? (
        <div>
          <Form.Item label="Upload YAML File">
            <Upload
              accept=".yaml,.yml"
              beforeUpload={handleFileUpload}
              maxCount={1}
              showUploadList={false}
            >
              <Button icon={<UploadIcon size={16} />}>
                Click to Upload YAML
              </Button>
            </Upload>
          </Form.Item>

          <Form.Item label="Or Paste YAML Content">
            <TextArea
              value={yamlContent}
              onChange={handleYamlChange}
              placeholder={`apiVersion: apps/v1
kind: Deployment
metadata:
  name: example
  namespace: default
spec:
  replicas: 3
  ...`}
              rows={10}
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
          </Form.Item>
        </div>
      ) : (
        <Form.Item 
          label="Select Baseline Namespace"
          help="Choose one namespace from the selected list above to use as baseline"
        >
          <Select
            size="large"
            value={selectedIndex}
            onChange={handleNamespaceSelect}
            placeholder="Select a namespace as baseline..."
            disabled={selectedNamespaces.length === 0}
            options={selectedNamespaces.map((ns, index) => ({
              label: `#${index + 1} - ${ns.cluster} / ${ns.namespace}`,
              value: index,
            }))}
            style={{ width: '100%' }}
          />
          {selectedNamespaces.length === 0 && (
            <div style={{ marginTop: 8, color: '#999', fontSize: 13 }}>
              Please add namespaces first using the search above
            </div>
          )}
        </Form.Item>
      )}
    </Card>
  );
};
