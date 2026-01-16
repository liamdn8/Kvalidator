import { Radio, Space, Input, Select, Form } from 'antd';
import { FileText, Database, Code, Server, Box } from 'lucide-react';
import { useState, useEffect } from 'react';
import { validationApi } from '../services/api';

const { TextArea } = Input;

interface BaselineSetupProps {
  onBaselineChange: (baseline: {
    type: 'yaml' | 'namespace';
    yamlContent?: string;
    cluster?: string;
    namespace?: string;
  }) => void;
}

export const BaselineSetup = ({ onBaselineChange }: BaselineSetupProps) => {
  const [type, setType] = useState<'yaml' | 'namespace'>('yaml');
  const [yamlContent, setYamlContent] = useState('');
  const [clusters, setClusters] = useState<string[]>([]);
  const [namespaces, setNamespaces] = useState<string[]>([]);
  const [selectedCluster, setSelectedCluster] = useState<string>();
  const [selectedNamespace, setSelectedNamespace] = useState<string>();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadClusters();
  }, []);

  const loadClusters = async () => {
    try {
      setLoading(true);
      const data = await validationApi.getClusters();
      setClusters(data);
    } catch (error) {
      console.error('Failed to load clusters:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadNamespaces = async (cluster: string) => {
    try {
      setLoading(true);
      const data = await validationApi.getNamespaces(cluster);
      setNamespaces(data);
    } catch (error) {
      console.error('Failed to load namespaces:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleTypeChange = (value: 'yaml' | 'namespace') => {
    setType(value);
    onBaselineChange({
      type: value,
      yamlContent: value === 'yaml' ? yamlContent : undefined,
      cluster: value === 'namespace' ? selectedCluster : undefined,
      namespace: value === 'namespace' ? selectedNamespace : undefined,
    });
  };

  const handleClusterChange = (cluster: string) => {
    setSelectedCluster(cluster);
    setSelectedNamespace(undefined);
    setNamespaces([]);
    loadNamespaces(cluster);
    onBaselineChange({
      type: 'namespace',
      cluster,
      namespace: undefined,
    });
  };

  const handleNamespaceChange = (namespace: string) => {
    setSelectedNamespace(namespace);
    onBaselineChange({
      type: 'namespace',
      cluster: selectedCluster,
      namespace,
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

  return (
    <div>
      <Form.Item label="Baseline Source">
        <Radio.Group value={type} onChange={(e) => handleTypeChange(e.target.value)}>
          <Space style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start' }}>
            <Radio value="yaml">
              <Space>
                <FileText size={16} />
                YAML Files
              </Space>
            </Radio>
            <Radio value="namespace">
              <Space>
                <Database size={16} />
                Cluster Namespace
              </Space>
            </Radio>
          </Space>
        </Radio.Group>
      </Form.Item>

      {type === 'yaml' ? (
        <Form.Item label={<Space><Code size={16} />YAML Content</Space>}>
          <TextArea
            value={yamlContent}
            onChange={handleYamlChange}
            placeholder="apiVersion: apps/v1&#10;kind: Deployment&#10;metadata:&#10;  name: example&#10;..."
            rows={8}
            style={{ fontFamily: 'monospace' }}
          />
        </Form.Item>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <Form.Item label={<Space><Server size={16} />Cluster</Space>}>
            <Select
              value={selectedCluster}
              onChange={handleClusterChange}
              placeholder="Select cluster..."
              loading={loading}
              options={clusters.map(c => ({ label: c, value: c }))}
            />
          </Form.Item>
          <Form.Item label={<Space><Box size={16} />Namespace</Space>}>
            <Select
              value={selectedNamespace}
              onChange={handleNamespaceChange}
              placeholder="Select namespace..."
              disabled={!selectedCluster}
              loading={loading}
              options={namespaces.map(ns => ({ label: ns, value: ns }))}
            />
          </Form.Item>
        </div>
      )}
    </div>
  );
};
