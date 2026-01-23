import React, { useState, useEffect } from 'react';
import { 
  Upload, Button, Input, Select, Card, message, Space, Table, Tag, 
  Divider, Progress, Radio, Tooltip, Modal, List, Alert
} from 'antd';
import { 
  UploadOutlined, FileExcelOutlined, SearchOutlined, 
  DeleteOutlined, DownloadOutlined, EyeOutlined,
  CloseCircleOutlined, CheckCircleOutlined, SyncOutlined, 
  ClockCircleOutlined, ReloadOutlined, CloudUploadOutlined
} from '@ant-design/icons';
import type { UploadFile } from 'antd/es/upload/interface';
import { validationApi } from '../services/api';
import { NamespaceSearch } from '../components/NamespaceSearch';
import type { ClusterNamespace } from '../types';

const { Option } = Select;

interface NamespaceInfo {
  name: string;
  resourceCount: number;
  resourceKinds: string;
}

interface YamlFileEntry {
  fileName: string;
  yamlContent: string;
  description?: string;
}

interface ConversionJob {
  jobId: string;
  status: string;
  targetNamespace: string;
  fileCount: number;
  totalItems?: number;
  progress?: number;
  errorMessage?: string;
  submittedAt: string;
  completedAt?: string;
  flattenMode: string;
}

const BatchYamlToCNFPage: React.FC = () => {
  const [yamlFiles, setYamlFiles] = useState<YamlFileEntry[]>([]);
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  
  // Namespace search & selection (using ClusterNamespace format)
  const [selectedNamespaces, setSelectedNamespaces] = useState<ClusterNamespace[]>([]);
  
  // Extracted namespaces from YAML files
  const [extractedNamespaces, setExtractedNamespaces] = useState<NamespaceInfo[]>([]);
  const [extracting, setExtracting] = useState(false);
  
  const [flattenMode, setFlattenMode] = useState<string>('flat');
  const [description, setDescription] = useState<string>('');
  
  const [submitting, setSubmitting] = useState(false);
  
  const [jobs, setJobs] = useState<ConversionJob[]>([]);
  const [selectedJob, setSelectedJob] = useState<ConversionJob | null>(null);
  const [showJobModal, setShowJobModal] = useState(false);

  // Poll jobs periodically
  useEffect(() => {
    loadJobs();
    const interval = setInterval(loadJobs, 3000); // Poll every 3 seconds
    return () => clearInterval(interval);
  }, []);

  const loadJobs = async () => {
    try {
      const allJobs = await validationApi.getAllConversionJobs();
      setJobs(allJobs);
    } catch (error) {
      console.error('Failed to load jobs:', error);
    }
  };

  // Handle multiple YAML files upload
  const handleYamlUpload = async (file: File) => {
    try {
      const content = await file.text();
      
      const newFile: YamlFileEntry = {
        fileName: file.name,
        yamlContent: content,
        description: ''
      };
      
      setYamlFiles(prev => [...prev, newFile]);
      message.success(`File "${file.name}" added`);
      
      return false; // Prevent default upload
    } catch (error: any) {
      message.error(`Failed to read file: ${error.message}`);
      return false;
    }
  };

  // Remove a YAML file
  const removeYamlFile = (index: number) => {
    setYamlFiles(prev => prev.filter((_, i) => i !== index));
    message.info('File removed');
  };

  // Extract namespaces from all files
  const handleExtractNamespaces = async () => {
    if (yamlFiles.length === 0) {
      message.warning('Please upload YAML files first');
      return;
    }

    try {
      setExtracting(true);
      
      const response = await validationApi.extractNamespacesFromBatch(yamlFiles);
      
      if (response.success) {
        setExtractedNamespaces(response.namespaces);
        message.success(response.message);
      } else {
        message.error('Failed to extract namespaces');
      }
    } catch (error: any) {
      console.error('Failed to extract namespaces:', error);
      message.error(`Failed to extract namespaces: ${error.response?.data?.message || error.message}`);
    } finally {
      setExtracting(false);
    }
  };

  // Submit batch conversion job (creates one job per selected target)
  const handleSubmitBatchConversion = async () => {
    if (selectedNamespaces.length === 0) {
      message.warning('Please select at least one target namespace');
      return;
    }

    if (yamlFiles.length === 0) {
      message.warning('Please upload at least one YAML file');
      return;
    }

    try {
      setSubmitting(true);

      // Convert ClusterNamespace[] to targets format
      const targets = selectedNamespaces.map(ns => ({
        cluster: ns.cluster,
        namespace: ns.namespace
      }));

      const response = await validationApi.submitBatchConversion({
        targets: targets, // Array of {cluster, namespace} pairs
        yamlFiles: yamlFiles,
        flattenMode: flattenMode,
        description: description || undefined,
      });

      // Response is an array of jobs (one per namespace)
      const newJobs = Array.isArray(response) ? response : [response];
      message.success(`Submitted ${newJobs.length} conversion job(s)!`);
      
      // Reset form
      setYamlFiles([]);
      setFileList([]);
      setExtractedNamespaces([]);
      setDescription('');
      setSelectedNamespaces([]);
      
      // Reload jobs
      loadJobs();
      
    } catch (error: any) {
      console.error('Failed to submit batch conversion:', error);
      message.error(`Failed to submit: ${error.response?.data?.message || error.message}`);
    } finally {
      setSubmitting(false);
    }
  };

  // Download job Excel
  const handleDownloadJobExcel = async (jobId: string) => {
    try {
      const blob = await validationApi.downloadConversionJobExcel(jobId);
      
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `${jobId}.xlsx`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      
      message.success('Excel file downloaded!');
    } catch (error: any) {
      message.error(`Failed to download: ${error.message}`);
    }
  };

  // Download all jobs as ZIP
  const handleDownloadAllJobsZip = async () => {
    try {
      message.loading({ content: 'Preparing ZIP file...', key: 'downloadzip' });
      const blob = await validationApi.downloadAllConversionJobsZip();
      
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5);
      link.setAttribute('download', `cnf-checklists-all-${timestamp}.zip`);
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);
      
      message.success({ content: 'ZIP file downloaded!', key: 'downloadzip' });
    } catch (error: any) {
      message.error({ content: `Failed to download: ${error.message}`, key: 'downloadzip' });
    }
  };

  // Delete job
  const handleDeleteJob = async (jobId: string) => {
    try {
      await validationApi.deleteConversionJob(jobId);
      message.success('Job deleted');
      loadJobs();
    } catch (error: any) {
      message.error(`Failed to delete: ${error.message}`);
    }
  };

  // View job details
  const handleViewJob = (job: ConversionJob) => {
    setSelectedJob(job);
    setShowJobModal(true);
  };

  // Job status icon
  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'COMPLETED':
        return <CheckCircleOutlined style={{ color: '#52c41a' }} />;
      case 'PROCESSING':
        return <SyncOutlined spin style={{ color: '#1890ff' }} />;
      case 'FAILED':
        return <CloseCircleOutlined style={{ color: '#ff4d4f' }} />;
      default:
        return <ClockCircleOutlined style={{ color: '#faad14' }} />;
    }
  };

  const jobColumns = [
    {
      title: 'Job ID',
      dataIndex: 'jobId',
      key: 'jobId',
      width: 200,
      render: (text: string) => (
        <Tooltip title={text}>
          <span style={{ fontFamily: 'monospace', fontSize: '12px' }}>
            {text.substring(0, 25)}...
          </span>
        </Tooltip>
      ),
    },
    {
      title: 'Target Namespace',
      dataIndex: 'targetNamespace',
      key: 'targetNamespace',
      render: (ns: string) => <Tag color="blue">{ns}</Tag>,
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <Tag icon={getStatusIcon(status)} color={
          status === 'COMPLETED' ? 'success' :
          status === 'PROCESSING' ? 'processing' :
          status === 'FAILED' ? 'error' : 'default'
        }>
          {status}
        </Tag>
      ),
    },
    {
      title: 'Progress',
      dataIndex: 'progress',
      key: 'progress',
      render: (progress: number) => (
        <Progress percent={progress || 0} size="small" style={{ width: 100 }} />
      ),
    },
    {
      title: 'Files',
      dataIndex: 'fileCount',
      key: 'fileCount',
    },
    {
      title: 'Items',
      dataIndex: 'totalItems',
      key: 'totalItems',
      render: (items: number) => items || '-',
    },
    {
      title: 'Submitted',
      dataIndex: 'submittedAt',
      key: 'submittedAt',
      render: (date: string) => new Date(date).toLocaleString(),
    },
    {
      title: 'Actions',
      key: 'actions',
      render: (record: ConversionJob) => (
        <Space>
          <Tooltip title="View Details">
            <Button
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewJob(record)}
            />
          </Tooltip>
          {record.status === 'COMPLETED' && (
            <Tooltip title="Download Excel">
              <Button
                size="small"
                type="primary"
                icon={<DownloadOutlined />}
                onClick={() => handleDownloadJobExcel(record.jobId)}
              />
            </Tooltip>
          )}
          <Tooltip title="Delete">
            <Button
              size="small"
              danger
              icon={<DeleteOutlined />}
              onClick={() => handleDeleteJob(record.jobId)}
            />
          </Tooltip>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: '24px' }}>
      <Card title="Batch YAML to CNF Checklist Converter" bordered={false}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          
          {/* Configuration */}
          <Card type="inner" title="Configuration">
            <Space direction="vertical" style={{ width: '100%' }}>
              <div>
                <label style={{ fontWeight: 500, marginBottom: 8, display: 'block' }}>
                  Flatten Mode
                </label>
                <Radio.Group value={flattenMode} onChange={(e) => setFlattenMode(e.target.value)}>
                  <Radio value="flat">
                    <Tooltip title="Traditional flatten - simpler structure">
                      Flat (Standard)
                    </Tooltip>
                  </Radio>
                  <Radio value="semantic">
                    <Tooltip title="Semantic flatten - preserves nested structure">
                      Semantic (V2)
                    </Tooltip>
                  </Radio>
                </Radio.Group>
              </div>

              <div>
                <label style={{ fontWeight: 500, marginBottom: 8, display: 'block' }}>
                  Description (Optional)
                </label>
                <Input
                  placeholder="Job description..."
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  style={{ maxWidth: 600 }}
                />
              </div>
            </Space>
          </Card>

          {/* Upload YAML Files */}
          <Card type="inner" title="Upload YAML Files">
            <Space direction="vertical" style={{ width: '100%' }}>
              <Upload
                beforeUpload={handleYamlUpload}
                fileList={fileList}
                onChange={({ fileList }) => setFileList(fileList)}
                multiple
                accept=".yaml,.yml"
                showUploadList={false}
              >
                <Button icon={<UploadOutlined />}>Select YAML Files (Multiple)</Button>
              </Upload>

              {yamlFiles.length > 0 && (
                <div>
                  <Divider>Uploaded Files ({yamlFiles.length})</Divider>
                  <List
                    size="small"
                    bordered
                    dataSource={yamlFiles}
                    renderItem={(item, index) => (
                      <List.Item
                        actions={[
                          <Button 
                            size="small" 
                            danger 
                            icon={<DeleteOutlined />}
                            onClick={() => removeYamlFile(index)}
                          >
                            Remove
                          </Button>
                        ]}
                      >
                        <Space>
                          <FileExcelOutlined />
                          <span>{item.fileName}</span>
                          <Tag color="blue">{(item.yamlContent.length / 1024).toFixed(1)} KB</Tag>
                        </Space>
                      </List.Item>
                    )}
                  />
                </div>
              )}
            </Space>
          </Card>

          {/* Namespace Selection */}
          <Card type="inner" title="Select Target Namespaces">
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              
              <Alert
                message="Info"
                description="Search and select namespaces from your cluster, or extract them from uploaded YAML files."
                type="info"
                showIcon
              />

              {/* Search from Cluster - using NamespaceSearch component */}
              <div>
                <label style={{ fontWeight: 500, marginBottom: 8, display: 'block' }}>
                  Search Namespaces from Cluster
                </label>
                <NamespaceSearch
                  selectedNamespaces={selectedNamespaces}
                  onNamespacesChange={setSelectedNamespaces}
                />
              </div>

              <Divider>OR</Divider>

              {/* Extract from YAML files */}
              <div>
                <label style={{ fontWeight: 500, marginBottom: 8, display: 'block' }}>
                  Extract Namespaces from YAML Files
                </label>
                <Button 
                  icon={<SearchOutlined />}
                  onClick={handleExtractNamespaces}
                  loading={extracting}
                  disabled={yamlFiles.length === 0}
                >
                  Extract Namespaces
                </Button>

                {extractedNamespaces.length > 0 && (
                  <div style={{ marginTop: 12 }}>
                    <Alert
                      type="info"
                      message="Note: Extracted namespaces will use 'default-cluster' as cluster name. Use search from cluster for accurate cluster information."
                      style={{ marginBottom: 8 }}
                      showIcon
                    />
                    <Select
                      mode="multiple"
                      placeholder="Select namespaces from extracted list"
                      style={{ width: '100%', maxWidth: 600 }}
                      value={selectedNamespaces.map(ns => `${ns.cluster}:${ns.namespace}`)}
                      onChange={(values: string[]) => {
                        const namespaces = values.map(val => {
                          const [cluster, namespace] = val.split(':');
                          return { cluster, namespace };
                        });
                        setSelectedNamespaces(namespaces);
                      }}
                      optionFilterProp="children"
                      showSearch
                    >
                      {extractedNamespaces.map((ns) => (
                        <Option key={`default-cluster:${ns.name}`} value={`default-cluster:${ns.name}`}>
                          <Space>
                            <Tag color="gray">default-cluster</Tag>
                            <span>{ns.name}</span>
                            <Tag color="green">{ns.resourceCount} resources</Tag>
                            <Tag>{ns.resourceKinds}</Tag>
                          </Space>
                        </Option>
                      ))}
                    </Select>
                  </div>
                )}
              </div>

              {selectedNamespaces.length > 0 && (
                <Alert
                  message={`${selectedNamespaces.length} namespace${selectedNamespaces.length !== 1 ? 's' : ''} selected`}
                  description={
                    <div>
                      <p><strong>Selected Namespaces:</strong></p>
                      <ul style={{ marginBottom: 0, paddingLeft: 20 }}>
                        {selectedNamespaces.map((ns, idx) => (
                          <li key={idx}>
                            <Tag color="blue">{ns.cluster}</Tag> {ns.namespace}
                          </li>
                        ))}
                      </ul>
                      <p style={{ marginTop: 8, fontWeight: 500 }}>
                        ⚠️ Each namespace will create a separate conversion job with the uploaded YAML files.
                      </p>
                    </div>
                  }
                  type="success"
                  showIcon
                />
              )}
            </Space>
          </Card>

          {/* Submit */}
          <Card type="inner">
            <Space>
              <Button
                type="primary"
                size="large"
                icon={<CloudUploadOutlined />}
                onClick={handleSubmitBatchConversion}
                loading={submitting}
                disabled={yamlFiles.length === 0 || selectedNamespaces.length === 0}
              >
                Submit Conversion Jobs ({selectedNamespaces.length} job{selectedNamespaces.length !== 1 ? 's' : ''})
              </Button>

              <Button
                size="large"
                onClick={() => {
                  setYamlFiles([]);
                  setFileList([]);
                  setSelectedNamespaces([]);
                  setExtractedNamespaces([]);
                  setDescription('');
                }}
              >
                Reset
              </Button>
            </Space>
          </Card>

          {/* Jobs Table */}
          <Card type="inner" title="Conversion Jobs" extra={
            <Space>
              <Button 
                type="primary"
                icon={<DownloadOutlined />}
                onClick={handleDownloadAllJobsZip}
                disabled={jobs.filter(j => j.status === 'COMPLETED').length === 0}
              >
                Download All (ZIP)
              </Button>
              <Button icon={<ReloadOutlined />} onClick={loadJobs}>
                Refresh
              </Button>
            </Space>
          }>
            <Table
              columns={jobColumns}
              dataSource={jobs}
              rowKey="jobId"
              size="small"
              pagination={{ pageSize: 10 }}
            />
          </Card>

        </Space>
      </Card>

      {/* Job Detail Modal */}
      <Modal
        title="Job Details"
        open={showJobModal}
        onCancel={() => setShowJobModal(false)}
        footer={[
          <Button key="close" onClick={() => setShowJobModal(false)}>
            Close
          </Button>,
          selectedJob?.status === 'COMPLETED' && (
            <Button
              key="download"
              type="primary"
              icon={<DownloadOutlined />}
              onClick={() => {
                if (selectedJob) {
                  handleDownloadJobExcel(selectedJob.jobId);
                }
              }}
            >
              Download Excel
            </Button>
          ),
        ]}
        width={700}
      >
        {selectedJob && (
          <Space direction="vertical" style={{ width: '100%' }}>
            <div>
              <strong>Job ID:</strong> <code>{selectedJob.jobId}</code>
            </div>
            <div>
              <strong>Target Namespace:</strong> <Tag color="blue">{selectedJob.targetNamespace}</Tag>
            </div>
            <div>
              <strong>Status:</strong> <Tag icon={getStatusIcon(selectedJob.status)}>{selectedJob.status}</Tag>
            </div>
            <div>
              <strong>Progress:</strong> <Progress percent={selectedJob.progress || 0} />
            </div>
            <div>
              <strong>YAML Files:</strong> {selectedJob.fileCount}
            </div>
            <div>
              <strong>Total Items:</strong> {selectedJob.totalItems || '-'}
            </div>
            <div>
              <strong>Flatten Mode:</strong> <Tag>{selectedJob.flattenMode}</Tag>
            </div>
            <div>
              <strong>Submitted:</strong> {new Date(selectedJob.submittedAt).toLocaleString()}
            </div>
            {selectedJob.completedAt && (
              <div>
                <strong>Completed:</strong> {new Date(selectedJob.completedAt).toLocaleString()}
              </div>
            )}
            {selectedJob.errorMessage && (
              <Alert
                message="Error"
                description={selectedJob.errorMessage}
                type="error"
                showIcon
              />
            )}
          </Space>
        )}
      </Modal>
    </div>
  );
};

export default BatchYamlToCNFPage;
