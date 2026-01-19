import axios from 'axios';
import type { ValidationRequest, BatchValidationRequest, ValidationJobResponse, ValidationResultJson, NamespaceSearchResult } from '../types';
import type { CNFChecklistRequest, CnfValidationResultJson } from '../types/cnf';

const api = axios.create({
  baseURL: '/kvalidator/api',
  headers: {
    'Content-Type': 'application/json',
  },
});

export const validationApi = {
  // Get available clusters
  getClusters: async (): Promise<string[]> => {
    const { data } = await api.get('/kubernetes/clusters');
    return data;
  },

  // Get namespaces for a cluster
  getNamespaces: async (cluster: string): Promise<string[]> => {
    const { data } = await api.get('/kubernetes/namespaces', {
      params: { cluster },
    });
    return data;
  },

  // Search namespaces by keyword
  searchNamespaces: async (keyword: string): Promise<NamespaceSearchResult[]> => {
    const { data } = await api.get('/kubernetes/namespaces/search', {
      params: { keyword },
    });
    return data.data || data; // Handle both {data: [...]} and [...] formats
  },

  // Submit validation job
  submitValidation: async (request: ValidationRequest): Promise<ValidationJobResponse> => {
    const { data } = await api.post('/validate', request);
    return data;
  },

  // Submit batch validation job
  submitBatchValidation: async (request: BatchValidationRequest): Promise<ValidationJobResponse> => {
    // Note: The backend might return a similar JobResponse or a specific BatchResponse. 
    // Assuming it returns a Job ID like single validation or immediately returns results?
    // Based on API docs, it seems to be /validate/batch.
    // Let's assume it works similarly with async processing.
    const { data } = await api.post('/validate/batch', request);
    return data;
  },

  // Submit CNF checklist validation job
  submitCNFChecklistValidation: async (request: CNFChecklistRequest): Promise<ValidationJobResponse> => {
    const { data } = await api.post('/validate/cnf-checklist', request);
    return data;
  },

  // Submit CNF checklist validation and get results immediately (Sync)
  submitCNFChecklistValidationSync: async (request: CNFChecklistRequest): Promise<ValidationResultJson> => {
    const { data } = await api.post('/validate/cnf-checklist/sync', request);
    return data;
  },

  // Get validation job status
  getJobStatus: async (jobId: string): Promise<ValidationJobResponse> => {
    const { data } = await api.get(`/validate/${jobId}`);
    return data;
  },

  // Get validation results (when completed)
  getValidationResults: async (jobId: string): Promise<ValidationResultJson> => {
    const { data } = await api.get(`/validate/${jobId}/json`);
    return data;
  },

  // Get CNF-specific validation results (when completed)
  getCnfValidationResults: async (jobId: string): Promise<CnfValidationResultJson> => {
    const { data } = await api.get(`/validate/${jobId}/cnf-json`);
    return data;
  },

  // Get individual jobs for a batch job
  getBatchIndividualJobs: async (batchJobId: string): Promise<{batchJobId: string, individualJobs: string[], totalJobs: number}> => {
    const { data } = await api.get(`/validate/batch/${batchJobId}/jobs`);
    return data;
  },

  // Upload and parse JSON file
  uploadJsonFile: async (file: File): Promise<{ success: boolean; message: string; itemCount: number; items: any[] }> => {
    const { data } = await api.post('/cnf-checklist/upload/json', file, {
      headers: {
        'Content-Type': 'application/octet-stream',
      },
    });
    return data;
  },

  // Upload and parse Excel file
  uploadExcelFile: async (file: File): Promise<{ success: boolean; message: string; itemCount: number; items: any[] }> => {
    const { data } = await api.post('/cnf-checklist/upload/excel', file, {
      headers: {
        'Content-Type': 'application/octet-stream',
      },
    });
    return data;
  },

  // Download Excel template
  downloadExcelTemplate: async (): Promise<Blob> => {
    const { data } = await api.get('/cnf-checklist/template/excel', {
      responseType: 'blob',
    });
    return data;
  },

  // Poll job status until completed
  pollJobStatus: async (
    jobId: string,
    onProgress?: (job: ValidationJobResponse) => void,
    maxAttempts = 60,
    intervalMs = 2000
  ): Promise<ValidationJobResponse> => {
    for (let i = 0; i < maxAttempts; i++) {
      const job = await validationApi.getJobStatus(jobId);
      
      if (onProgress) {
        onProgress(job);
      }
      
      if (job.status === 'COMPLETED' || job.status === 'FAILED') {
        return job;
      }
      
      // Wait before next poll
      await new Promise(resolve => setTimeout(resolve, intervalMs));
    }
    
    throw new Error('Validation timeout - job did not complete in time');
  },
};

export default api;
