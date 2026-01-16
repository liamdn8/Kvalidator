import { ConfigProvider, App as AntApp } from 'antd';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { ValidationPage } from './pages/ValidationPage';
import { BatchValidationPage } from './pages/BatchValidationPage';
import { CNFChecklistPage } from './pages/CNFChecklistPage';
import { MainLayout } from './layouts/MainLayout';

function App() {
  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#3b82f6',
          borderRadius: 6,
        },
      }}
    >
      <AntApp>
        <BrowserRouter basename="/kvalidator/web">
          <Routes>
            <Route path="/" element={<MainLayout />}>
              <Route index element={<ValidationPage />} />
              <Route path="batch" element={<BatchValidationPage />} />
              <Route path="cnf-checklist" element={<CNFChecklistPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </AntApp>
    </ConfigProvider>
  );
}

export default App;
