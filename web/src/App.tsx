import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { LandingPage } from './pages/LandingPage';
import { DashboardPage } from './pages/DashboardPage';
import { GuidePage } from './pages/GuidePage';
import { SweatzonePage } from './pages/SweatzonePage';

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingPage />} />
        <Route path="/play" element={<DashboardPage />} />
        <Route path="/guide" element={<GuidePage />} />
        <Route path="/sweatzone" element={<SweatzonePage />} />
      </Routes>
    </BrowserRouter>
  );
}
