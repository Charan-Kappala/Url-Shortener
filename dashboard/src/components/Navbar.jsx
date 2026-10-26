import { Link, useLocation } from 'react-router-dom'

export default function Navbar() {
  const { pathname } = useLocation()

  return (
    <nav style={{
      position: 'sticky', top: 0, zIndex: 100,
      background: 'rgba(15,17,23,0.85)', backdropFilter: 'blur(12px)',
      borderBottom: '1px solid var(--border)',
      padding: '0 24px',
    }}>
      <div style={{
        maxWidth: 1100, margin: '0 auto',
        display: 'flex', alignItems: 'center', justifyContent: 'space-between',
        height: 60,
      }}>
        <Link to="/" style={{ textDecoration: 'none' }}>
          <span style={{ fontSize: 20, fontWeight: 800 }}>
            <span className="gradient-text">⚡ Snip</span>
            <span style={{ color: 'var(--text-secondary)' }}>Link</span>
          </span>
        </Link>
        <div style={{ display: 'flex', gap: 8 }}>
          <Link to="/" style={{
            padding: '6px 14px', borderRadius: 8, textDecoration: 'none',
            fontSize: 14, fontWeight: 600,
            color: pathname === '/' ? 'var(--accent-light)' : 'var(--text-muted)',
            background: pathname === '/' ? 'var(--accent-glow)' : 'transparent',
            transition: 'all 0.2s',
          }}>Home</Link>
          <a href="/actuator/health" target="_blank" rel="noopener noreferrer" style={{
            padding: '6px 14px', borderRadius: 8, textDecoration: 'none',
            fontSize: 14, fontWeight: 600, color: 'var(--text-muted)',
          }}>API ↗</a>
        </div>
      </div>
    </nav>
  )
}
