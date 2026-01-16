# Kvalidator Frontend

React-based web UI for Kubernetes Resource Validator.

## Tech Stack

- **React 18** with TypeScript
- **Vite 5** - Fast build tool
- **Ant Design** - UI components
- **Lucide React** - Icons
- **Axios** - HTTP client
- **XLSX** - Excel export

## Architecture

```
Frontend (React)          Backend (Quarkus)
Port: 3000               Port: 8080
┌─────────────┐          ┌──────────────┐
│             │  Proxy   │              │
│   Vite Dev  │ ──────>  │   REST API   │
│   Server    │  /api/*  │              │
│             │          │              │
└─────────────┘          └──────────────┘
```

## Development

### Prerequisites
- Node.js 18+
- npm 10+
- Quarkus backend running on port 8080

### Install Dependencies
```bash
cd frontend
npm install
```

### Start Development Server
```bash
npm run dev
```

App will be available at: **http://localhost:3000**

### Build for Production
```bash
npm run build
```

Output: `dist/` folder

## Project Structure

```
frontend/
├── src/
│   ├── components/
│   │   ├── BaselineSetup.tsx      # YAML or namespace baseline
│   │   ├── TargetSelection.tsx    # Target namespace selection
│   │   └── ValidationResults.tsx  # Results table & stats
│   ├── pages/
│   │   └── ValidationPage.tsx     # Main page layout
│   ├── services/
│   │   └── api.ts                 # API client
│   ├── types/
│   │   └── index.ts               # TypeScript interfaces
│   ├── App.tsx                    # App root
│   └── main.tsx                   # Entry point
├── vite.config.ts                 # Vite configuration (proxy setup)
├── tsconfig.json                  # TypeScript config
└── package.json
```

## API Integration

### Proxy Configuration
Vite dev server proxies `/api/*` requests to Quarkus backend (localhost:8080).

See `vite.config.ts`:
```typescript
server: {
  port: 3000,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    }
  }
}
```

### Endpoints Used
- `GET /api/kubernetes/clusters` - List clusters
- `GET /api/kubernetes/namespaces?cluster={name}` - List namespaces
- `GET /api/kubernetes/namespaces/search?keyword={keyword}` - Search namespaces
- `POST /api/validate` - Start validation

## Features

### 1. Baseline Configuration
- **YAML Mode**: Paste YAML content directly
- **Namespace Mode**: Select cluster + namespace as baseline

### 2. Target Selection
- **Search**: Find namespaces by keyword across all clusters
- **Manual**: Select cluster → namespace → Add
- **List Management**: View and remove targets

### 3. Validation
- Auto-format namespaces as "cluster/namespace"
- Real-time validation feedback
- Loading states with Ant Design Spin

### 4. Results Display
- Summary statistics with icons
- Detailed results table
- Color-coded status badges (Match/Mismatch/Missing/Extra)
- Excel export functionality

## Component Details

### BaselineSetup
Props:
- `onBaselineChange`: Callback when baseline config changes

Features:
- Radio toggle between YAML/Namespace
- Auto-load clusters on mount
- Cascade loading: cluster → namespaces

### TargetSelection
Props:
- `targets`: Array of ClusterNamespace
- `onTargetsChange`: Update targets array

Features:
- Search with results preview
- Dropdown selections
- Duplicate detection
- Add/remove targets

### ValidationResults
Props:
- `result`: ValidationResult | null

Features:
- Statistics cards with Ant Design Statistic
- Sortable table with pagination
- Excel export with XLSX library
- Differences column with expandable list

## Styling

Uses Ant Design's ConfigProvider for theming:
- Primary color: `#3b82f6` (blue)
- Border radius: `6px`

Custom styles:
- Gradient header
- Modern card layouts
- Responsive design

## Error Handling

- API errors shown via Ant Design `message`
- Console logging for debugging
- Loading states prevent double-submission
- Input validation before API calls

## Production Build

1. Build:
   ```bash
   npm run build
   ```

2. Output: `dist/` folder contains:
   - `index.html`
   - `assets/` (JS, CSS, images)

3. Deploy options:
   - Serve from Quarkus (copy to `src/main/resources/META-INF/resources/`)
   - Separate web server (nginx, Apache)
   - CDN hosting

4. Environment variables:
   Update API base URL in production:
   ```typescript
   const api = axios.create({
     baseURL: import.meta.env.VITE_API_URL || '/api',
   });
   ```

## Troubleshooting

### Port 3000 already in use
```bash
# Kill process on port 3000
lsof -ti:3000 | xargs kill -9

# Or use different port
vite --port 3001
```

### API calls fail (CORS)
Ensure Quarkus has CORS enabled in `application.properties`:
```properties
quarkus.http.cors=true
quarkus.http.cors.origins=http://localhost:3000
quarkus.http.cors.methods=GET,POST,PUT,DELETE,OPTIONS
```

### Icons not showing
Lucide icons require React 18+. Check versions:
```bash
npm list react lucide-react
```

### Build fails
Clear cache and reinstall:
```bash
rm -rf node_modules package-lock.json
npm install
npm run build
```

## Testing

### Manual Testing Workflow
1. Start backend: `cd .. && mvn quarkus:dev`
2. Start frontend: `npm run dev`
3. Open browser: http://localhost:3000
4. Test scenarios:
   - YAML baseline + 1 target
   - Namespace baseline + multiple targets
   - Search namespaces
   - Validation with results
   - Excel export

### API Testing
Use browser DevTools Network tab to inspect:
- Request payloads
- Response data
- Error messages
- Loading times

## Next Steps

Optional enhancements:
- [ ] File upload for YAML (instead of paste)
- [ ] WebSocket for real-time progress
- [ ] Validation history/cache
- [ ] Dark mode toggle
- [ ] PDF export
- [ ] Batch validation from CSV

## License

Same as parent project (Apache 2.0)
