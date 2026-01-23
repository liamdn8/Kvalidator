import { useState } from 'react';
import { Radio, Card, Input, Select, Form, Upload, Button, Space, message as antdMessage, Tag, Statistic, Row, Col } from 'antd';
import { FileText, Database, Upload as UploadIcon, FolderOpen, FileCode } from 'lucide-react';
import type { ClusterNamespace } from '../types';
import type { UploadFile } from 'antd';
// @ts-ignore
import * as yaml from 'js-yaml';

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
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [resourceStats, setResourceStats] = useState<{
    totalResources: number;
    byKind: Record<string, number>;
  } | null>(null);

  // Parse YAML and count resources
  const parseAndCountResources = (content: string) => {
    try {
      const docs = yaml.loadAll(content);
      const byKind: Record<string, number> = {};
      let totalResources = 0;

      docs.forEach((doc: any) => {
        if (doc && doc.kind) {
          byKind[doc.kind] = (byKind[doc.kind] || 0) + 1;
          totalResources++;
        }
      });

      setResourceStats({ totalResources, byKind });
    } catch (error) {
      console.error('Failed to parse YAML:', error);
      setResourceStats(null);
    }
  };

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
    parseAndCountResources(content);
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

  // Handle multiple file uploads and merge them
  const handleMultipleFilesUpload = async (fileList: UploadFile[]) => {
    const contents: string[] = [];
    
    for (const file of fileList) {
      if (file.originFileObj) {
        const content = await readFileAsText(file.originFileObj);
        contents.push(content);
      }
    }

    // Merge all YAML contents with separators
    const mergedContent = contents.join('\n---\n');
    setYamlContent(mergedContent);
    parseAndCountResources(mergedContent);
    onBaselineChange({
      type: 'yaml',
      yamlContent: mergedContent,
    });

    antdMessage.success(`Loaded and merged ${fileList.length} YAML file(s)`);
  };

  const readFileAsText = (file: File): Promise<string> => {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = (e) => resolve(e.target?.result as string);
      reader.onerror = reject;
      reader.readAsText(file);
    });
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
          <Form.Item label="Upload YAML Files">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Upload
                accept=".yaml,.yml"
                multiple
                fileList={fileList}
                onChange={({ fileList }) => {
                  setFileList(fileList);
                  handleMultipleFilesUpload(fileList);
                }}
                beforeUpload={() => false}
              >
                <Space>
                  <Button icon={<UploadIcon size={16} />}>
                    Upload Files
                  </Button>
                  <Button icon={<FolderOpen size={16} />}>
                    Or Select Folder
                  </Button>
                </Space>
              </Upload>
              
              {fileList.length > 0 && (
                <div style={{ marginTop: 8 }}>
                  <Tag color="blue">
                    <FileCode size={14} style={{ marginRight: 4, verticalAlign: 'middle' }} />
                    {fileList.length} file(s) loaded
                  </Tag>
                </div>
              )}
            </Space>
          </Form.Item>

          {resourceStats && resourceStats.totalResources > 0 && (
            <Card size="small" style={{ marginBottom: 16, background: '#f8fafc' }}>
              <Row gutter={16}>
                <Col span={8}>
                  <Statistic 
                    title="Total Resources" 
                    value={resourceStats.totalResources} 
                    valueStyle={{ color: '#1890ff', fontSize: 20 }}
                  />
                </Col>
                <Col span={16}>
                  <div style={{ marginTop: 8 }}>
                    <div style={{ marginBottom: 4, fontSize: 12, color: '#64748b' }}>Resource Types:</div>
                    <Space wrap>
                      {Object.entries(resourceStats.byKind)
                        .sort(([, a], [, b]) => b - a)
                        .map(([kind, count]) => (
                          <Tag key={kind} color="geekblue">
                            {kind}: {count}
                          </Tag>
                        ))}
                    </Space>
                  </div>
                </Col>
              </Row>
            </Card>
          )}

          <Form.Item label="Or Paste YAML Content">
            <TextArea
              value={yamlContent}
              onChange={handleYamlChange}
              placeholder={`apiVersion: apps/v1
kind: Deployment
metadata:
  name: example
spec:
  replicas: 3
---
apiVersion: v1
kind: Service
metadata:
  name: example-service`}
              rows={12}
              style={{ fontFamily: 'monospace', fontSize: 12 }}
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
