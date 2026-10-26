import { BrowserRouter, Routes, Route } from 'react-router-dom'
import './index.css'
import Navbar from './components/Navbar'
import Home from './pages/Home'
import AnalyticsDashboard from './pages/AnalyticsDashboard'

export default function App() {
  return (
    <BrowserRouter>
      <Navbar />
      <Routes>
        <Route path="/" element={<Home />} />
        <Route path="/analytics/:shortCode" element={<AnalyticsDashboard />} />
      </Routes>
    </BrowserRouter>
  )
}
