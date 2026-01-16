import { Layout, Menu } from 'antd';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { Layers, GitCompare, ListChecks } from 'lucide-react';

const { Header, Content } = Layout;

export const MainLayout = () => {
  const location = useLocation();

  const menuItems = [
    {
      key: '/',
      label: <Link to="/">Single Validation</Link>,
      icon: <Layers size={16} />,
    },
    {
      key: '/batch',
      label: <Link to="/batch">Batch Validation</Link>,
      icon: <GitCompare size={16} />,
    },
    {
      key: '/cnf-checklist',
      label: <Link to="/cnf-checklist">CNF Checklist</Link>,
      icon: <ListChecks size={16} />,
    },
  ];

  return (
    <Layout className="layout" style={{ minHeight: '100vh' }}>
      <Header style={{ display: 'flex', alignItems: 'center', background: '#001529' }}>
        <div className="logo" style={{ color: 'white', fontSize: '1.2rem', fontWeight: 'bold', marginRight: '40px' }}>
          KValidator
        </div>
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[location.pathname]}
          items={menuItems}
          style={{ flex: 1, minWidth: 0 }}
        />
      </Header>
      <Content>
        <Outlet />
      </Content>
    </Layout>
  );
};
