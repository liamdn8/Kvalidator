import React, { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { Button, Card, Col, Row, Statistic, Table, Space, Alert, Tag, Tabs } from 'antd';
import { DownloadOutlined, ReloadOutlined, FileTextOutlined, LoadingOutlined } from '@ant-design/icons';
import * as XLSX from 'xlsx';
import { CnfValidationResultJson, ValidationResultJson, ValidationJobResponse } from '../types';
import { validationApi } from '../services/api';

interface CnfChecklistResultsProps {
  jobId?: string;
}

interface JobStatusWithResult {
  status: ValidationJobResponse;
  result: CnfValidationResultJson | ValidationResultJson | null;
}

export const CnfChecklistResults: React.FC<CnfChecklistResultsProps> = ({ jobId: propJobId }) => {
  const { jobId: paramJobId } = useParams<{ jobId: string }>();
  const jobId = propJobId || paramJobId!;

  const [batchJob, setBatchJob] = useState<ValidationJobResponse | null>(null);
  const [individualJobs, setIndividualJobs] = useState<Map<string, JobStatusWithResult>>(new Map());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const fetchResults = async () => {
    try {
      const batchStatus = await validationApi.getJobStatus(jobId);
      setBatchJob(batchStatus);

      if (batchStatus.status === 'COMPLETED') {
        const jobsMap = new Map<string, JobStatusWithResult>();
        
        try {
          // Get individual jobs for this batch
          const batchInfo = await validationApi.getBatchIndividualJobs(jobId);
          console.log('Individual jobs for batch:', batchInfo);
          
          // Fetch results for each individual job
          for (const individualJobId of batchInfo.individualJobs) {
            try {
              // Try CNF results first, then fallback to regular results
              let result: CnfValidationResultJson | ValidationResultJson | null = null;
              try {
                result = await validationApi.getCnfValidationResults(individualJobId);
                console.log('Got CNF results for job:', individualJobId);
              } catch {
                try {
                  result = await validationApi.getValidationResults(individualJobId);
                  console.log('Got standard results for job:', individualJobId);
                } catch (err) {
                  console.warn('No results found for job:', individualJobId, err);
                }
              }
              
              // Get individual job status
              const individualStatus = await validationApi.getJobStatus(individualJobId);
              jobsMap.set(individualJobId, { status: individualStatus, result });
            } catch (err) {
              console.error(`Failed to fetch result for individual job ${individualJobId}:`, err);
              jobsMap.set(individualJobId, { status: batchStatus, result: null });
            }
          }
        } catch (err) {
          console.warn('Failed to get individual jobs, treating as single job:', err);
          // Fallback: treat as single job
          try {
            let result: CnfValidationResultJson | ValidationResultJson | null = null;
            try {
              result = await validationApi.getCnfValidationResults(jobId);
            } catch {
              result = await validationApi.getValidationResults(jobId);
            }
            jobsMap.set(jobId, { status: batchStatus, result });
          } catch (fallbackErr) {
            console.error(`Failed to fetch result for single job ${jobId}:`, fallbackErr);
            jobsMap.set(jobId, { status: batchStatus, result: null });
          }
        }
        
        setIndividualJobs(jobsMap);
      } else {
        const jobsMap = new Map();
        jobsMap.set(jobId, { status: batchStatus, result: null });
        setIndividualJobs(jobsMap);
      }
    } catch (err) {
      console.error('Error fetching job results:', err);
      setError(err instanceof Error ? err.message : 'Unknown error occurred');
    }
  };

  useEffect(() => {
    let mounted = true;
    
    const loadResults = async () => {
      setLoading(true);
      await fetchResults();
      if (mounted) setLoading(false);
    };

    loadResults();
    
    return () => { mounted = false; };
  }, [jobId]);

  const handleRefresh = async () => {
    setRefreshing(true);
    await fetchResults();
    setRefreshing(false);
  };

  const exportToExcel = () => {
    const workbook = XLSX.utils.book_new();
    
    // Summary sheet
    const summaryData = [
      ['CNF Checklist Validation Results'],
      ['Job ID', jobId],
      ['Submitted At', batchJob?.submittedAt ? new Date(batchJob.submittedAt).toLocaleString() : ''],
      ['Completed At', batchJob?.completedAt ? new Date(batchJob.completedAt).toLocaleString() : ''],
      ['Total Jobs', individualJobs.size],
      [],
      ['Job Name', 'Status', 'Fields', 'Matches', 'Differences']
    ];

    individualJobs.forEach((jobData, jobId) => {
      const result = jobData.result;
      let fieldCount = 0, matchCount = 0, diffCount = 0;
      
      // Handle both CNF and standard formats
      const isCnfFormat = result && 'results' in result;
      if (isCnfFormat) {
        const cnfResult = result as CnfValidationResultJson;
        fieldCount = cnfResult.summary.totalFields;
        matchCount = cnfResult.summary.totalMatches;
        diffCount = cnfResult.summary.totalDifferences;
      } else if (result) {
        const stdResult = result as ValidationResultJson;
        fieldCount = Object.keys(stdResult.comparisons).reduce((sum, key) => 
          sum + Object.keys(stdResult.comparisons[key].objectComparisons || {}).length, 0);
        diffCount = stdResult.summary?.totalDifferences || 0;
        matchCount = fieldCount - diffCount;
      }
      
      summaryData.push([
        jobData.status.validationName || jobId,
        jobData.status.status,
        fieldCount,
        matchCount,
        diffCount
      ]);
    });

    const summarySheet = XLSX.utils.aoa_to_sheet(summaryData);
    XLSX.utils.book_append_sheet(workbook, summarySheet, 'Summary');

    // Individual job sheets
    individualJobs.forEach((jobData, jobIdKey) => {
      if (jobData.result) {
        const result = jobData.result;
        const isCnfFormat = 'results' in result;
        const sheetData = [['VIM/Namespace', 'Object', 'Field', 'Expected', 'Actual', 'Status', 'Matched Field']];
        
        if (isCnfFormat) {
          const cnfResult = result as CnfValidationResultJson;
          cnfResult.results.forEach((nsResult: any) => {
            nsResult.items.forEach((item: any) => {
              sheetData.push([
                `${nsResult.vimName}/${nsResult.namespace}`,
                item.objectName,
                item.fieldKey,
                item.baselineValue || '',
                item.actualValue || '',
                item.status,
                item.matchedFieldKey || ''
              ]);
            });
          });
        }
        
        const sheet = XLSX.utils.aoa_to_sheet(sheetData);
        const sheetName = (jobData.status.validationName || jobIdKey).substring(0, 31);
        XLSX.utils.book_append_sheet(workbook, sheet, sheetName);
      }
    });

    XLSX.writeFile(workbook, `cnf-checklist-results-${jobId}.xlsx`);
  };

  const renderOverview = () => {
    let totalFields = 0;
    let totalMatches = 0;
    let totalDifferences = 0;
    let completedJobs = 0;

    individualJobs.forEach((jobData) => {
      if (jobData.status.status === 'COMPLETED' && jobData.result) {
        completedJobs++;
        const result = jobData.result;
        
        // Handle CNF format only (since this is CNF checklist)
        const isCnfFormat = 'results' in result;
        if (isCnfFormat) {
          const cnfResult = result as CnfValidationResultJson;
          totalFields += cnfResult.summary.totalFields;
          totalMatches += cnfResult.summary.totalMatches;
          totalDifferences += cnfResult.summary.totalDifferences;
        }
      }
    });

    return (
      <Card title="CNF Checklist Validation Overview" style={{ marginBottom: 16 }}>
        <Row gutter={16}>
          <Col span={6}>
            <Statistic title="Total Jobs" value={individualJobs.size} />
          </Col>
          <Col span={6}>
            <Statistic title="Completed" value={completedJobs} valueStyle={{ color: '#52c41a' }} />
          </Col>
          <Col span={6}>
            <Statistic title="Matches" value={totalMatches} valueStyle={{ color: '#52c41a' }} />
          </Col>
          <Col span={6}>
            <Statistic title="Differences" value={totalDifferences} valueStyle={{ color: '#ff4d4f' }} />
          </Col>
        </Row>
        
        {totalDifferences === 0 && totalMatches > 0 && (
          <Alert
            message="All CNF checklist items match! Your configuration is compliant."
            type="success"
            showIcon
            style={{ marginTop: 16 }}
          />
        )}
      </Card>
    );
  };

  const renderJobsTable = () => {
    const columns = [
      {
        title: 'Job Name',
        dataIndex: 'jobName',
        key: 'jobName',
        render: (_: any, record: any) => record.status.validationName || record.jobId,
      },
      {
        title: 'Status',
        dataIndex: 'status',
        key: 'status',
        render: (_: any, record: any) => (
          <Tag color={record.status.status === 'COMPLETED' ? 'green' : 'default'}>
            {record.status.status}
          </Tag>
        ),
      },
      {
        title: 'Fields',
        dataIndex: 'fields',
        key: 'fields',
        render: (_: any, record: any) => {
          if (record.result && 'results' in record.result) {
            return (record.result as CnfValidationResultJson).summary.totalFields;
          }
          return 0;
        },
      },
      {
        title: 'Matches',
        dataIndex: 'matches',
        key: 'matches',
        render: (_: any, record: any) => {
          if (record.result && 'results' in record.result) {
            const matches = (record.result as CnfValidationResultJson).summary.totalMatches;
            return <span style={{ color: '#52c41a' }}>{matches}</span>;
          }
          return 0;
        },
      },
      {
        title: 'Differences',
        dataIndex: 'differences',
        key: 'differences',
        render: (_: any, record: any) => {
          if (record.result && 'results' in record.result) {
            const diffs = (record.result as CnfValidationResultJson).summary.totalDifferences;
            return <span style={{ color: '#ff4d4f' }}>{diffs}</span>;
          }
          return 0;
        },
      },
      {
        title: 'Match Rate',
        dataIndex: 'matchRate',
        key: 'matchRate',
        render: (_: any, record: any) => {
          if (record.result && 'results' in record.result) {
            const cnfResult = record.result as CnfValidationResultJson;
            const fieldCount = cnfResult.summary.totalFields;
            const matchCount = cnfResult.summary.totalMatches;
            const rate = fieldCount > 0 ? ((matchCount / fieldCount) * 100).toFixed(1) : '0';
            return (
              <Tag color={cnfResult.summary.totalDifferences === 0 ? 'green' : 'red'}>
                {rate}%
              </Tag>
            );
          }
          return <Tag color="default">0%</Tag>;
        },
      },
    ];

    const dataSource = Array.from(individualJobs.entries()).map(([jobId, jobData]) => ({
      key: jobId,
      jobId,
      ...jobData,
    }));

    return (
      <Card title="Individual Job Results" style={{ marginBottom: 16 }}>
        <Table 
          columns={columns}
          dataSource={dataSource}
          size="small"
        />
      </Card>
    );
  };

  const renderIndividualResults = () => {
    return (
      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        {Array.from(individualJobs.entries()).map(([jobId, jobData]) => {
          if (!jobData.result || !('results' in jobData.result)) {
            return null;
          }

          const cnfResult = jobData.result as CnfValidationResultJson;
          
          return (
            <Card 
              key={jobId}
              title={
                <Space>
                  <FileTextOutlined />
                  {jobData.status.validationName || jobId}
                  <Tag color={cnfResult.summary.totalDifferences === 0 ? 'green' : 'red'}>
                    {cnfResult.summary.totalMatches} matches, {cnfResult.summary.totalDifferences} differences
                  </Tag>
                </Space>
              }
            >
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {cnfResult.results.map((nsResult: any, nsIndex: number) => (
                  <Card 
                    key={nsIndex}
                    size="small"
                    title={`${nsResult.vimName} / ${nsResult.namespace}`}
                    style={{ border: '1px solid #d9d9d9' }}
                  >
                    <Table
                      size="small"
                      columns={[
                        { title: 'Object', dataIndex: 'objectName', key: 'objectName' },
                        {
                          title: 'Field',
                          dataIndex: 'fieldKey',
                          key: 'fieldKey',
                          render: (fieldKey: string, record: any) => (
                            <Space>
                              <code style={{ fontSize: '12px', backgroundColor: '#f5f5f5', padding: '2px 4px', borderRadius: '2px' }}>
                                {fieldKey}
                              </code>
                              {record.matchedFieldKey && record.matchedFieldKey !== fieldKey && (
                                <Tag color="blue" style={{ fontSize: '10px' }}>
                                  → {record.matchedFieldKey}
                                </Tag>
                              )}
                            </Space>
                          ),
                        },
                        {
                          title: 'Expected',
                          dataIndex: 'baselineValue',
                          key: 'baselineValue',
                          render: (value: string) => (
                            <code style={{ fontSize: '12px', backgroundColor: '#e6f7ff', padding: '2px 4px', borderRadius: '2px' }}>
                              {value || 'N/A'}
                            </code>
                          ),
                        },
                        {
                          title: 'Actual',
                          dataIndex: 'actualValue',
                          key: 'actualValue',
                          render: (value: string) => (
                            <code style={{ fontSize: '12px', backgroundColor: '#f6ffed', padding: '2px 4px', borderRadius: '2px' }}>
                              {value || 'N/A'}
                            </code>
                          ),
                        },
                        {
                          title: 'Status',
                          dataIndex: 'status',
                          key: 'status',
                          render: (status: string) => (
                            <Tag color={status === 'MATCH' ? 'green' : 'red'}>
                              {status}
                            </Tag>
                          ),
                        },
                      ]}
                      dataSource={nsResult.items.map((item: any, idx: number) => ({ ...item, key: idx }))}
                      pagination={false}
                    />
                  </Card>
                ))}
              </Space>
            </Card>
          );
        })}
      </Space>
    );
  };

  if (loading) {
    return (
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '64px' }}>
        <Space>
          <LoadingOutlined style={{ fontSize: 24 }} spin />
          Loading CNF checklist results...
        </Space>
      </div>
    );
  }

  if (error) {
    return (
      <Alert
        message="Error"
        description={`Error loading results: ${error}`}
        type="error"
        showIcon
      />
    );
  }

  if (!batchJob) {
    return (
      <Alert
        message="Error"
        description="Batch job not found"
        type="error"
        showIcon
      />
    );
  }

  const { TabPane } = Tabs;

  return (
    <div style={{ padding: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
        <div>
          <h1 style={{ fontSize: '24px', fontWeight: 'bold', margin: 0 }}>CNF Checklist Results</h1>
          <p style={{ color: '#666', margin: '4px 0' }}>Job ID: {jobId}</p>
          {batchJob.submittedAt && (
            <p style={{ fontSize: '12px', color: '#999', margin: 0 }}>
              Submitted: {new Date(batchJob.submittedAt).toLocaleString()}
            </p>
          )}
        </div>
        <Space>
          <Button 
            icon={<ReloadOutlined spin={refreshing} />}
            onClick={handleRefresh}
            disabled={refreshing}
          >
            Refresh
          </Button>
          <Button 
            type="primary"
            icon={<DownloadOutlined />}
            onClick={exportToExcel}
            disabled={individualJobs.size === 0}
          >
            Export Excel
          </Button>
        </Space>
      </div>

      {renderOverview()}

      <Tabs defaultActiveKey="summary">
        <TabPane tab="Summary" key="summary">
          {renderJobsTable()}
        </TabPane>
        <TabPane tab="Detailed Results" key="details">
          {renderIndividualResults()}
        </TabPane>
      </Tabs>
    </div>
  );
};

export default CnfChecklistResults;