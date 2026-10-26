import { useEffect, useState } from 'react'
import { getGlobalStats } from '../api/client'

const StatItem = ({ label, value, icon, color }) => (
  <div className="stat-card">
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
      <div>
        <p style={{ color: 'var(--text-muted)', fontSize: 13, fontWeight: 500, margin: 0, letterSpacing: '0.05em', textTransform: 'uppercase' }}>
          {label}
        </p>
        <p style={{ color: 'var(--text-primary)', fontSize: 36, fontWeight: 700, margin: '8px 0 0', lineHeight: 1 }}>
          {value?.toLocaleString() ?? '—'}
        </p>
      </div>
      <span style={{ fontSize: 28, opacity: 0.8 }}>{icon}</span>
    </div>
  </div>
)

export default function GlobalStats() {
  const [stats, setStats] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    getGlobalStats()
      .then(setStats)
      .catch(() => setError('Could not load stats'))
  }, [])

  if (error) return (
    <div style={{ color: 'var(--error)', textAlign: 'center', padding: 16 }}>{error}</div>
  )

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 20 }}>
      <StatItem label="Total URLs" value={stats?.totalUrlsCreated} icon="🔗" />
      <StatItem label="Total Clicks" value={stats?.totalClicks} icon="👆" />
      <StatItem label="Created Today" value={stats?.urlsCreatedToday} icon="📅" />
    </div>
  )
}
