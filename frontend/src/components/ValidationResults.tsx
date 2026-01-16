// ValidationResults Component - Updated for multi-cluster namespace support
import { Card, Statistic, Table, Tag, Button, Space, Modal, Input, Row, Col } from 'antd';
import { Download, CheckCircle, AlertCircle, XCircle, Plus, Eye, Search } from 'lucide-react';
import type { ValidationResultJson } from '../types';
import * as XLSX from 'xlsx';
import { useState } from 'react';

interface ValidationResultsProps {
  result: ValidationResultJson | null;
}

const getStatusColor = (status: string) => {
  switch (status) {
    case 'BASELINE': return 'blue';
    case 'IDENTICAL': return 'success';
    case 'OK': return 'success';
    case 'DIFFERENT': return 'warning';
    case 'NOK': return 'warning';
    case 'MISSING': return 'error';
    case 'EXTRA': return 'processing';
    default: return 'default';
  }
};

const getStatusIcon = (status: string) => {
  switch (status) {
    case 'BASELINE': return null;
    case 'IDENTICAL': return <CheckCircle size={16} />;
    case 'OK': return <CheckCircle size={16} />;
    case 'DIFFERENT': return <AlertCircle size={16} />;
    case 'NOK': return <AlertCircle size={16} />;
    case 'MISSING': return <XCircle size={16} />;
    case 'EXTRA': return <Plus size={16} />;
    default: return null;
  }
};

export const ValidationResults = ({ result }: ValidationResultsProps) => {
  const [detailsModalVisible, setDetailsModalVisible] = useState(false);
  const [selectedObject, setSelectedObject] = useState<any>(null);
  
  if (!result) return null;

  const { summary, comparisons } = result;

  // Validate data structure
  if (!summary || !comparisons || typeof comparisons !== 'object') {
    console.error('Invalid result structure:', result);
    return (
      <Card>
        <div style={{ textAlign: 'center', padding: 40 }}>
          <AlertCircle size={48} color="#ff4d4f" style={{ marginBottom: 16 }} />
          <h3>Invalid validation results</h3>
          <p style={{ color: '#999' }}>The validation completed but returned unexpected data format.</p>
          <pre style={{ textAlign: 'left', background: '#f5f5f5', padding: 16, borderRadius: 4, marginTop: 16, overflow: 'auto' }}>
            {JSON.stringify(result, null, 2)}
          </pre>
        </div>
      </Card>
    );
  }

  // Extract all unique namespaces and objects
  const namespacesSet = new Set<string>();
  const objectsMap = new Map<string, {
    objectName: string;
    kind: string;
    statuses: Map<string, { status: string; differenceCount: number; details: string[]; items?: any[] }>;
  }>();

  let baselineNamespace = '';

  Object.entries(comparisons).forEach(([comparisonKey, comparison]) => {
    // Use labels from backend as namespace keys
    // For CNF Checklist: labels have suffix "(Baseline)" and "(Actual)" to differentiate
    // For normal comparison: labels are just namespace names
    let leftFullNs = comparison.leftNamespace || '';
    let rightFullNs = comparison.rightNamespace || '';
    
    // Fallback: If backend doesn't provide labels, parse from comparison key
    if (!leftFullNs || !rightFullNs) {
      const parts = comparisonKey.split('_vs_');
      leftFullNs = parts[0] || '';
      rightFullNs = parts[1] || '';
    }
    
    if (!baselineNamespace) {
      baselineNamespace = leftFullNs;
    }
    namespacesSet.add(leftFullNs);
    namespacesSet.add(rightFullNs);

    // Only process comparisons where left is the baseline
    // This ensures all comparisons are against the baseline, not sequential pairs
    if (leftFullNs !== baselineNamespace) {
      return; // Skip comparisons that are not against baseline
    }

    if (comparison && comparison.objectComparisons) {
      Object.entries(comparison.objectComparisons).forEach(([objId, objComp]) => {
        if (!objectsMap.has(objId)) {
          objectsMap.set(objId, {
            objectName: objId,
            kind: objComp.objectType || 'Unknown',
            statuses: new Map(),
          });
        }

        const obj = objectsMap.get(objId)!;
        
        // Determine status for this comparison
        const details: string[] = [];
        let status = 'OK';
        let hasOnlyInLeft = false;
        let hasOnlyInRight = false;
        let hasMismatch = false;
        
        // Check if object exists only in LEFT (baseline) but not in RIGHT (target) - MISSING
        const isObjectOnlyInLeft = objComp.items && 
                                    objComp.items.length === 1 && 
                                    objComp.items[0].key === objId &&
                                    objComp.items[0].status === 'ONLY_IN_LEFT' &&
                                    objComp.items[0].leftValue === 'exists';
        
        // Check if object exists only in RIGHT (target) but not in LEFT (baseline) - EXTRA
        const isObjectOnlyInRight = objComp.items && 
                                     objComp.items.length === 1 && 
                                     objComp.items[0].key === objId &&
                                     objComp.items[0].status === 'ONLY_IN_RIGHT' &&
                                     objComp.items[0].rightValue === 'exists';
        
        if (isObjectOnlyInLeft) {
          // Object exists in baseline but not in target - mark as MISSING
          status = 'MISSING';
          details.push(`Object missing in ${comparison.rightNamespace}`);
        } else if (isObjectOnlyInRight) {
          // Object exists in target but not in baseline - mark as EXTRA
          status = 'EXTRA';
          details.push(`Object only exists in ${comparison.rightNamespace}`);
        } else if (objComp.items && Array.isArray(objComp.items)) {
          // Object exists in both - compare fields
          objComp.items.forEach((item: any) => {
            const itemStatus = item.status as string;
            const fieldName = item.key || item.path; // Use key or path
            if (itemStatus === 'ONLY_IN_LEFT') {
              details.push(`Missing: ${fieldName}`);
              hasOnlyInLeft = true;
            } else if (itemStatus === 'ONLY_IN_RIGHT') {
              details.push(`Extra: ${fieldName}`);
              hasOnlyInRight = true;
            } else if (itemStatus === 'VALUE_MISMATCH' || itemStatus === 'DIFFERENT') {
              details.push(`${fieldName}: ${item.leftValue} → ${item.rightValue}`);
              hasMismatch = true;
            }
          });
          
          // Determine namespace status:
          // - IDENTICAL: all fields match (differenceCount = 0)
          // - DIFFERENT: any differences (missing fields, extra fields, value mismatches)
          if (objComp.differenceCount === 0) {
            status = 'IDENTICAL';
          } else if (hasOnlyInLeft || hasOnlyInRight || hasMismatch) {
            status = 'DIFFERENT';
          } else {
            status = 'DIFFERENT';
          }
        }

        // Store status for right namespace (target) using FULL namespace key
        obj.statuses.set(rightFullNs, {
          status,
          differenceCount: objComp.differenceCount || 0,
          details,
          items: objComp.items || [], // Store items for detail view
        });
        
        // If this is the first time seeing this object, mark baseline
        if (!obj.statuses.has(leftFullNs)) {
          obj.statuses.set(leftFullNs, {
            status: 'BASELINE',
            differenceCount: 0,
            details: [],
            items: [],
          });
        }
      });
    }
  });

  // Convert to sorted arrays
  const namespaces = Array.from(namespacesSet);
  
  // Debug: Log namespaces
  console.log('🔍 Namespaces from backend:', namespaces);
  console.log('🔍 Baseline namespace:', baselineNamespace);
  
  // Debug: Log first object's status keys
  const firstObj = Array.from(objectsMap.values())[0];
  if (firstObj) {
    console.log('🔍 First object status keys:', Array.from(firstObj.statuses.keys()));
    console.log('🔍 Namespace match check:', namespaces.map(ns => ({
      namespace: ns,
      hasStatus: firstObj.statuses.has(ns),
      statusKeys: Array.from(firstObj.statuses.keys())
    })));
  }
  
  // Sort: baseline first, then others
  namespaces.sort((a, b) => {
    if (a === baselineNamespace) return -1;
    if (b === baselineNamespace) return 1;
    return a.localeCompare(b);
  });

  const tableData = Array.from(objectsMap.values()).map((obj, index) => {
    // Check if object exists in baseline
    // An object exists in baseline if:
    // 1. It has a status entry for baseline namespace
    const baselineStatus = obj.statuses.get(baselineNamespace);
    
    // Debug logging
    if (obj.objectName === 'test-config-59') {
      console.log('🔍 test-config-59 DEBUG:', {
        baselineNamespace,
        baselineStatus,
        hasBaselineStatus: !!baselineStatus,
        allStatuses: Array.from(obj.statuses.entries())
      });
    }
    
    // Object exists in baseline if there is a status entry for baseline namespace
    const existsInBaseline = !!baselineStatus;
    
    // Calculate overall status for this object
    // Only check non-baseline namespaces - baseline is just for reference
    let overallStatus = 'OK';
    
    // Check all namespace statuses except baseline
    obj.statuses.forEach((statusObj, ns) => {
      // Skip baseline namespace in overall status calculation
      if (ns === baselineNamespace) return;
      
      // Any non-IDENTICAL status in other namespaces makes overall status NOK
      if (statusObj.status !== 'IDENTICAL') {
        overallStatus = 'NOK';
      }
    });
    
    // Build row data with proper status handling
    const rowData: any = {
      key: obj.objectName,
      stt: index + 1,
      kind: obj.kind,
      objectName: obj.objectName,
      overallStatus,
      allStatuses: obj.statuses,
    };
    
    // Set status for each namespace
    namespaces.forEach(ns => {
      const nsStatus = obj.statuses.get(ns);
      
      // Debug logging for first object
      if (index === 0) {
        console.log(`🔍 Object ${obj.objectName}, namespace "${ns}":`, {
          hasStatus: !!nsStatus,
          status: nsStatus?.status,
          allStatusKeys: Array.from(obj.statuses.keys())
        });
      }
      
      if (ns === baselineNamespace) {
        // Baseline namespace
        if (!existsInBaseline) {
          // Object doesn't exist in baseline - mark as MISSING
          rowData[ns] = { status: 'MISSING', differenceCount: 0, details: [], items: [] };
        } else {
          // Object exists in baseline - mark as BASELINE
          rowData[ns] = nsStatus || { status: 'BASELINE', differenceCount: 0, details: [], items: [] };
        }
      } else {
        // Other namespaces (not baseline)
        // Simply use the status from backend - don't override
        if (nsStatus) {
          rowData[ns] = nsStatus;
        } else {
          // No status entry means object is missing in this namespace
          rowData[ns] = { status: 'MISSING', differenceCount: 0, details: [], items: [] };
        }
      }
    });
    
    return rowData;
  });

  // Build dynamic columns
  const columns = [
    {
      title: 'STT',
      dataIndex: 'stt',
      key: 'stt',
      width: 50,
      fixed: 'left' as const,
    },
    {
      title: 'Kind',
      dataIndex: 'kind',
      key: 'kind',
      width: 110,
      fixed: 'left' as const,
      filters: [...new Set(tableData.map(d => d.kind))].map(k => ({ text: k, value: k })),
      onFilter: (value: any, record: any) => record.kind === value,
    },
    {
      title: 'Object Name',
      dataIndex: 'objectName',
      key: 'objectName',
      width: 220,
      fixed: 'left' as const,
      sorter: (a: any, b: any) => a.objectName.localeCompare(b.objectName),
      filterDropdown: ({ setSelectedKeys, selectedKeys, confirm, clearFilters }: any) => (
        <div style={{ padding: 8 }}>
          <Input
            placeholder="Search object name"
            value={selectedKeys[0]}
            onChange={(e) => setSelectedKeys(e.target.value ? [e.target.value] : [])}
            onPressEnter={() => confirm()}
            style={{ marginBottom: 8, display: 'block' }}
          />
          <Space>
            <Button
              type="primary"
              onClick={() => confirm()}
              size="small"
              style={{ width: 90 }}
            >
              Search
            </Button>
            <Button onClick={() => clearFilters()} size="small" style={{ width: 90 }}>
              Reset
            </Button>
          </Space>
        </div>
      ),
      filterIcon: (filtered: boolean) => (
        <Search size={14} style={{ color: filtered ? '#1890ff' : undefined }} />
      ),
      onFilter: (value: any, record: any) =>
        record.objectName.toLowerCase().includes(value.toLowerCase()),
    },
    {
      title: 'Status',
      dataIndex: 'overallStatus',
      key: 'overallStatus',
      width: 80,
      fixed: 'left' as const,
      filters: [
        { text: 'OK', value: 'OK' },
        { text: 'NOK', value: 'NOK' },
      ],
      onFilter: (value: any, record: any) => record.overallStatus === value,
      sorter: (a: any, b: any) => a.overallStatus.localeCompare(b.overallStatus),
      render: (status: string) => (
        <div 
          style={{ 
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            width: '100%',
            height: '100%',
          }}
          title={status}
        >
          <span style={{ 
            display: 'inline-flex',
            padding: 4,
            borderRadius: 4,
            backgroundColor: getStatusColor(status) === 'success' ? '#f6ffed' : 
                            getStatusColor(status) === 'warning' ? '#fffbe6' :
                            getStatusColor(status) === 'error' ? '#fff2f0' : '#e6f7ff',
            color: getStatusColor(status) === 'success' ? '#52c41a' : 
                   getStatusColor(status) === 'warning' ? '#faad14' :
                   getStatusColor(status) === 'error' ? '#ff4d4f' : '#1890ff',
          }}>
            {getStatusIcon(status)}
          </span>
        </div>
      ),
    },
    ...namespaces.map((ns) => {
      // ns is now the full label from backend (e.g., "vim/namespace (Baseline)")
      // Format display: "cluster-name/namespace" → "cluster-name / namespace"
      const displayName = ns.includes('/') 
        ? ns.split('/').map(part => part.trim()).join(' / ')
        : ns;
      
      return {
      title: displayName,
      dataIndex: ns,
      key: ns,
      width: 140,
      filters: [
        { text: 'Baseline', value: 'BASELINE' },
        { text: 'Identical', value: 'IDENTICAL' },
        { text: 'Different', value: 'DIFFERENT' },
        { text: 'Missing', value: 'MISSING' },
        { text: 'Extra', value: 'EXTRA' },
      ],
      onFilter: (value: any, record: any) => {
        const statusObj = record[ns];
        return statusObj && statusObj.status === value;
      },
      sorter: (a: any, b: any) => {
        const statusA = a[ns]?.status || '';
        const statusB = b[ns]?.status || '';
        return statusA.localeCompare(statusB);
      },
      render: (statusObj: any) => {
        if (!statusObj) return <span style={{ color: '#999', fontSize: 12 }}>-</span>;
        
        const { status, differenceCount, details } = statusObj;
        // Display text for VIM column (matching Excel export):
        // - BASELINE → BASE
        // - IDENTICAL → OK
        // - MISSING → MISSING (object exists in baseline but not in target)
        // - EXTRA → EXTRA (object exists in target but not in baseline)
        // - DIFFERENT → DIFF (n) (field value differences)
        const displayText = status === 'BASELINE' 
          ? 'BASE' 
          : status === 'IDENTICAL'
          ? 'OK'
          : status === 'MISSING'
          ? 'MISSING'
          : status === 'EXTRA'
          ? 'EXTRA'
          : differenceCount > 0 ? `DIFF (${differenceCount})` : 'DIFF';
        
        // Truncate long content in tooltip to prevent UI issues
        const MAX_TOOLTIP_LENGTH = 2000;
        let tooltipText = status;
        if (details.length > 0) {
          const detailsText = details.join('\n');
          if (detailsText.length > MAX_TOOLTIP_LENGTH) {
            tooltipText += '\n' + detailsText.substring(0, MAX_TOOLTIP_LENGTH) + 
                           `\n... [Content too long: ${detailsText.length} chars total, showing first ${MAX_TOOLTIP_LENGTH}]`;
          } else {
            tooltipText += '\n' + detailsText;
          }
        }
        
        return (
          <Tag 
            color={getStatusColor(status)} 
            style={{ 
              display: 'inline-flex', 
              alignItems: 'center', 
              justifyContent: 'center',
              gap: 4,
              padding: '0 6px',
              margin: 0,
              fontSize: 11,
              lineHeight: '20px',
              width: '100%',
            }}
            title={tooltipText}
          >
            {getStatusIcon(status) && (
              <span style={{ display: 'flex', alignItems: 'center', transform: 'scale(0.85)' }}>
                {getStatusIcon(status)}
              </span>
            )}
            <span style={{ display: 'flex', alignItems: 'center' }}>{displayText}</span>
          </Tag>
        );
      },
    };
    }),
    {
      title: 'Action',
      key: 'action',
      width: 100,
      fixed: 'right' as const,
      render: (_: any, record: any) => (
        <Button
          type="link"
          size="small"
          icon={<Eye size={16} />}
          onClick={() => {
            setSelectedObject(record);
            setDetailsModalVisible(true);
          }}
        >
          View
        </Button>
      ),
    },
  ];

  const exportToExcel = () => {
    // Create worksheet with pivot table structure
    const exportData = tableData.map((row) => {
      const rowData: any = {
        'STT': row.stt,
        'Kind': row.kind,
        'Object Name': row.objectName,
      };
      
      namespaces.forEach(ns => {
        const statusObj = (row as any)[ns];
        if (statusObj) {
          const { status, differenceCount } = statusObj;
          rowData[ns] = status === 'BASELINE' 
            ? 'BASELINE' 
            : status === 'IDENTICAL'
            ? 'IDENTICAL'
            : `${status}${differenceCount > 0 ? ` (${differenceCount})` : ''}`;
        } else {
          rowData[ns] = 'MISSING';
        }
      });
      
      return rowData;
    });

    const ws = XLSX.utils.json_to_sheet(exportData);

    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'Validation Results');
    
    // Add summary sheet
    const summaryData = [
      { Metric: 'Total Objects', Value: summary.totalObjects },
      { Metric: 'Total Differences', Value: summary.totalDifferences },
      { Metric: 'Namespace Pairs', Value: summary.namespacePairs },
      { Metric: 'Execution Time (ms)', Value: summary.executionTimeMs },
      { Metric: 'Namespaces', Value: namespaces.join(', ') },
    ];
    const wsSummary = XLSX.utils.json_to_sheet(summaryData);
    XLSX.utils.book_append_sheet(wb, wsSummary, 'Summary');
    
    XLSX.writeFile(wb, `validation-${result.jobId}-${Date.now()}.xlsx`);
  };

  const downloadServerExcel = () => {
    // Download Excel file from server
    const downloadUrl = `/kvalidator/api/validate/${result.jobId}/download`;
    const link = document.createElement('a');
    link.href = downloadUrl;
    link.download = `validation-report-${result.jobId}.xlsx`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  // Calculate statistics
  const totalObjects = tableData.length;
  const okCount = tableData.filter(row => row.overallStatus === 'OK').length;
  const nokCount = tableData.filter(row => row.overallStatus === 'NOK').length;
  const okPercentage = totalObjects > 0 ? ((okCount / totalObjects) * 100).toFixed(1) : '0.0';

  return (
    <>
      {/* Summary Statistics - Grid Layout */}
      <Row gutter={[16, 16]} style={{ marginBottom: '24px' }}>
        <Col span={24}>
          <p><strong>Baseline:</strong> {baselineNamespace}</p>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic 
              title="Total" 
              value={totalObjects}
              valueStyle={{ color: '#1890ff' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic 
              title="OK" 
              value={okCount}
              valueStyle={{ color: '#3f8600' }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic 
              title="NOK" 
              value={nokCount}
              valueStyle={{ color: nokCount > 0 ? '#cf1322' : undefined }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card size="small">
            <Statistic 
              title="OK Rate" 
              value={okPercentage}
              suffix="%"
              valueStyle={{ 
                color: parseFloat(okPercentage) >= 80 ? '#3f8600' : parseFloat(okPercentage) >= 50 ? '#faad14' : '#cf1322',
                fontSize: '24px'
              }}
            />
          </Card>
        </Col>
      </Row>

      <Card
        title={`Validation Results (${tableData.length} objects across ${namespaces.length} namespaces)`}
        extra={
          <Space>
            <Button 
              type="default" 
              icon={<Download size={16} />} 
              onClick={exportToExcel}
            >
              Export (Client-side)
            </Button>
            <Button 
              type="primary" 
              icon={<Download size={16} />} 
              onClick={downloadServerExcel}
            >
              Download Report
            </Button>
          </Space>
        }
      >
        <Table
          columns={columns}
          dataSource={tableData}
          rowKey="key"
          pagination={{ 
            pageSize: 20,
            showSizeChanger: true,
            showTotal: (total) => `Total ${total} objects`
          }}
          scroll={{ x: 'max-content' }}
          bordered
        />
      </Card>

      {/* Details Modal */}
      <Modal
        title={
          <div>
            <strong>{selectedObject?.objectName}</strong>
            <Tag color="blue" style={{ marginLeft: 8 }}>{selectedObject?.kind}</Tag>
          </div>
        }
        open={detailsModalVisible}
        onCancel={() => {
          setDetailsModalVisible(false);
          setSelectedObject(null);
        }}
        width={1400}
        footer={[
          <Button key="close" onClick={() => setDetailsModalVisible(false)}>
            Close
          </Button>,
        ]}
      >
        {selectedObject && (() => {
          // Build field-level comparison table
          const fieldMap = new Map<string, { values: Map<string, string>, statuses: Map<string, string> }>();
          
          // Collect all field keys from comparisons
          Object.entries(comparisons).forEach(([, comparison]) => {
            const objComp = comparison.objectComparisons?.[selectedObject.objectName];
            if (!objComp || !objComp.items) return;
            
            // Use leftNamespace and rightNamespace from comparison object (set by backend)
            // These are the same namespace keys used in the main table
            const leftNs = comparison.leftNamespace || '';
            const rightNs = comparison.rightNamespace || '';
            
            objComp.items.forEach((item: any) => {
              const fieldKey = item.key;
              if (!fieldKey) return;
              
              if (!fieldMap.has(fieldKey)) {
                fieldMap.set(fieldKey, { values: new Map(), statuses: new Map() });
              }
              
              const field = fieldMap.get(fieldKey)!;
              
              // Handle all status types from backend
              if (item.status === 'MATCH') {
                const value = String(item.leftValue !== undefined ? item.leftValue : item.rightValue || '');
                field.values.set(leftNs, value);
                field.values.set(rightNs, value);
                field.statuses.set(leftNs, 'MATCH');
                field.statuses.set(rightNs, 'MATCH');
              } else if (item.status === 'DIFFERENT' || item.status === 'VALUE_MISMATCH') {
                field.values.set(leftNs, String(item.leftValue !== undefined ? item.leftValue : ''));
                field.values.set(rightNs, String(item.rightValue !== undefined ? item.rightValue : ''));
                field.statuses.set(leftNs, 'MATCH');
                field.statuses.set(rightNs, 'DIFFERENT');
              } else if (item.status === 'ONLY_IN_LEFT') {
                // Field only exists in baseline (left), missing in target (right)
                field.values.set(leftNs, String(item.leftValue || ''));
                field.statuses.set(leftNs, 'MATCH');
                field.statuses.set(rightNs, 'MISSING');
              } else if (item.status === 'ONLY_IN_RIGHT') {
                // Field only exists in target (right), extra compared to baseline
                field.values.set(rightNs, String(item.rightValue || ''));
                field.statuses.set(rightNs, 'EXTRA');
                field.statuses.set(leftNs, 'MISSING');
              }
            });
          });

          const detailColumns = [
            {
              title: 'Field Key',
              dataIndex: 'fieldKey',
              key: 'fieldKey',
              width: 280,
              fixed: 'left' as const,
              sorter: (a: any, b: any) => a.fieldKey.localeCompare(b.fieldKey),
              render: (text: string) => (
                <code style={{ fontSize: '12px', color: '#1890ff' }}>
                  {text}
                </code>
              ),
            },
            {
              title: 'Status',
              dataIndex: 'overallStatus',
              key: 'overallStatus',
              width: 100,
              fixed: 'left' as const,
              filters: [
                { text: 'Match', value: 'MATCH' },
                { text: 'Different', value: 'DIFFERENT' },
                { text: 'Missing', value: 'MISSING' },
                { text: 'Extra', value: 'EXTRA' },
              ],
              onFilter: (value: any, record: any) => record.overallStatus === value,
              sorter: (a: any, b: any) => a.overallStatus.localeCompare(b.overallStatus),
              render: (status: string) => {
                let color = 'default';
                let text = status;
                let icon = null;
                
                if (status === 'MATCH') {
                  color = 'success';
                  text = 'MATCH';
                  icon = <CheckCircle size={14} />;
                } else if (status === 'DIFFERENT') {
                  color = 'warning';
                  text = 'DIFF';
                  icon = <AlertCircle size={14} />;
                } else if (status === 'MISSING') {
                  color = 'error';
                  text = 'MISS';
                  icon = <XCircle size={14} />;
                } else if (status === 'EXTRA') {
                  color = 'processing';
                  text = 'EXTRA';
                  icon = <Plus size={14} />;
                }
                
                return (
                  <Tag 
                    color={color}
                    style={{ 
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: 4,
                      margin: 0,
                      fontSize: 11,
                    }}
                  >
                    {icon}
                    <span>{text}</span>
                  </Tag>
                );
              },
            },
            ...namespaces.map((ns) => {
              const nsStatusData = selectedObject[ns];
              const nsStatus = nsStatusData?.status || 'UNKNOWN';
              
              // ns is the full label from backend (e.g., "vim/namespace (Baseline)")
              // Format display: "cluster/namespace" → "cluster / namespace"
              const displayNs = ns.includes('/') 
                ? ns.split('/').map(part => part.trim()).join(' / ')
                : ns;
              
              return {
                title: (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                    <span>{displayNs}</span>
                    <Tag 
                      color={getStatusColor(nsStatus)} 
                      style={{ fontSize: 10, padding: '0 4px', margin: 0 }}
                    >
                      {nsStatus === 'BASELINE' ? 'BASE' : nsStatus === 'IDENTICAL' ? 'OK' : nsStatus}
                    </Tag>
                  </div>
                ),
                dataIndex: ns,
                key: ns,
                width: 250,
                filters: [
                  { text: 'Match', value: 'MATCH' },
                  { text: 'Different', value: 'DIFFERENT' },
                  { text: 'Missing', value: 'MISSING' },
                  { text: 'Extra', value: 'EXTRA' },
                ],
                onFilter: (value: any, record: any) => record[`${ns}_status`] === value,
                sorter: (a: any, b: any) => {
                  const valA = String(a[ns] || '');
                  const valB = String(b[ns] || '');
                  return valA.localeCompare(valB);
                },
                render: (_: any, record: any) => {
                  const value = record[ns];
                  const status = record[`${ns}_status`];
                  
                  if (value === null || value === undefined || value === '') {
                    return <span style={{ color: '#999', fontStyle: 'italic', fontSize: 12 }}>(empty)</span>;
                  }
                  
                  let backgroundColor = '#fff';
                  let color = '#000';
                  let borderLeft = 'none';
                  
                  // Get baseline value (first namespace is baseline)
                  const baselineNs = namespaces[0];
                  const baselineValue = record[baselineNs];
                  const isBaseline = ns === baselineNs;
                  
                  // Determine color based on comparison with baseline
                  if (status === 'MISSING') {
                    backgroundColor = '#fff2f0'; // error/red
                    color = '#cf1322';
                    borderLeft = '3px solid #ff4d4f';
                  } else if (status === 'EXTRA') {
                    backgroundColor = '#e6f7ff'; // processing/blue for extra field
                    color = '#1890ff';
                    borderLeft = '3px solid #1890ff';
                  } else if (isBaseline) {
                    // Baseline namespace - use blue (BASE color)
                    backgroundColor = '#e6f7ff';
                    color = '#1890ff';
                    borderLeft = '3px solid #1890ff';
                  } else if (value !== baselineValue) {
                    // Value different from baseline - use warning/yellow
                    backgroundColor = '#fffbe6';
                    color = '#d46b08';
                    borderLeft = '3px solid #faad14';
                  } else {
                    // Value matches baseline - use success/green
                    backgroundColor = '#f6ffed';
                    color = '#52c41a';
                    borderLeft = '3px solid #52c41a';
                  }
                  
                  return (
                    <div style={{ 
                      backgroundColor,
                      padding: '4px 8px',
                      borderRadius: 4,
                      color,
                      fontSize: 12,
                      borderLeft,
                    }}>
                      {value}
                    </div>
                  );
                },
              };
            }),
          ];

          const detailData = Array.from(fieldMap.entries())
            .filter(([fieldKey]) => {
              // Filter out object name itself (metadata.name, or simple object name)
              // Keep only nested fields
              return fieldKey !== selectedObject.objectName && 
                     fieldKey !== 'metadata.name' &&
                     fieldKey.includes('.');
            })
            .map(([fieldKey, field]) => {
            const row: any = {
              key: fieldKey,
              fieldKey,
            };
            
            let overallStatus = 'MATCH';
            const allValues = new Set<string>();
            
            // Check if object exists in baseline
            const baselineNs = namespaces[0];
            const baselineStatusData = selectedObject[baselineNs];
            const objectExistsInBaseline = baselineStatusData && 
                                          baselineStatusData.status !== 'MISSING';
            
            namespaces.forEach(ns => {
              let nsStatus = field.statuses.get(ns) || 'MISSING';
              const nsValue = field.values.get(ns) || '';
              
              // If object doesn't exist in baseline
              if (!objectExistsInBaseline) {
                if (ns === baselineNs) {
                  // Baseline namespace - field is MISSING
                  nsStatus = 'MISSING';
                } else if (nsValue) {
                  // Other namespaces with value - field is EXTRA
                  nsStatus = 'EXTRA';
                } else {
                  // Other namespaces without value - field is MISSING
                  nsStatus = 'MISSING';
                }
              }
              
              row[ns] = nsValue;
              row[`${ns}_status`] = nsStatus;
              
              // Collect all unique values (excluding empty)
              if (nsValue) {
                allValues.add(nsValue);
              }
              
              // Check for MISSING, EXTRA, or DIFFERENT status
              if (nsStatus === 'MISSING') {
                if (overallStatus !== 'EXTRA') {
                  overallStatus = 'MISSING';
                }
              } else if (nsStatus === 'EXTRA') {
                overallStatus = 'EXTRA';
              } else if (nsStatus === 'DIFFERENT' && overallStatus !== 'MISSING' && overallStatus !== 'EXTRA') {
                overallStatus = 'DIFFERENT';
              }

            });
            
            // If there are multiple different values, it's DIFFERENT
            if (allValues.size > 1 && overallStatus === 'MATCH') {
              overallStatus = 'DIFFERENT';
            }
            
            row.overallStatus = overallStatus;
            
            return row;
          });
          
          const fieldStats = {
            total: detailData.length,
            match: detailData.filter(d => d.overallStatus === 'MATCH').length,
            different: detailData.filter(d => d.overallStatus === 'DIFFERENT').length,
            missing: detailData.filter(d => d.overallStatus === 'MISSING').length,
            extra: detailData.filter(d => d.overallStatus === 'EXTRA').length,
          };

          return (
            <div>
              <Card style={{ marginBottom: 16 }}>
                <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: 16 }}>
                  <Statistic
                    title="Total Fields"
                    value={fieldStats.total}
                    valueStyle={{ fontSize: 20 }}
                  />
                  <Statistic
                    title="Match"
                    value={fieldStats.match}
                    valueStyle={{ color: '#52c41a', fontSize: 20 }}
                    prefix={<CheckCircle size={16} />}
                  />
                  <Statistic
                    title="Different"
                    value={fieldStats.different}
                    valueStyle={{ color: '#fa8c16', fontSize: 20 }}
                    prefix={<AlertCircle size={16} />}
                  />
                  <Statistic
                    title="Missing"
                    value={fieldStats.missing}
                    valueStyle={{ color: '#ff4d4f', fontSize: 20 }}
                    prefix={<XCircle size={16} />}
                  />
                  <Statistic
                    title="Extra"
                    value={fieldStats.extra}
                    valueStyle={{ color: '#1890ff', fontSize: 20 }}
                    prefix={<Plus size={16} />}
                  />
                </div>
              </Card>

              <Table
                columns={detailColumns}
                dataSource={detailData}
                rowKey="key"
                pagination={{ 
                  pageSize: 50,
                  showSizeChanger: true,
                  showTotal: (total) => `Total ${total} fields`
                }}
                scroll={{ x: 'max-content', y: 500 }}
                bordered
                size="small"
              />
            </div>
          );
        })()}
      </Modal>
    </>
  );
};
