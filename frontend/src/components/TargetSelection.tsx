import { useState, useEffect } from 'react';
import { Select, Button, Space, Card, List, Tag, Input, Form, App } from 'antd';
import { PlusCircle, Search, X, Server } from 'lucide-react';
import { validationApi } from '../services/api';
import type { ClusterNamespace, NamespaceSearchResult } from '../types';

interface TargetSelectionProps {
  targets: ClusterNamespace[];
  onTargetsChange: (targets: ClusterNamespace[]) => void;
}

export const TargetSelection = ({ targets, onTargetsChange }: TargetSelectionProps) => {
  const { message } = App.useApp();
  const [clusters, setClusters] = useState<string[]>([]);
  const [namespaces, setNamespaces] = useState<string[]>([]);
  const [selectedCluster, setSelectedCluster] = useState<string>();
  const [selectedNamespace, setSelectedNamespace] = useState<string>();
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<NamespaceSearchResult[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadClusters();
  }, []);

  const loadClusters = async () => {
    try {
      const data = await validationApi.getClusters();
      setClusters(data);
    } catch (error) {
      console.error('Failed to load clusters:', error);
    }
  };

  const loadNamespaces = async (cluster: string) => {
    try {
      setLoading(true);
      const data = await validationApi.getNamespaces(cluster);
      setNamespaces(data);
    } catch (error) {
      message.error('Failed to load namespaces');
    } finally {
      setLoading(false);
    }
  };

  const handleClusterChange = (cluster: string) => {
    setSelectedCluster(cluster);
    setSelectedNamespace(undefined);
    setNamespaces([]);
    loadNamespaces(cluster);
  };

  const handleAddTarget = () => {
    if (!selectedCluster || !selectedNamespace) {
      message.warning('Please select both cluster and namespace');
      return;
    }

    const exists = targets.some(
      t => t.cluster === selectedCluster && t.namespace === selectedNamespace
    );

    if (exists) {
      message.warning('This target already exists');
      return;
    }

    onTargetsChange([...targets, { cluster: selectedCluster, namespace: selectedNamespace }]);
    setSelectedNamespace(undefined);
    message.success('Target added');
  };

  const handleRemoveTarget = (cluster: string, namespace: string) => {
    onTargetsChange(targets.filter(t => !(t.cluster === cluster && t.namespace === namespace)));
  };

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      message.warning('Please enter a search keyword');
      return;
    }

    try {
      setLoading(true);
      const results = await validationApi.searchNamespaces(searchKeyword);
      setSearchResults(results);
      if (results.length === 0) {
        message.info('No namespaces found');
      }
    } catch (error) {
      message.error('Search failed');
    } finally {
      setLoading(false);
    }
  };

  const handleAddFromSearch = (result: NamespaceSearchResult) => {
    const exists = targets.some(
      t => t.cluster === result.cluster && t.namespace === result.namespace
    );

    if (exists) {
      message.warning('This target already exists');
      return;
    }

    onTargetsChange([...targets, { cluster: result.cluster, namespace: result.namespace }]);
    message.success('Target added from search');
  };

  return (
    <div>
      <Form.Item label={<Space><Search size={16} />Search Namespaces</Space>}>
        <Space.Compact style={{ width: '100%' }}>
          <Input
            value={searchKeyword}
            onChange={(e) => setSearchKeyword(e.target.value)}
            placeholder="Search by keyword..."
            onPressEnter={handleSearch}
          />
          <Button type="primary" icon={<Search size={16} />} onClick={handleSearch} loading={loading}>
            Search
          </Button>
        </Space.Compact>
      </Form.Item>

      {searchResults.length > 0 && (
        <Card size="small" style={{ marginBottom: 16 }}>
          <List
            size="small"
            dataSource={searchResults}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button
                    size="small"
                    type="link"
                    icon={<PlusCircle size={14} />}
                    onClick={() => handleAddFromSearch(item)}
                  >
                    Add
                  </Button>
                ]}
              >
                <List.Item.Meta
                  title={`${item.cluster} / ${item.namespace}`}
                  description={`${item.objectCount} resources${item.description ? ` - ${item.description}` : ''}`}
                />
              </List.Item>
            )}
          />
        </Card>
      )}

      <Form.Item label={<Space><PlusCircle size={16} />Add Target</Space>}>
        <Space.Compact style={{ width: '100%' }}>
          <Select
            value={selectedCluster}
            onChange={handleClusterChange}
            placeholder="Select cluster..."
            style={{ width: '40%' }}
            options={clusters.map(c => ({ label: c, value: c }))}
          />
          <Select
            value={selectedNamespace}
            onChange={setSelectedNamespace}
            placeholder="Select namespace..."
            disabled={!selectedCluster}
            loading={loading}
            style={{ width: '40%' }}
            options={namespaces.map(ns => ({ label: ns, value: ns }))}
          />
          <Button type="primary" icon={<PlusCircle size={16} />} onClick={handleAddTarget}>
            Add
          </Button>
        </Space.Compact>
      </Form.Item>

      <Form.Item label={`Selected Targets (${targets.length})`}>
        {targets.length === 0 ? (
          <Card size="small">
            <div style={{ textAlign: 'center', color: '#999', padding: '20px 0' }}>
              No targets selected
            </div>
          </Card>
        ) : (
          <List
            size="small"
            bordered
            dataSource={targets}
            renderItem={(item) => (
              <List.Item
                actions={[
                  <Button
                    type="text"
                    danger
                    size="small"
                    icon={<X size={14} />}
                    onClick={() => handleRemoveTarget(item.cluster, item.namespace)}
                  />
                ]}
              >
                <Space>
                  <Tag color="blue">{item.cluster}</Tag>
                  <Server size={14} />
                  <span>{item.namespace}</span>
                </Space>
              </List.Item>
            )}
          />
        )}
      </Form.Item>
    </div>
  );
};
