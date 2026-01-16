import { Card, Table, Tag, Statistic, Row, Col, Button } from 'antd';
import { CheckCircle, XCircle, AlertCircle, Download } from 'lucide-react';
import type { CnfValidationResultJson, CnfComparison, CnfChecklistResult } from '../types/cnf';
import * as XLSX from 'xlsx';

interface CnfValidationResultsProps {
  result: CnfValidationResultJson;
}

const getStatusColor = (status: string) => {
  switch (status) {
    case 'MATCH': return 'success';
    case 'DIFFERENT': return 'error';
    case 'MISSING_IN_RUNTIME': return 'warning';
    case 'ERROR': return 'default';
    default: return 'default';
  }
};

const getStatusIcon = (status: string) => {
  switch (status) {
    case 'MATCH': return <CheckCircle size={16} color="green" />;
    case 'DIFFERENT': return <XCircle size={16} color="red" />;
    case 'MISSING_IN_RUNTIME': return <AlertCircle size={16} color="orange" />;
    case 'ERROR': return <XCircle size={16} color="gray" />;
    default: return null;
  }
};

export const CnfValidationResults = ({ result }: CnfValidationResultsProps) => {
  
  const exportToExcel = () => {
    const workbook = XLSX.utils.book_new();
    
    // Overall summary sheet
    const summaryData = [
      ['CNF Checklist Validation Results'],
      ['Job ID', result.jobId],
      ['Description', result.description],
      ['Submitted At', new Date(result.submittedAt).toLocaleString()],
      ['Completed At', new Date(result.completedAt).toLocaleString()],
      [],
      ['Overall Summary'],
      ['Total VIM/Namespaces', result.summary.totalVimNamespaces],
      ['Total Fields', result.summary.totalFields],
      ['Matches', result.summary.totalMatches],
      ['Differences', result.summary.totalDifferences],
      ['Missing', result.summary.totalMissing],
      ['Errors', result.summary.totalErrors],
    ];
    
    const summarySheet = XLSX.utils.aoa_to_sheet(summaryData);
    XLSX.utils.book_append_sheet(workbook, summarySheet, 'Summary');
    
    // Results sheet with all items
    const resultsData: any[] = [[
      'VIM Name',
      'Namespace',
      'Kind',
      'Object Name',
      'Field Key',
      'Baseline Value',
      'Actual Value',
      'Status',
      'Message'
    ]];
    
    result.results.forEach(comp => {
      comp.items.forEach(item => {
        resultsData.push([
          comp.vimName,
          comp.namespace,
          item.kind,
          item.objectName,
          item.fieldKey,
          item.baselineValue,
          item.actualValue || '',
          item.status,
          item.message || ''
        ]);
      });
    });
    
    const resultsSheet = XLSX.utils.aoa_to_sheet(resultsData);
    XLSX.utils.book_append_sheet(workbook, resultsSheet, 'Results');
    
    // Download
    XLSX.writeFile(workbook, `cnf-validation-${result.jobId}.xlsx`);
  };

  const columns = [
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      width: 120,
      fixed: 'left' as const,
      filters: [...new Set(result.results.flatMap(r => r.items.map(i => i.kind)))].map(k => ({ text: k, value: k })),
      onFilter: (value: any, record: CnfChecklistResult) => record.kind === value,
    },
    {
      title: 'Object Name',
      dataIndex: 'objectName',
      key: 'objectName',
      width: 180,
      fixed: 'left' as const,
      render: (text: string) => <strong>{text}</strong>,
    },
    {
      title: 'Field Key',
      dataIndex: 'fieldKey',
      key: 'fieldKey',
      width: 300,
      render: (text: string) => (
        <code style={{ fontSize: '11px', color: '#1890ff' }}>
          {text}
        </code>
      ),
    },
    {
      title: 'Expected (Baseline)',
      dataIndex: 'baselineValue',
      key: 'baselineValue',
      width: 250,
      render: (text: string) => (
        <div style={{ 
          fontSize: '12px', 
          background: '#e6f7ff', 
          padding: '6px 10px', 
          borderRadius: '4px',
          border: '1px solid #91d5ff',
          fontFamily: 'monospace',
          wordBreak: 'break-all'
        }}>
          {text}
        </div>
      ),
    },
    {
      title: 'Actual (Runtime)',
      dataIndex: 'actualValue',
      key: 'actualValue',
      width: 250,
      render: (text: string | null, record: CnfChecklistResult) => {
        if (!text) {
          return (
            <div style={{ 
              fontSize: '12px', 
              background: '#fff1f0', 
              padding: '6px 10px', 
              borderRadius: '4px',
              border: '1px solid #ffa39e',
              color: '#999', 
              fontStyle: 'italic' 
            }}>
              ⚠️ Not found in runtime
            </div>
          );
        }
        
        const isMatch = record.status === 'MATCH';
        return (
          <div style={{ 
            fontSize: '12px', 
            background: isMatch ? '#f6ffed' : '#fff1f0', 
            padding: '6px 10px', 
            borderRadius: '4px',
            border: `1px solid ${isMatch ? '#b7eb8f' : '#ffa39e'}`,
            fontFamily: 'monospace',
            wordBreak: 'break-all',
            color: isMatch ? '#52c41a' : '#ff4d4f'
          }}>
            {isMatch && '✓ '}{text}
          </div>
        );
      },
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 140,
      fixed: 'right' as const,
      filters: [
        { text: 'Match', value: 'MATCH' },
        { text: 'Different', value: 'DIFFERENT' },
        { text: 'Missing', value: 'MISSING_IN_RUNTIME' },
        { text: 'Error', value: 'ERROR' },
      ],
      onFilter: (value: any, record: CnfChecklistResult) => record.status === value,
      render: (status: string) => (
        <Tag color={getStatusColor(status)} icon={getStatusIcon(status)} style={{ fontSize: '12px' }}>
          {status.replace(/_/g, ' ')}
        </Tag>
      ),
    },
    {
      title: 'Message',
      dataIndex: 'message',
      key: 'message',
      width: 200,
      ellipsis: true,
      render: (text: string) => text || '-',
    },
  ];

  return (
    <div style={{ marginTop: 24 }}>
      <Card 
        title="CNF Validation Results" 
        extra={
          <Button icon={<Download size={16} />} onClick={exportToExcel}>
            Export Excel
          </Button>
        }
      >
        {/* Overall Summary */}
        <Row gutter={16} style={{ marginBottom: 24 }}>
          <Col span={4}>
            <Statistic 
              title="Total Fields" 
              value={result.summary.totalFields}
              prefix={<CheckCircle size={20} />}
            />
          </Col>
          <Col span={4}>
            <Statistic 
              title="Matches" 
              value={result.summary.totalMatches}
              valueStyle={{ color: '#3f8600' }}
              prefix={<CheckCircle size={20} color="green" />}
            />
          </Col>
          <Col span={4}>
            <Statistic 
              title="Differences" 
              value={result.summary.totalDifferences}
              valueStyle={{ color: '#cf1322' }}
              prefix={<XCircle size={20} color="red" />}
            />
          </Col>
          <Col span={4}>
            <Statistic 
              title="Missing" 
              value={result.summary.totalMissing}
              valueStyle={{ color: '#d46b08' }}
              prefix={<AlertCircle size={20} color="orange" />}
            />
          </Col>
          <Col span={4}>
            <Statistic 
              title="VIM/Namespaces" 
              value={result.summary.totalVimNamespaces}
            />
          </Col>
          <Col span={4}>
            <Statistic 
              title="Execution Time" 
              value={result.summary.executionTimeMs}
              suffix="ms"
            />
          </Col>
        </Row>

        {/* Results by VIM/Namespace */}
        {result.results.map((comp: CnfComparison) => (
          <Card
            key={`${comp.vimName}/${comp.namespace}`}
            type="inner"
            title={`${comp.vimName}/${comp.namespace}`}
            style={{ marginBottom: 16 }}
            extra={
              <div>
                <Tag color="success">{comp.summary.matchCount} Match</Tag>
                <Tag color="error">{comp.summary.differenceCount} Diff</Tag>
                <Tag color="warning">{comp.summary.missingCount} Missing</Tag>
              </div>
            }
          >
            <Table
              dataSource={comp.items}
              columns={columns}
              rowKey={(_, index) => `${comp.vimName}-${comp.namespace}-${index}`}
              pagination={{ pageSize: 10, showSizeChanger: true, showTotal: (total) => `Total ${total} fields` }}
              size="small"
              scroll={{ x: 'max-content' }}
            />
          </Card>
        ))}
      </Card>
    </div>
  );
};
