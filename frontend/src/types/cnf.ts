/**
 * CNF Checklist Types
 */

export interface CNFChecklistItem {
  vimName: string;
  namespace: string;
  kind: string;
  objectName: string;
  fieldKey: string;
  manoValue: string;
}

export interface CNFChecklistRequest {
  items: CNFChecklistItem[];
  description?: string;
  matchingStrategy?: 'exact' | 'value' | 'identity'; // Default: 'value'
  ignoreFields?: string[]; // Custom ignore fields for this validation
}

/**
 * CNF Validation Result Types
 */
export interface CnfChecklistResult {
  kind: string;
  objectName: string;
  fieldKey: string;
  baselineValue: string;
  actualValue: string | null;
  status: 'MATCH' | 'DIFFERENT' | 'MISSING_IN_RUNTIME' | 'IGNORED' | 'ERROR';
  message?: string;
}

export interface CnfSummary {
  totalFields: number;
  matchCount: number;
  differenceCount: number;
  missingCount: number;
  ignoredCount: number;
  errorCount: number;
}

export interface CnfComparison {
  vimName: string;
  namespace: string;
  items: CnfChecklistResult[];
  summary: CnfSummary;
}

export interface CnfOverallSummary {
  totalVimNamespaces: number;
  totalFields: number;
  totalMatches: number;
  totalIgnored: number;
  totalDifferences: number;
  totalMissing: number;
  totalErrors: number;
  executionTimeMs: number;
}

export interface CnfValidationResultJson {
  jobId: string;
  submittedAt: string;
  completedAt: string;
  description: string;
  summary: CnfOverallSummary;
  results: CnfComparison[];
}
