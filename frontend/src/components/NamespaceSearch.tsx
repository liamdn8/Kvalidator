import { useState } from 'react';
import { Input, Button, Card, Table, Tag, Spin, App, Badge, Checkbox } from 'antd';
import { Search } from 'lucide-react';
import { validationApi } from '../services/api';
import type { ClusterNamespace, NamespaceSearchResult } from '../types';

interface NamespaceSearchProps {
  selectedNamespaces: ClusterNamespace[];
  onNamespacesChange: (namespaces: ClusterNamespace[]) => void;
  onSearchResultsChange?: (hasResults: boolean) => void;
  onSearchKeywordChange?: (keyword: string) => void;
}

export const NamespaceSearch = ({ selectedNamespaces, onNamespacesChange, onSearchResultsChange, onSearchKeywordChange }: NamespaceSearchProps) => {
  const { message } = App.useApp();
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchResults, setSearchResults] = useState<NamespaceSearchResult[]>([]);
  const [loading, setLoading] = useState(false);

  const handleSearch = async () => {
    if (!searchKeyword.trim()) {
      message.warning('Please enter a search keyword');
      return;
    }

    try {
      setLoading(true);
      const results = await validationApi.searchNamespaces(searchKeyword);
      setSearchResults(results);
      if (onSearchResultsChange) {
        onSearchResultsChange(results.length > 0);
      }
      if (onSearchKeywordChange && results.length > 0) {
        onSearchKeywordChange(searchKeyword);
      }
      if (results.length === 0) {
        message.info('No namespaces found');
      } else {
        message.success(`Found ${results.length} namespace(s)`);
      }
    } catch (error) {
      message.error('Search failed');
      console.error('Search error:', error);
      if (onSearchResultsChange) {
        onSearchResultsChange(false);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleToggleNamespace = (result: NamespaceSearchResult) => {
    const exists = selectedNamespaces.some(
      ns => ns.cluster === result.cluster && ns.namespace === result.namespace
    );

    if (exists) {
      // Remove
      onNamespacesChange(
        selectedNamespaces.filter(ns => !(ns.cluster === result.cluster && ns.namespace === result.namespace))
      );
    } else {
      // Add
      onNamespacesChange([...selectedNamespaces, { 
        cluster: result.cluster, 
        namespace: result.namespace 
      }]);
    }
  };

  const isSelected = (cluster: string, namespace: string) => {
    return selectedNamespaces.some(ns => ns.cluster === cluster && ns.namespace === namespace);
  };

  const columns = [
    {
      title: '',
      key: 'select',
      width: 50,
      render: (_: any, record: NamespaceSearchResult) => (
        <Checkbox
          checked={isSelected(record.cluster, record.namespace)}
          onChange={() => handleToggleNamespace(record)}
        />
      ),
    },
    {
      title: 'Namespace',
      key: 'namespace',
      render: (_: any, record: NamespaceSearchResult) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Tag color="blue">{record.cluster}</Tag>
          <span style={{ fontWeight: 500 }}>{record.namespace}</span>
          <Badge 
            count={`${record.objectCount} resources`} 
            showZero 
            style={{ backgroundColor: '#e5ffd9', color: '#4a7c00', borderColor: '#e5ffd9' }}
            overflowCount={999}
          />
        </div>
      ),
    },
  ];

  return (
    <div>
      <div style={{ marginBottom: 16 }}>
        <Input.Search
          size="large"
          placeholder="Search namespaces by keyword (e.g., app, prod, kube-system)..."
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          onSearch={handleSearch}
          loading={loading}
          enterButton={
            <Button type="primary" icon={<Search size={16} />}>
              Search
            </Button>
          }
        />
      </div>

      {loading && (
        <div style={{ textAlign: 'center', padding: 40 }}>
          <Spin size="large" tip="Searching namespaces..." />
        </div>
      )}

      {!loading && searchResults.length > 0 && (
        <Card 
          title={
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>Search Results ({searchResults.length})</span>
              <div>
                <span>Selected: {selectedNamespaces.length}</span>
                {selectedNamespaces.length > 0 && selectedNamespaces.length < 2 && (
                  <Tag color="warning" style={{ marginLeft: 8 }}>Minimum 2 required</Tag>
                )}
              </div>
            </div>
          }
          size="small"
        >
          <Table
            size="small"
            columns={columns}
            dataSource={searchResults}
            rowKey={(record) => `${record.cluster}-${record.namespace}`}
            pagination={false}
            rowClassName={(record) => 
              isSelected(record.cluster, record.namespace) ? 'ant-table-row-selected' : ''
            }
            onRow={(record) => ({
              onClick: () => handleToggleNamespace(record),
              style: { cursor: 'pointer' }
            })}
          />
        </Card>
      )}
    </div>
  );
};
