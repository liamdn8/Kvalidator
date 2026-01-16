import { useState, useEffect } from 'react';
import { Card, Button, Space, message, Radio, Alert } from 'antd';
import { Download, Upload as UploadIcon, FileCode, Eye } from 'lucide-react';
import type { ClusterNamespace } from '../types';

const { TextArea } = Input;
import { Input } from 'antd';

interface FullValidationConfigProps {
  selectedNamespaces?: ClusterNamespace[];
  ignoreFields?: string[];
  onImport?: (config: { namespaces: string[]; ignoreFields: string[] }) => void;
}

type ViewMode = 'preview' | 'yaml';

export const FullValidationConfig = ({ 
  selectedNamespaces = [], 
  ignoreFields = [],
  onImport 
}: FullValidationConfigProps) => {
  const [viewMode, setViewMode] = useState<ViewMode>('preview');
  const [fullConfig, setFullConfig] = useState('');

  useEffect(() => {
    generateFullConfig();
  }, [selectedNamespaces, ignoreFields]);

  const generateFullConfig = () => {
    const namespaceList = selectedNamespaces.map(ns => `${ns.cluster}/${ns.namespace}`);
    
    const configObj = {
      version: '1.0',
      description: 'KValidator Configuration',
      validation: {
        namespaces: namespaceList,
        ignoreFields: ignoreFields.sort(),
      },
      metadata: {
        createdAt: new Date().toISOString(),
        createdBy: 'KValidator Web UI',
      },
    };

    const yamlContent = `# KValidator Full Validation Configuration
# Generated: ${new Date().toLocaleString()}

version: "${configObj.version}"
description: "${configObj.description}"

# Namespaces to validate
validation:
  namespaces:
${namespaceList.length > 0 ? namespaceList.map(ns => `    - "${ns}"`).join('\n') : '    # No namespaces selected'}

  # Fields to ignore during comparison
  ignoreFields:
${ignoreFields.length > 0 ? ignoreFields.sort().map(f => `    - "${f}"`).join('\n') : '    # No ignore rules configured'}

metadata:
  createdAt: "${configObj.metadata.createdAt}"
  createdBy: "${configObj.metadata.createdBy}"
`;

    setFullConfig(yamlContent);
  };

  const exportFullConfig = () => {
    try {
      const blob = new Blob([fullConfig], { type: 'application/x-yaml' });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19);
      a.download = `kvalidator-config-${timestamp}.yaml`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      
      message.success('Full configuration exported successfully');
    } catch (error) {
      console.error('Failed to export config:', error);
      message.error('Failed to export configuration');
    }
  };

  const importFullConfig = () => {
    const input = document.createElement('input');
    input.type = 'file';
    input.accept = '.yaml,.yml';
    
    input.onchange = async (e: any) => {
      const file = e.target.files?.[0];
      if (!file) return;

      try {
        const content = await file.text();
        
        // Parse YAML (simple parser for our format)
        const namespaces: string[] = [];
        const fields: string[] = [];
        
        const lines = content.split('\n');
        let inNamespaces = false;
        let inIgnoreFields = false;
        
        for (const line of lines) {
          const trimmed = line.trim();
          
          if (trimmed.includes('namespaces:')) {
            inNamespaces = true;
            inIgnoreFields = false;
            continue;
          }
          
          if (trimmed.includes('ignoreFields:')) {
            inNamespaces = false;
            inIgnoreFields = true;
            continue;
          }
          
          if (trimmed.startsWith('- ')) {
            const value = trimmed.substring(2).replace(/^["']|["']$/g, '');
            if (inNamespaces && value && !value.startsWith('#')) {
              namespaces.push(value);
            } else if (inIgnoreFields && value && !value.startsWith('#')) {
              fields.push(value);
            }
          }
          
          // Stop when reaching metadata or other sections
          if (trimmed.startsWith('metadata:') || trimmed.startsWith('version:')) {
            inNamespaces = false;
            inIgnoreFields = false;
          }
        }

        if (namespaces.length === 0 && fields.length === 0) {
          message.warning('No valid configuration found in file');
          return;
        }

        // Call onImport callback
        if (onImport) {
          onImport({ namespaces, ignoreFields: fields });
          message.success(`Imported ${namespaces.length} namespaces and ${fields.length} ignore rules`);
        }
        
      } catch (error: any) {
        console.error('Failed to import config:', error);
        message.error(`Failed to import: ${error.message}`);
      }
    };
    
    input.click();
  };

  const renderPreview = () => (
    <div>
      <Alert
        message="Configuration Preview"
        description={
          <div>
            <p style={{ marginBottom: 8 }}>
              <strong>Namespaces:</strong> {selectedNamespaces.length} selected
            </p>
            <p style={{ marginBottom: 8 }}>
              <strong>Ignore Rules:</strong> {ignoreFields.length} configured
            </p>
            <p style={{ margin: 0, color: '#666', fontSize: 12 }}>
              This configuration can be exported as YAML and re-imported later for quick setup.
            </p>
          </div>
        }
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      {selectedNamespaces.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>Selected Namespaces:</div>
          <div style={{ 
            maxHeight: 200, 
            overflowY: 'auto', 
            padding: 12, 
            backgroundColor: '#f5f5f5', 
            borderRadius: 4,
            fontFamily: 'monospace',
            fontSize: 12,
          }}>
            {selectedNamespaces.map((ns, idx) => (
              <div key={idx}>{ns.cluster}/{ns.namespace}</div>
            ))}
          </div>
        </div>
      )}

      {ignoreFields.length > 0 && (
        <div>
          <div style={{ fontWeight: 500, marginBottom: 8 }}>Ignore Rules:</div>
          <div style={{ 
            maxHeight: 200, 
            overflowY: 'auto', 
            padding: 12, 
            backgroundColor: '#f5f5f5', 
            borderRadius: 4,
            fontFamily: 'monospace',
            fontSize: 12,
          }}>
            {ignoreFields.sort().map((field, idx) => (
              <div key={idx}>{field}</div>
            ))}
          </div>
        </div>
      )}

      {selectedNamespaces.length === 0 && ignoreFields.length === 0 && (
        <Alert
          message="No Configuration"
          description="Select namespaces and configure ignore rules to see the preview."
          type="warning"
          showIcon
        />
      )}
    </div>
  );

  const renderYaml = () => (
    <div>
      <Alert
        message="Full Configuration YAML"
        description="This is the complete validation configuration including namespaces and ignore rules. You can export this file and import it later."
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />
      <TextArea
        value={fullConfig}
        readOnly
        rows={25}
        style={{ 
          fontFamily: 'monospace', 
          fontSize: 12,
          backgroundColor: '#f5f5f5',
        }}
      />
    </div>
  );

  return (
    <Card
      title="Full Validation Configuration"
      extra={
        <Space>
          <Button 
            icon={<UploadIcon size={16} />} 
            onClick={importFullConfig}
            size="small"
          >
            Import
          </Button>
          <Button 
            icon={<Download size={16} />} 
            onClick={exportFullConfig}
            size="small"
            type="primary"
            disabled={selectedNamespaces.length === 0 && ignoreFields.length === 0}
          >
            Export
          </Button>
        </Space>
      }
      size="small"
    >
      {/* View Mode Selector */}
      <div style={{ marginBottom: 16 }}>
        <Radio.Group value={viewMode} onChange={(e) => setViewMode(e.target.value)}>
          <Space>
            <Radio.Button value="preview">
              <Eye size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
              Preview
            </Radio.Button>
            <Radio.Button value="yaml">
              <FileCode size={16} style={{ marginRight: 8, verticalAlign: 'middle' }} />
              YAML
            </Radio.Button>
          </Space>
        </Radio.Group>
      </div>

      {viewMode === 'preview' ? renderPreview() : renderYaml()}
    </Card>
  );
};
