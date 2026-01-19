export interface ClusterNamespace {
  cluster: string;
  namespace: string;
}

export interface ValidationRequest {
  namespaces: string[]; // Format: "cluster/namespace"
  baselineYamlContent?: string; // Client-side YAML content
  exportExcel?: boolean;
  description?: string;
  ignoreFields?: string[]; // Custom ignore fields for this validation
}

export interface BatchValidationRequestItem {
  name: string;
  namespaces: string[];
  verbose?: boolean;
}

export interface BatchValidationRequest {
  requests: BatchValidationRequestItem[];
  globalSettings?: {
    parallel?: boolean;
    outputDir?: string;
  };
}

export interface BatchJobResult {
  requestName: string;
  jobId: string;
  status: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  success?: boolean;
  errorMessage?: string;
  outputPath?: string;
  executionTimeMs?: number;
  objectsCompared?: number;
  differencesFound?: number;
}

export interface BatchValidationJobResponse extends ValidationJobResponse {
  individualJobs?: BatchJobResult[];
}

export type JobStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface ValidationJobResponse {
  jobId: string;
  status: JobStatus;
  progress?: {
    currentStep: string;
    percentage: number;
  };
  message?: string;
  errorMessage?: string;
  submittedAt: string;
  startedAt?: string;
  completedAt?: string;
  reportPath?: string;
  downloadUrl?: string;
  jsonUrl?: string;
  successfulCount?: number;
  failedCount?: number;
  individualJobIds?: string[];
  validationName?: string;
  objectsCompared?: number;
  differencesFound?: number;
}

export interface ValidationResultJson {
  jobId: string;
  submittedAt: string;
  completedAt: string;
  description?: string;
  summary: {
    totalObjects: number;
    totalDifferences: number;
    namespacePairs: number;
    executionTimeMs: number;
  };
  comparisons: Record<string, NamespaceComparison>;
}

export interface NamespaceComparison {
  leftNamespace: string;
  rightNamespace: string;
  objectComparisons: Record<string, ObjectComparison>;
}

export interface ObjectComparison {
  objectId: string;
  objectType: string;
  fullMatch: boolean;
  differenceCount: number;
  items: ComparisonItem[];
}

export interface ComparisonItem {
  path?: string;  // For field-level comparison
  key?: string;   // For object-level comparison (object exists/missing)
  leftValue?: string;
  rightValue?: string;
  status: 'MATCH' | 'ONLY_IN_LEFT' | 'ONLY_IN_RIGHT' | 'VALUE_MISMATCH' | 'DIFFERENT';
  match?: boolean;
}

export interface ObjectDifference {
  objectName: string;
  kind: string;
  status: 'MISSING' | 'EXTRA' | 'DIFFERENT' | 'IDENTICAL';
  details?: string[];
}

export interface ComparisonResult {
  resourceName: string;
  kind: string;
  status: 'MATCH' | 'MISMATCH' | 'MISSING' | 'EXTRA';
  differences?: string[];
  namespace?: string;
  cluster?: string;
}

export interface NamespaceSearchResult {
  cluster: string;
  namespace: string;
  objectCount: number;
  description?: string;
}

// Re-export CNF types
export * from './cnf';
