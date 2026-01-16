import type { CnfValidationResultJson } from '../types/cnf';
import type { ValidationResultJson } from '../types';
import { ValidationResults } from './ValidationResults';

interface CnfValidationResultsProps {
  result: CnfValidationResultJson;
}

/**
 * Mapper function: Convert CNF validation result to standard ValidationResultJson format
 * This allows reusing the ValidationResults component for CNF validation
 */
const mapCnfToValidationResult = (cnfResult: CnfValidationResultJson): ValidationResultJson => {
  const comparisons: Record<string, any> = {};
  
  // Process each CNF comparison (VIM/Namespace)
  cnfResult.results.forEach((cnfComp) => {
    const namespaceKey = `${cnfComp.vimName}/${cnfComp.namespace}`;
    
    // Create comparison key (baseline vs actual)
    const comparisonKey = `${namespaceKey} (Baseline)_vs_${namespaceKey} (Actual)`;
    
    // Group items by object
    const objectComparisonsMap = new Map<string, any[]>();
    
    cnfComp.items.forEach((item) => {
      const objectKey = `${item.kind}/${item.objectName}`;
      
      if (!objectComparisonsMap.has(objectKey)) {
        objectComparisonsMap.set(objectKey, []);
      }
      
      // Map CNF status to standard comparison status
      let comparisonStatus: 'MATCH' | 'DIFFERENT' | 'ONLY_IN_LEFT' | 'ONLY_IN_RIGHT' | 'VALUE_MISMATCH';
      
      if (item.status === 'MATCH') {
        comparisonStatus = 'MATCH';
      } else if (item.status === 'MISSING_IN_RUNTIME') {
        comparisonStatus = 'ONLY_IN_LEFT'; // Exists in baseline but not in runtime
      } else if (item.status === 'DIFFERENT') {
        comparisonStatus = 'VALUE_MISMATCH';
      } else {
        comparisonStatus = 'DIFFERENT';
      }
      
      objectComparisonsMap.get(objectKey)!.push({
        key: item.fieldKey,
        path: item.fieldKey,
        leftValue: item.baselineValue,
        rightValue: item.actualValue,
        status: comparisonStatus,
        match: item.status === 'MATCH'
      });
    });
    
    // Build objectComparisons
    const objectComparisons: Record<string, any> = {};
    
    objectComparisonsMap.forEach((items, objectKey) => {
      const firstItem = cnfComp.items.find(i => `${i.kind}/${i.objectName}` === objectKey);
      if (!firstItem) return;
      
      const differenceCount = items.filter(i => i.status !== 'MATCH').length;
      
      objectComparisons[objectKey] = {
        objectId: objectKey,
        objectType: firstItem.kind,
        fullMatch: differenceCount === 0,
        differenceCount: differenceCount,
        items: items
      };
    });
    
    comparisons[comparisonKey] = {
      leftNamespace: `${namespaceKey} (Baseline)`,
      rightNamespace: `${namespaceKey} (Actual)`,
      objectComparisons: objectComparisons
    };
  });
  
  // Calculate total objects
  const totalObjects = cnfResult.results.reduce((sum, comp) => {
    const uniqueObjects = new Set(comp.items.map(item => `${item.kind}/${item.objectName}`));
    return sum + uniqueObjects.size;
  }, 0);
  
  return {
    jobId: cnfResult.jobId,
    submittedAt: cnfResult.submittedAt,
    completedAt: cnfResult.completedAt,
    description: cnfResult.description,
    summary: {
      totalObjects: totalObjects,
      totalDifferences: cnfResult.summary.totalDifferences + cnfResult.summary.totalMissing,
      namespacePairs: cnfResult.summary.totalVimNamespaces,
      executionTimeMs: cnfResult.summary.executionTimeMs
    },
    comparisons: comparisons
  };
};

/**
 * CNF Validation Results Component
 * 
 * This component wraps the standard ValidationResults component by mapping
 * CNF validation result format to the standard validation result format.
 * This ensures consistent UI/UX across different validation types.
 */
export const CnfValidationResults = ({ result }: CnfValidationResultsProps) => {
  // Map CNF result to standard validation result format
  const mappedResult = mapCnfToValidationResult(result);
  
  // Reuse the standard ValidationResults component
  return <ValidationResults result={mappedResult} />;
};
