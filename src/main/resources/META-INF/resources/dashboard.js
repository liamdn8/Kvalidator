// KValidator Dashboard Logic

const API_BASE = '/api/validate';
const API_DISCOVERY_BASE = '/api/kubernetes';

const DEFAULT_MANUAL_LABEL = '<i data-lucide="play" class="icon-sm me-2"></i>Run Validation';
const DEFAULT_QUICK_LABEL = '<i data-lucide="zap" class="icon-sm me-2"></i>Run Quick Validation';
const SEARCH_BUTTON_LABEL = '<i data-lucide="scan-search" class="icon-sm me-1"></i>Search';
const BASELINE_UPLOAD_LIMIT = 5 * 1024 * 1024; // 5 MB

let pollInterval = null;
let timeInterval = null;
let startTime = null;
let currentMode = 'manual';
let clusters = [];
let clustersLoading = true;
const namespaceCache = {};
let lastLoggedStep = '';
let quickSearchResults = [];
const quickSelectedTargets = new Set();

const manualModeBtn = document.getElementById('manualModeBtn');
const quickModeBtn = document.getElementById('quickModeBtn');
const manualSection = document.getElementById('manualSection');
const quickSection = document.getElementById('quickSection');
const baselineClusterSelect = document.getElementById('baselineClusterSelect');
const baselineNamespaceSelect = document.getElementById('baselineNamespaceSelect');
const addTargetBtn = document.getElementById('addTargetBtn');
const targetsTableBody = document.getElementById('targetsTableBody');
const baselineInput = document.getElementById('baselineInput');
const baselineUploadInput = document.getElementById('baselineUploadInput');
const baselineUploadStatus = document.getElementById('baselineUploadStatus');
const baselineUploadMeta = document.getElementById('baselineUploadMeta');
const baselineUploadClear = document.getElementById('baselineUploadClear');
const baselineUploadWrapper = document.getElementById('baselineUploadWrapper');
const quickSearchInput = document.getElementById('quickSearchInput');
const quickSearchBtn = document.getElementById('quickSearchBtn');
const quickExactMatch = document.getElementById('quickExactMatch');
const quickResultsPanel = document.getElementById('quickResultsPanel');
const quickResultsCount = document.getElementById('quickResultsCount');
const quickSearchMeta = document.getElementById('quickSearchMeta');
const quickBaselineSelect = document.getElementById('quickBaselineSelect');
const quickTargetsList = document.getElementById('quickTargetsList');
const quickTargetsMeta = document.getElementById('quickTargetsMeta');
const quickStatusMessage = document.getElementById('quickStatusMessage');
const formError = document.getElementById('formError');
const validationForm = document.getElementById('validationForm');

const statusSummary = document.getElementById('statusSummary');
const summaryStatusBadge = document.getElementById('summaryStatusBadge');
const summaryJobId = document.getElementById('summaryJobId');
const summaryProgress = document.getElementById('summaryProgress');
const summaryMessage = document.getElementById('summaryMessage');

const statusCard = document.getElementById('statusCard');
const statusBadge = document.getElementById('statusBadge');
const displayJobId = document.getElementById('displayJobId');
const progressBar = document.getElementById('progressBar');
const currentAction = document.getElementById('currentAction');
const consoleOutput = document.getElementById('consoleOutput');
const resultActions = document.getElementById('resultActions');
const btnDownloadReport = document.getElementById('btnDownloadReport');
const btnJsonResult = document.getElementById('btnJsonResult');
const resultSummary = document.getElementById('resultSummary');
const errorAlert = document.getElementById('errorAlert');
const errorMessage = document.getElementById('errorMessage');
const introCard = document.getElementById('introCard');
const timeElapsed = document.getElementById('timeElapsed');
const btnSubmit = document.getElementById('btnSubmit');

let baselineUploadInfo = null;
init();

/**
 * Parse and flatten YAML baseline file client-side
 */
async function parseBaselineYaml(file) {
	const text = await file.text();
	const parsed = jsyaml.load(text);
	
	if (!parsed) {
		throw new Error('Invalid YAML file');
	}
	
	const flattened = {};
	const items = Array.isArray(parsed.items) ? parsed.items : [parsed];
	
	items.forEach((item) => {
		if (!item || !item.kind || !item.metadata || !item.metadata.name) {
			return;
		}
		
		const objectName = item.metadata.name;
		const flatObject = flattenKubernetesObject(item);
		flattened[objectName] = flatObject;
	});
	
	return {
		objects: flattened,
		cluster: parsed.metadata?.labels?.cluster || 'baseline',
		namespace: parsed.metadata?.namespace || items[0]?.metadata?.namespace || 'baseline'
	};
}

/**
 * Flatten a Kubernetes object into key-value pairs (matches server-side FlatObjectModel)
 */
function flattenKubernetesObject(obj) {
	const result = {};
	
	// Basic fields
	if (obj.kind) result['kind'] = obj.kind;
	if (obj.apiVersion) result['apiVersion'] = obj.apiVersion;
	
	// Flatten metadata
	if (obj.metadata) {
		flattenObject(obj.metadata, 'metadata', result);
	}
	
	// Flatten spec
	if (obj.spec) {
		flattenObject(obj.spec, 'spec', result);
	}
	
	return result;
}

/**
 * Recursively flatten an object into dot-notation paths
 */
function flattenObject(obj, prefix, result) {
	if (obj === null || obj === undefined) {
		return;
	}
	
	if (typeof obj !== 'object' || obj instanceof Date) {
		result[prefix] = String(obj);
		return;
	}
	
	if (Array.isArray(obj)) {
		obj.forEach((item, index) => {
			flattenObject(item, `${prefix}[${index}]`, result);
		});
		return;
	}
	
	for (const [key, value] of Object.entries(obj)) {
		const path = prefix ? `${prefix}.${key}` : key;
		flattenObject(value, path, result);
	}
}

function init() {
	if (!validationForm) {
		return;
	}

	bindEventListeners();
	ensureTargetRowExists();
	setMode('manual');
	updateSubmitLabel();
	refreshIcons();
	void loadClusters();
}

function bindEventListeners() {
	manualModeBtn?.addEventListener('click', () => setMode('manual'));
	quickModeBtn?.addEventListener('click', () => setMode('quick'));
	addTargetBtn?.addEventListener('click', () => addTargetRow());
	baselineClusterSelect?.addEventListener('change', () => void onBaselineClusterChange());
	baselineNamespaceSelect?.addEventListener('change', () => clearFormError());
	baselineInput?.addEventListener('input', () => clearFormError());
baselineUploadInput?.addEventListener('change', (event) => void onBaselineUploadSelected(event));
baselineUploadClear?.addEventListener('click', () => {
	clearBaselineUpload();
	baselineUploadInput?.focus();
});

	quickSearchBtn?.addEventListener('click', () => void performQuickSearch());
	quickSearchInput?.addEventListener('keydown', (event) => {
		if (event.key === 'Enter') {
			event.preventDefault();
			void performQuickSearch();
		}
	});
	quickBaselineSelect?.addEventListener('change', () => {
		const baselineKey = quickBaselineSelect.value;
		if (!baselineKey) {
			renderQuickTargets();
			updateQuickStatus();
			return;
		}
		quickSelectedTargets.delete(baselineKey);
		if (quickSearchResults.length > 0 && quickSelectedTargets.size === 0) {
			quickSearchResults.forEach(({ cluster, namespace }) => {
				const key = makeNamespaceKey(cluster, namespace);
				if (key !== baselineKey) {
					quickSelectedTargets.add(key);
				}
			});
		}
		renderQuickTargets();
		updateQuickStatus();
	});

	validationForm.addEventListener('submit', (event) => void onSubmit(event));
}

async function onBaselineUploadSelected(event) {
	const file = event.target?.files?.[0];
	if (!file) {
		clearBaselineUpload();
		return;
	}
	
	if (file.size > BASELINE_UPLOAD_LIMIT) {
		showFormError(`Baseline file too large (${(file.size / 1024 / 1024).toFixed(2)} MB). Maximum size is 5 MB.`);
		clearBaselineUpload();
		return;
	}
	
	if (!file.name.match(/\.(yaml|yml)$/i)) {
		showFormError('Please select a valid YAML file (.yaml or .yml)');
		clearBaselineUpload();
		return;
	}
	
	try {
		setBaselineUploadStatus('Parsing YAML...', 'info');
		const parsed = await parseBaselineYaml(file);
		
		const objectCount = Object.keys(parsed.objects).length;
		if (objectCount === 0) {
			showFormError('No valid Kubernetes objects found in the YAML file');
			clearBaselineUpload();
			return;
		}
		
		baselineUploadInfo = {
			filename: file.name,
			size: file.size,
			cluster: parsed.cluster,
			namespace: parsed.namespace,
			objects: parsed.objects,
			objectCount
		};
		
		setBaselineUploadStatus(`Loaded ${objectCount} object${objectCount === 1 ? '' : 's'} from ${file.name}`, 'success');
		baselineUploadWrapper?.classList.add('has-file');
		baselineUploadClear?.classList.remove('d-none');
		
		if (baselineUploadMeta) {
			baselineUploadMeta.textContent = `${parsed.namespace} @ ${parsed.cluster} · ${(file.size / 1024).toFixed(1)} KB`;
			baselineUploadMeta.hidden = false;
		}
		
		clearFormError();
		refreshIcons();
	} catch (error) {
		console.error('Failed to parse baseline YAML', error);
		showFormError('Failed to parse YAML file: ' + (error.message || 'Invalid format'));
		clearBaselineUpload();
	}
}

function clearBaselineUpload() {
	baselineUploadInfo = null;
	if (baselineUploadInput) {
		baselineUploadInput.value = '';
	}
	setBaselineUploadStatus('No file selected.', 'muted');
	baselineUploadWrapper?.classList.remove('has-file');
	baselineUploadClear?.classList.add('d-none');
	if (baselineUploadMeta) {
		baselineUploadMeta.textContent = '';
		baselineUploadMeta.hidden = true;
	}
	refreshIcons();
}

function setBaselineUploadStatus(message, variant = 'muted') {
	if (!baselineUploadStatus) {
		return;
	}
	baselineUploadStatus.textContent = message;
	baselineUploadStatus.classList.remove('text-muted', 'text-success', 'text-danger', 'text-info');
	if (variant === 'success') {
		baselineUploadStatus.classList.add('text-success', 'fw-semibold');
	} else if (variant === 'danger') {
		baselineUploadStatus.classList.add('text-danger');
	} else if (variant === 'info') {
		baselineUploadStatus.classList.add('text-info');
	} else {
		baselineUploadStatus.classList.add('text-muted');
	}
}

async function onSubmit(event) {
	event.preventDefault();
	clearFormError();

	const payload = currentMode === 'manual' ? buildManualPayload() : buildQuickPayload();
	if (!payload) {
		return;
	}

	setLoading(true);
	resetUI();

	try {
		const response = await fetch(API_BASE, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(payload)
		});

		if (!response.ok) {
			throw new Error(`Server returned ${response.status}: ${response.statusText}`);
		}

		const data = await response.json();
		startPolling(data.jobId);
	} catch (error) {
		console.error('Job submission failed', error);
		showError(error.message || 'Failed to submit validation job.');
		setLoading(false);
	}
}

function resetUI() {
	if (pollInterval) {
		clearInterval(pollInterval);
		pollInterval = null;
	}
	if (timeInterval) {
		clearInterval(timeInterval);
		timeInterval = null;
	}

	lastLoggedStep = '';
	startTime = new Date();
	updateTimer();
	timeInterval = setInterval(updateTimer, 1000);

	introCard?.classList.add('d-none');
	statusCard?.classList.remove('d-none');
	statusSummary?.classList.remove('d-none');
	resultActions?.classList.add('d-none');
	errorAlert?.classList.add('d-none');

	applyStatusBadge(statusBadge, 'PENDING');
	applyStatusBadge(summaryStatusBadge, 'PENDING');

	if (summaryJobId) summaryJobId.textContent = '-';
	if (summaryProgress) summaryProgress.textContent = '0%';
	if (summaryMessage) summaryMessage.textContent = 'Submitting validation job...';
	if (displayJobId) displayJobId.textContent = '...';
	if (currentAction) currentAction.textContent = 'Preparing job...';

	if (progressBar) {
		progressBar.style.width = '0%';
		progressBar.textContent = '0%';
		progressBar.classList.remove('bg-danger', 'bg-success');
		progressBar.classList.add('progress-bar-animated');
	}

	if (consoleOutput) {
		consoleOutput.innerHTML = '<div class="text-muted">Job submitted...</div>';
	}
}

function updateTimer() {
	if (!timeElapsed || !startTime) {
		return;
	}
	const elapsedSeconds = Math.max(0, Math.floor((Date.now() - startTime.getTime()) / 1000));
	const minutes = Math.floor(elapsedSeconds / 60);
	const seconds = elapsedSeconds % 60;
	timeElapsed.textContent = minutes > 0 ? `${minutes}m ${seconds}s` : `${seconds}s`;
}

function setLoading(isLoading) {
	if (!btnSubmit) {
		return;
	}
	btnSubmit.dataset.loading = isLoading ? 'true' : 'false';
	btnSubmit.disabled = isLoading;
	if (isLoading) {
		btnSubmit.innerHTML = '<span class="spinner-border spinner-border-sm me-2" role="status" aria-hidden="true"></span>Submitting...';
	} else {
		updateSubmitLabel();
		if (currentMode === 'quick') {
			updateQuickStatus();
		} else {
			setSubmitAvailability(true);
		}
	}
}

function updateSubmitLabel() {
	if (!btnSubmit || btnSubmit.dataset.loading === 'true') {
		return;
	}
	btnSubmit.innerHTML = currentMode === 'manual' ? DEFAULT_MANUAL_LABEL : DEFAULT_QUICK_LABEL;
	refreshIcons();
}

function setMode(mode) {
	currentMode = mode;
	clearFormError();
	clearBaselineUpload();

	const isManual = mode === 'manual';
	manualSection?.classList.toggle('d-none', !isManual);
	quickSection?.classList.toggle('d-none', isManual);

	manualModeBtn?.classList.toggle('btn-primary', isManual);
	manualModeBtn?.classList.toggle('btn-light', !isManual);
	quickModeBtn?.classList.toggle('btn-primary', !isManual);
	quickModeBtn?.classList.toggle('btn-light', isManual);

	if (isManual) {
		ensureTargetRowExists();
		setSubmitAvailability(true);
	} else {
		updateQuickStatus();
	}

	updateSubmitLabel();
	refreshIcons();
}

function ensureTargetRowExists() {
	const existingRows = targetsTableBody?.querySelectorAll('tr.target-row') || [];
	if (existingRows.length === 0) {
		addTargetRow();
	}
}

function addTargetRow(initial = {}) {
	if (!targetsTableBody) {
		return;
	}

	const placeholder = targetsTableBody.querySelector('.table-empty');
	if (placeholder) {
		placeholder.remove();
	}

	const row = document.createElement('tr');
	row.className = 'target-row';
	row.innerHTML = `
		<td>
			<select class="form-select target-cluster"></select>
		</td>
		<td>
			<select class="form-select target-namespace" disabled>
				<option value="">Select cluster first...</option>
			</select>
		</td>
		<td class="text-center">
			<button type="button" class="btn btn-outline-danger btn-sm remove-target" aria-label="Remove target">
				<i data-lucide="trash-2" class="icon-sm"></i>
			</button>
		</td>
	`;

	targetsTableBody.appendChild(row);

	const clusterSelect = row.querySelector('.target-cluster');
	const namespaceSelect = row.querySelector('.target-namespace');
	populateClusterOptions(clusterSelect, initial.cluster || '');

	clusterSelect?.addEventListener('change', () => {
		clearFormError();
		const cluster = clusterSelect.value;
		if (!cluster) {
			resetNamespaceSelect(namespaceSelect);
			return;
		}
		void loadNamespacesForSelect(cluster, namespaceSelect);
	});

	namespaceSelect?.addEventListener('change', () => clearFormError());

	const removeBtn = row.querySelector('.remove-target');
	removeBtn?.addEventListener('click', () => {
		row.remove();
		const rows = targetsTableBody.querySelectorAll('tr.target-row');
		if (rows.length === 0) {
			ensureTargetRowExists();
		}
		updateRemoveButtons();
	});

	if (initial.cluster) {
		void loadNamespacesForSelect(initial.cluster, namespaceSelect, initial.namespace || '');
	}

	updateRemoveButtons();
	refreshIcons();
}

function updateRemoveButtons() {
	if (!targetsTableBody) {
		return;
	}
	const rows = Array.from(targetsTableBody.querySelectorAll('tr.target-row'));
	const disable = rows.length <= 1;
	rows.forEach((row) => {
		const removeBtn = row.querySelector('.remove-target');
		if (removeBtn) {
			removeBtn.disabled = disable;
		}
	});
}

async function loadClusters() {
	clustersLoading = true;
	populateClusterOptions(baselineClusterSelect);
	const rows = targetsTableBody?.querySelectorAll('tr.target-row') || [];
	rows.forEach((row) => {
		const clusterSelect = row.querySelector('.target-cluster');
		populateClusterOptions(clusterSelect);
	});

	try {
		const response = await fetch(`${API_DISCOVERY_BASE}/clusters`);
		if (!response.ok) {
			throw new Error(`Failed to load clusters (${response.status})`);
		}
		const data = await response.json();
		const discovered = Array.isArray(data) ? data.filter(Boolean) : [];
		if (!discovered.includes('current')) {
			discovered.push('current');
		}
		clusters = discovered.length > 0 ? discovered : ['current'];
	} catch (error) {
		console.error('Cluster discovery failed', error);
		clusters = ['current'];
	} finally {
		clusters = [...new Set(clusters)];
		clustersLoading = false;
	}

	populateClusterOptions(baselineClusterSelect, baselineClusterSelect?.value || '');
	await onBaselineClusterChange(true);

	const targetRows = targetsTableBody?.querySelectorAll('tr.target-row') || [];
	targetRows.forEach((row) => {
		const clusterSelect = row.querySelector('.target-cluster');
		const namespaceSelect = row.querySelector('.target-namespace');
		const selectedCluster = clusterSelect?.value || '';
		populateClusterOptions(clusterSelect, selectedCluster);
		if (selectedCluster) {
			void loadNamespacesForSelect(selectedCluster, namespaceSelect, namespaceSelect?.value || '');
		}
	});

	refreshIcons();
}

function populateClusterOptions(select, preferredValue = '') {
	if (!select) {
		return;
	}

	const previous = preferredValue || select.value || '';
	select.innerHTML = '';

	const placeholder = document.createElement('option');
	placeholder.value = '';
	if (clustersLoading) {
		placeholder.textContent = 'Loading clusters...';
		select.disabled = true;
	} else if (clusters.length === 0) {
		placeholder.textContent = 'No clusters available';
		select.disabled = true;
	} else {
		placeholder.textContent = 'Select cluster...';
		select.disabled = false;
	}
	select.appendChild(placeholder);

	clusters.forEach((cluster) => {
		const option = document.createElement('option');
		option.value = cluster;
		option.textContent = cluster;
		select.appendChild(option);
	});

	if (previous && clusters.includes(previous)) {
		select.value = previous;
	}
}

async function onBaselineClusterChange(retainSelection = false) {
	if (!baselineNamespaceSelect) {
		return;
	}
	clearFormError();

	const cluster = baselineClusterSelect?.value;
	if (!cluster) {
		resetNamespaceSelect(baselineNamespaceSelect);
		return;
	}

	const previous = retainSelection ? baselineNamespaceSelect.value : '';
	setNamespaceLoading(baselineNamespaceSelect);

	try {
		const namespaces = await fetchNamespaces(cluster);
		populateNamespaceOptions(baselineNamespaceSelect, namespaces, previous);
	} catch (error) {
		console.error(`Failed to load namespaces for ${cluster}`, error);
		baselineNamespaceSelect.innerHTML = '<option value="">Unable to load namespaces</option>';
		baselineNamespaceSelect.disabled = true;
	}
}

async function loadNamespacesForSelect(cluster, select, preferredValue = '') {
	if (!select || !cluster) {
		resetNamespaceSelect(select);
		return;
	}

	setNamespaceLoading(select);
	try {
		const namespaces = await fetchNamespaces(cluster);
		populateNamespaceOptions(select, namespaces, preferredValue);
	} catch (error) {
		console.error(`Failed to load namespaces for ${cluster}`, error);
		select.innerHTML = '<option value="">Unable to load namespaces</option>';
		select.disabled = true;
	}
}

async function fetchNamespaces(cluster) {
	if (namespaceCache[cluster]) {
		return namespaceCache[cluster];
	}
	const response = await fetch(`${API_DISCOVERY_BASE}/namespaces?cluster=${encodeURIComponent(cluster)}`);
	if (!response.ok) {
		throw new Error(`Namespace request failed (${response.status})`);
	}
	const data = await response.json();
	namespaceCache[cluster] = Array.isArray(data) ? data : [];
	return namespaceCache[cluster];
}

function setNamespaceLoading(select) {
	if (!select) {
		return;
	}
	select.disabled = true;
	select.innerHTML = '<option value="">Loading namespaces...</option>';
}

function populateNamespaceOptions(select, namespaces, preferredValue = '') {
	if (!select) {
		return;
	}

	select.innerHTML = '';
	const placeholder = document.createElement('option');
	placeholder.value = '';
	if (!namespaces || namespaces.length === 0) {
		placeholder.textContent = 'No namespaces found';
		select.disabled = true;
	} else {
		placeholder.textContent = 'Select namespace...';
		select.disabled = false;
	}
	select.appendChild(placeholder);

	(namespaces || []).forEach((ns) => {
		const option = document.createElement('option');
		option.value = ns;
		option.textContent = ns;
		select.appendChild(option);
	});

	if (preferredValue && namespaces && namespaces.includes(preferredValue)) {
		select.value = preferredValue;
	}
}

function resetNamespaceSelect(select) {
	if (!select) {
		return;
	}
	select.disabled = true;
	select.innerHTML = '<option value="">Select cluster first...</option>';
}

async function performQuickSearch() {
	clearFormError();

	if (!quickResultsPanel) {
		return;
	}

	if (clustersLoading) {
		await loadClusters();
	}

	const clusterList = clusters.filter(Boolean);
	if (clusterList.length === 0) {
		resetQuickResultsPanel();
		setQuickStatus('No clusters available for search.', 'danger');
		setSubmitAvailability(false);
		return;
	}

	const keyword = (quickSearchInput?.value || '').trim();
	const isExact = Boolean(quickExactMatch?.checked);
	if (!keyword) {
		resetQuickResultsPanel();
		setQuickStatus('Enter a namespace keyword to search.', 'muted');
		setSubmitAvailability(false);
		return;
	}

	setQuickSearchLoading(true);
	setQuickStatus('Searching namespaces...');

	try {
		const results = [];
		const keywordLower = keyword.toLowerCase();
		let failedClusters = 0;
		for (const cluster of clusterList) {
			try {
				const namespaces = await fetchNamespaces(cluster);
				namespaces
					.filter((ns) => {
						const nameLower = ns.toLowerCase();
						return isExact ? nameLower === keywordLower : nameLower.includes(keywordLower);
					})
					.forEach((ns) => results.push({ cluster, namespace: ns }));
			} catch (error) {
				console.error(`Namespace fetch failed for ${cluster}`, error);
				failedClusters += 1;
			}
		}

		if (results.length === 0) {
			resetQuickResultsPanel();
			if (failedClusters === clusterList.length) {
				setQuickStatus('Unable to retrieve namespaces from available clusters.', 'danger');
			} else {
				setQuickStatus(`No namespaces matched "${keyword}".`, 'danger');
			}
			setSubmitAvailability(false);
			return;
		}
function resetQuickResultsPanel() {
	quickSearchResults = [];
	quickSelectedTargets.clear();
	if (quickResultsPanel) {
		quickResultsPanel.classList.add('d-none');
	}
	if (quickResultsCount) {
		quickResultsCount.textContent = 'Found 0 namespace(s)';
	}
	if (quickSearchMeta) {
		quickSearchMeta.textContent = '';
	}
	if (quickBaselineSelect) {
		quickBaselineSelect.innerHTML = '<option value="">Select a namespace...</option>';
		quickBaselineSelect.disabled = true;
	}
	if (quickTargetsList) {
		quickTargetsList.innerHTML = '';
	}
	if (quickTargetsMeta) {
		quickTargetsMeta.textContent = '0 targets selected';
	}
}

		renderQuickResults(results, { keyword, isExact });
	} finally {
		setQuickSearchLoading(false);
	}
}

function setQuickSearchLoading(isLoading) {
	if (!quickSearchBtn) {
		return;
	}
	quickSearchBtn.disabled = isLoading;
	if (quickSearchInput) {
		quickSearchInput.disabled = isLoading;
	}
	if (quickExactMatch) {
		quickExactMatch.disabled = isLoading;
	}
	if (isLoading) {
		quickSearchBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-1" role="status" aria-hidden="true"></span>Searching';
	} else {
		quickSearchBtn.innerHTML = SEARCH_BUTTON_LABEL;
		refreshIcons();
	}
}

function renderQuickResults(items, meta = {}) {
	quickSearchResults = [...items].sort((a, b) => {
		if (a.cluster === b.cluster) {
			return a.namespace.localeCompare(b.namespace);
		}
		return a.cluster.localeCompare(b.cluster);
	});

	const clusterCount = new Set(quickSearchResults.map((item) => item.cluster)).size;
	const total = quickSearchResults.length;
	quickResultsPanel?.classList.remove('d-none');
	if (quickResultsCount) {
		quickResultsCount.textContent = `Found ${total} namespace${total === 1 ? '' : 's'}`;
	}
	if (quickSearchMeta) {
		const keywordPart = meta.keyword ? `${meta.isExact ? 'Exact match' : 'Contains'} "${meta.keyword}"` : 'Listing namespaces';
		quickSearchMeta.textContent = `${keywordPart} · ${clusterCount} cluster${clusterCount === 1 ? '' : 's'}`;
	}

	const previousBaseline = quickBaselineSelect?.value;
	const defaultBaselineKey = quickSearchResults.length > 0 ? makeNamespaceKey(quickSearchResults[0].cluster, quickSearchResults[0].namespace) : '';
	const baselineKey = previousBaseline && quickSearchResults.some((item) => makeNamespaceKey(item.cluster, item.namespace) === previousBaseline)
		? previousBaseline
		: defaultBaselineKey;

	populateBaselineOptions(quickSearchResults, baselineKey);

	quickSelectedTargets.clear();
	quickSearchResults.forEach(({ cluster, namespace }) => {
		const key = makeNamespaceKey(cluster, namespace);
		if (key !== baselineKey) {
			quickSelectedTargets.add(key);
		}
	});

	renderQuickTargets();
	updateQuickStatus();
}

function populateBaselineOptions(items, selectedKey) {
	if (!quickBaselineSelect) {
		return;
	}

	quickBaselineSelect.innerHTML = '';
	if (!items || items.length === 0) {
		const option = document.createElement('option');
		option.value = '';
		option.textContent = 'No namespaces available';
		quickBaselineSelect.appendChild(option);
		quickBaselineSelect.disabled = true;
		return;
	}

	quickBaselineSelect.disabled = false;
	items.forEach(({ cluster, namespace }) => {
		const option = document.createElement('option');
		const key = makeNamespaceKey(cluster, namespace);
		option.value = key;
		option.textContent = `${cluster} / ${namespace}`;
		quickBaselineSelect.appendChild(option);
	});

	if (selectedKey) {
		quickBaselineSelect.value = selectedKey;
	}
}

function renderQuickTargets() {
	if (!quickTargetsList) {
		return;
	}

	quickTargetsList.innerHTML = '';
	const baselineKey = quickBaselineSelect?.value || '';
	let rendered = 0;
	const fragment = document.createDocumentFragment();

	quickSearchResults.forEach(({ cluster, namespace }) => {
		const key = makeNamespaceKey(cluster, namespace);
		if (key === baselineKey) {
			quickSelectedTargets.delete(key);
			return;
		}

		const isSelected = quickSelectedTargets.has(key);
		const item = document.createElement('div');
		item.className = 'list-group-item d-flex justify-content-between align-items-center';
		if (isSelected) {
			item.classList.add('bg-success-subtle');
		}
		item.innerHTML = `
			<div>
				<span class="fw-semibold">${cluster}</span>
				<span class="text-muted"> / </span>
				<span>${namespace}</span>
			</div>
			<div class="form-check">
				<input class="form-check-input quick-target-checkbox" type="checkbox" ${isSelected ? 'checked' : ''} data-key="${key}" data-cluster="${cluster}" data-namespace="${namespace}">
			</div>
		`;

		const checkbox = item.querySelector('.quick-target-checkbox');
		checkbox?.addEventListener('change', (event) => {
			if (event.target.checked) {
				quickSelectedTargets.add(key);
			} else {
				quickSelectedTargets.delete(key);
			}
			item.classList.toggle('bg-success-subtle', event.target.checked);
			updateQuickTargetsMeta();
			updateQuickStatus();
		});

		fragment.appendChild(item);
		rendered += 1;
	});

	if (rendered === 0) {
		const empty = document.createElement('div');
		empty.className = 'list-group-item text-center small text-muted';
		empty.textContent = 'Select another namespace to compare against the baseline.';
		fragment.appendChild(empty);
	}

	quickTargetsList.appendChild(fragment);
	updateQuickTargetsMeta();
}

function updateQuickTargetsMeta() {
	if (!quickTargetsMeta) {
		return;
	}
	const count = quickSelectedTargets.size;
	quickTargetsMeta.textContent = `${count} target${count === 1 ? '' : 's'} selected`;
}

function setQuickStatus(message, tone = 'muted') {
	if (!quickStatusMessage) {
		return;
	}
	quickStatusMessage.textContent = message;
	quickStatusMessage.classList.remove('text-muted', 'text-success', 'text-danger');
	let cssClass = 'text-muted';
	if (tone === 'success') {
		cssClass = 'text-success';
	} else if (tone === 'danger') {
		cssClass = 'text-danger';
	}
	quickStatusMessage.classList.add(cssClass);
}

function updateQuickStatus() {
	if (currentMode !== 'quick') {
		return;
	}
	if (!quickResultsPanel || quickResultsPanel.classList.contains('d-none')) {
		setQuickStatus('Search to discover namespaces.');
		setSubmitAvailability(false);
		return;
	}

	const baselineKey = quickBaselineSelect?.value || '';
	if (!baselineKey) {
		setQuickStatus('Choose a baseline namespace from the list.', 'danger');
		setSubmitAvailability(false);
		return;
	}

	const targetCount = quickSelectedTargets.size;
	if (targetCount === 0) {
		setQuickStatus('Select at least one target namespace.', 'danger');
		setSubmitAvailability(false);
		return;
	}

	setQuickStatus(`Ready to validate: 1 baseline + ${targetCount} target${targetCount === 1 ? '' : 's'}.`, 'success');
	setSubmitAvailability(true);
}

function setSubmitAvailability(canSubmit) {
	if (!btnSubmit || btnSubmit.dataset.loading === 'true') {
		return;
	}
	if (currentMode === 'quick') {
		btnSubmit.disabled = !canSubmit;
	} else {
		btnSubmit.disabled = false;
	}
}

function buildManualPayload() {
	const baselinePath = (baselineInput?.value || '').trim();
	const baselineCluster = baselineClusterSelect?.value || '';
	const baselineNamespace = baselineNamespaceSelect?.value || '';

	const rows = Array.from(targetsTableBody?.querySelectorAll('tr.target-row') || []);
	const targets = rows
		.map((row) => {
			const cluster = row.querySelector('.target-cluster')?.value;
			const namespace = row.querySelector('.target-namespace')?.value;
			return cluster && namespace ? `${cluster}/${namespace}` : '';
		})
		.filter((value) => value.length > 0);

	// Client-side baseline upload takes precedence
	if (baselineUploadInfo) {
		if (targets.length === 0) {
			showFormError('Select at least one target namespace when using a baseline.');
			return null;
		}
		
		// Clear manual baseline path if upload is active
		if (baselinePath) {
			baselineInput.value = '';
		}
		
		return {
			namespaces: uniqueList(targets),
			cluster: baselineCluster || 'current',
			baselineObjects: baselineUploadInfo.objects,
			baselineCluster: baselineUploadInfo.cluster,
			baselineNamespace: baselineUploadInfo.namespace
		};
	}

	if (baselinePath) {
		if (targets.length === 0) {
			showFormError('Select at least one target namespace when using a baseline file.');
			return null;
		}
		return {
			namespaces: uniqueList(targets),
			cluster: baselineCluster || 'current',
			baselinePath
		};
	}

	if (!baselineCluster || !baselineNamespace) {
		showFormError('Select a baseline cluster and namespace or provide a baseline file.');
		return null;
	}

	if (targets.length === 0) {
		showFormError('Add at least one target namespace to compare.');
		return null;
	}

	const baselineKey = `${baselineCluster}/${baselineNamespace}`;
	const filteredTargets = targets.filter((target) => target !== baselineKey);
	if (filteredTargets.length === 0) {
		showFormError('Choose a target namespace different from the baseline.');
		return null;
	}

	const namespaces = uniqueList([baselineKey, ...filteredTargets]);
	return {
		namespaces,
		cluster: baselineCluster || 'current'
	};
}

function buildQuickPayload() {
	const baselinePath = (baselineInput?.value || '').trim();
	
	if (baselinePath || baselineUploadInfo) {
		showFormError('Quick validation uses selected namespaces only. Clear the baseline or switch to Manual mode.');
		return null;
	}

	if (!quickResultsPanel || quickResultsPanel.classList.contains('d-none') || quickSearchResults.length === 0) {
		showFormError('Search for namespaces and select a baseline before running quick validation.');
		return null;
	}

	const baselineKey = quickBaselineSelect?.value || '';
	if (!baselineKey) {
		showFormError('Choose a baseline namespace.');
		return null;
	}

	const [baselineCluster, baselineNamespace] = baselineKey.split('/');
	if (!baselineCluster || !baselineNamespace) {
		showFormError('Baseline selection is invalid.');
		return null;
	}

	const selectedTargets = Array.from(quickSelectedTargets).filter((key) => key !== baselineKey);
	if (selectedTargets.length === 0) {
		showFormError('Select at least one target namespace.');
		return null;
	}

	return {
		namespaces: uniqueList([baselineKey, ...selectedTargets]),
		cluster: baselineCluster || 'current'
	};
}

function makeNamespaceKey(cluster, namespace) {
	return `${cluster}/${namespace}`;
}

function uniqueList(items) {
	const seen = new Set();
	const result = [];
	items.forEach((item) => {
		if (!seen.has(item)) {
			seen.add(item);
			result.push(item);
		}
	});
	return result;
}

function startPolling(jobId) {
	if (!jobId) {
		showError('Server response did not include a job identifier.');
		setLoading(false);
		return;
	}

	if (displayJobId) displayJobId.textContent = jobId;
	if (summaryJobId) summaryJobId.textContent = jobId;
	setLoading(false);

	void pollStatus(jobId);
	pollInterval = setInterval(() => void pollStatus(jobId), 2000);
}

async function pollStatus(jobId) {
	try {
		const response = await fetch(`${API_BASE}/${jobId}`);
		if (!response.ok) {
			throw new Error(`Failed to fetch job status (${response.status})`);
		}
		const job = await response.json();
		updateStatusUI(job);

		if (job.status === 'COMPLETED' || job.status === 'FAILED') {
			if (pollInterval) {
				clearInterval(pollInterval);
				pollInterval = null;
			}
			if (timeInterval) {
				clearInterval(timeInterval);
				timeInterval = null;
			}
			if (job.status === 'COMPLETED') {
				showSuccess(job);
			} else {
				showFailure(job);
			}
		}
	} catch (error) {
		console.error('Polling error', error);
	}
}

function updateStatusUI(job) {
	if (!job) {
		return;
	}

	applyStatusBadge(statusBadge, job.status);
	applyStatusBadge(summaryStatusBadge, job.status);

	const percent = normalizePercentage(job);
	if (progressBar) {
		progressBar.style.width = `${percent}%`;
		progressBar.textContent = `${percent}%`;
		if (job.status === 'COMPLETED' || job.status === 'FAILED') {
			progressBar.classList.remove('progress-bar-animated');
		}
	}
	if (summaryProgress) {
		summaryProgress.textContent = `${percent}%`;
	}

	const currentStep = job.progress?.currentStep || job.message || statusLabel(job.status);
	if (currentAction) {
		currentAction.textContent = currentStep;
	}
	if (summaryMessage) {
		summaryMessage.textContent = currentStep;
	}

	if (job.progress?.currentStep && job.progress.currentStep !== lastLoggedStep) {
		appendConsoleLine(job.progress.currentStep);
		lastLoggedStep = job.progress.currentStep;
	}
}

function showSuccess(job) {
	applyStatusBadge(statusBadge, 'COMPLETED');
	applyStatusBadge(summaryStatusBadge, 'COMPLETED');
	if (progressBar) {
		progressBar.style.width = '100%';
		progressBar.textContent = '100%';
		progressBar.classList.add('bg-success');
	}
	if (summaryProgress) summaryProgress.textContent = '100%';
	if (summaryMessage) summaryMessage.textContent = job.message || 'Validation completed successfully.';

	if (resultActions) {
		resultActions.classList.remove('d-none');
	}
	if (btnDownloadReport && job.downloadUrl) {
		btnDownloadReport.href = job.downloadUrl;
	}
	if (btnJsonResult && job.jsonUrl) {
		btnJsonResult.href = job.jsonUrl;
	}
	if (resultSummary) {
		const diffs = job.differencesFound !== undefined ? job.differencesFound : 'N/A';
		resultSummary.textContent = job.message || `Differences found: ${diffs}`;
	}

	appendConsoleLine(job.message || 'Validation completed successfully.', 'success');
}

function showFailure(job) {
	applyStatusBadge(statusBadge, 'FAILED');
	applyStatusBadge(summaryStatusBadge, 'FAILED');
	if (progressBar) {
		progressBar.classList.add('bg-danger');
	}
	if (summaryMessage) summaryMessage.textContent = job.message || 'Validation failed.';
	appendConsoleLine(job.message || 'Validation failed.', 'danger');
	showError(job.message || 'Validation failed.');
}

function showError(message) {
	if (errorAlert) {
		errorAlert.classList.remove('d-none');
	}
	if (errorMessage) {
		errorMessage.textContent = message;
	}
	if (summaryMessage) {
		summaryMessage.textContent = message;
	}
	if (timeInterval) {
		clearInterval(timeInterval);
		timeInterval = null;
	}
}

function showFormError(message) {
	if (!formError) {
		return;
	}
	formError.textContent = message;
	formError.classList.remove('d-none');
	formError.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}

function clearFormError() {
	if (!formError) {
		return;
	}
	formError.textContent = '';
	formError.classList.add('d-none');
}

function appendConsoleLine(text, variant = 'info') {
	if (!consoleOutput) {
		return;
	}
	const entry = document.createElement('div');
	const timestamp = new Date().toLocaleTimeString();
	entry.innerHTML = `<span class="text-muted">[${timestamp}]</span> ${text}`;
	if (variant === 'success') {
		entry.classList.add('text-success', 'fw-bold');
	} else if (variant === 'danger') {
		entry.classList.add('text-danger', 'fw-bold');
	} else {
		entry.classList.add('text-muted');
	}
	consoleOutput.appendChild(entry);
	consoleOutput.scrollTop = consoleOutput.scrollHeight;
}

function applyStatusBadge(element, status) {
	if (!element) {
		return;
	}
	const normalized = (status || 'PENDING').toUpperCase();
	let badgeClass = 'bg-secondary';
	if (normalized === 'PROCESSING') badgeClass = 'bg-primary';
	if (normalized === 'COMPLETED') badgeClass = 'bg-success';
	if (normalized === 'FAILED') badgeClass = 'bg-danger';
	element.className = `badge ${badgeClass}`;
	element.textContent = normalized;
}

function normalizePercentage(job) {
	if (job?.progress && typeof job.progress.percentage === 'number') {
		return Math.max(0, Math.min(100, Math.round(job.progress.percentage)));
	}
	if (job?.status === 'COMPLETED') {
		return 100;
	}
	if (job?.status === 'FAILED') {
		return progressBar ? parseInt(progressBar.textContent, 10) || 0 : 0;
	}
	return 0;
}

function statusLabel(status) {
	switch ((status || '').toUpperCase()) {
		case 'PENDING':
			return 'Pending';
		case 'PROCESSING':
			return 'Processing';
		case 'COMPLETED':
			return 'Completed';
		case 'FAILED':
			return 'Failed';
		default:
			return status || 'Status';
	}
}

function refreshIcons() {
	if (window.lucide && typeof window.lucide.createIcons === 'function') {
		window.lucide.createIcons();
	}
}

