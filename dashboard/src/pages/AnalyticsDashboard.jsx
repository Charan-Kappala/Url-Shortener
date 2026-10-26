import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { getAnalytics, getUrlInfo } from '../api/client'
import ClicksChart from '../components/ClicksChart'
import DeviceChart from '../components/DeviceChart'
import TopReferers from '../components/TopReferers'

const Section = ({ title, children }) => (
  <div className="glass-card" style={{ padding: 28 }}>
    <h3 style={{ margin: '0 0 20px', fontSize: 16, fontWeight: 700, color: 'var(--text-secondary)',
      textTransform: 'uppercase', letterSpacing: '0.06em' }}>
      {title}
    </h3>
    {children}
  </div>
)

export default function AnalyticsDashboard() {
  const { shortCode } = useParams()
  const [info, setInfo] = useState(null)
  const [analytics, setAnalytics] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  useEffect(() => {
    setLoading(true)
    Promise.all([getUrlInfo(shortCode), getAnalytics(shortCode)])
      .then(([i, a]) => { setInfo(i); setAnalytics(a) })
      .catch(() => setError('Could not load analytics for this short code.'))
      .finally(() => setLoading(false))
  }, [shortCode])

  if (loading) return (
    <div style={{ textAlign: 'center', padding: 80 }}>
      <div style={{ width: 40, height: 40, borderRadius: '50%',
        border: '3px solid var(--border)', borderTopColor: 'var(--accent)',
        animation: 'spin 0.8s linear infinite', margin: '0 auto 16px' }} />
      <p style={{ color: 'var(--text-muted)' }}>Loading analytics…</p>
      <style>{`@keyframes spin { to { transform: rotate(360deg) }}`}</style>
    </div>
  )

  if (error) return (
    <div style={{ textAlign: 'center', padding: 80 }}>
      <p style={{ color: 'var(--error)', fontSize: 16 }}>⚠️ {error}</p>
      <Link to="/" style={{ color: 'var(--accent-light)' }}>← Back to Home</Link>
    </div>
  )

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto', padding: '40px 24px' }}>
      {/* Header */}
      <div style={{ marginBottom: 32 }}>
        <Link to="/" style={{ color: 'var(--text-muted)', fontSize: 14, textDecoration: 'none', display: 'inline-flex', alignItems: 'center', gap: 6, marginBottom: 16 }}>
          ← Back
        </Link>
        <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', flexWrap: 'wrap', gap: 16 }}>
          <div>
            <h1 style={{ margin: 0, fontSize: 28, fontWeight: 800 }}>
              📊 <span className="gradient-text">/{shortCode}</span>
            </h1>
            <a href={info?.originalUrl} target="_blank" rel="noopener noreferrer"
              style={{ color: 'var(--text-muted)', fontSize: 13, textDecoration: 'none', marginTop: 4, display: 'block',
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: 520 }}>
              {info?.originalUrl}
            </a>
          </div>
          <div style={{ display: 'flex', gap: 12, alignItems: 'center' }}>
            <span className="badge" style={{ background: info?.isActive ? 'rgba(16,185,129,0.15)' : 'rgba(239,68,68,0.15)',
              color: info?.isActive ? 'var(--success)' : 'var(--error)' }}>
              {info?.isActive ? '● Active' : '● Inactive'}
            </span>
          </div>
        </div>
      </div>

      {/* Summary cards */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 16, marginBottom: 28 }}>
        {[
          { label: 'Total Clicks', value: analytics?.totalClicks?.toLocaleString() ?? '0', icon: '👆' },
          { label: 'Unique Days', value: analytics?.uniqueDays ?? '0', icon: '📅' },
          { label: 'Top Device', value: analytics?.topDevices?.[0]?.device ?? '—', icon: '📱' },
          { label: 'Top Referer', value: analytics?.topReferers?.[0]?.referer ?? '(direct)', icon: '🔗' },
        ].map(({ label, value, icon }) => (
          <div key={label} className="stat-card" style={{ padding: 20 }}>
            <p style={{ color: 'var(--text-muted)', fontSize: 12, margin: 0, textTransform: 'uppercase', letterSpacing: '0.05em' }}>{label}</p>
            <p style={{ fontSize: 22, fontWeight: 700, margin: '8px 0 0', color: 'var(--text-primary)', wordBreak: 'break-all' }}>
              {icon} {value}
            </p>
          </div>
        ))}
      </div>

      {/* Charts */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 20 }}>
        <Section title="Clicks over time">
          <ClicksChart data={analytics?.clicksByDay ?? []} />
        </Section>

        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 20 }}>
          <Section title="Device breakdown">
            <DeviceChart data={analytics?.topDevices ?? []} />
          </Section>
          <Section title="Top referrers">
            <TopReferers data={analytics?.topReferers ?? []} />
          </Section>
        </div>
      </div>

      {/* Meta */}
      <div style={{ marginTop: 24, padding: '16px 20px', borderRadius: 12, background: 'var(--bg-card)', border: '1px solid var(--border)',
        display: 'flex', gap: 32, flexWrap: 'wrap' }}>
        <div>
          <p style={{ color: 'var(--text-muted)', fontSize: 12, margin: 0 }}>Created</p>
          <p style={{ color: 'var(--text-secondary)', fontSize: 14, margin: '4px 0 0', fontWeight: 500 }}>
            {info?.createdAt ? new Date(info.createdAt).toLocaleString() : '—'}
          </p>
        </div>
        {info?.expiresAt && (
          <div>
            <p style={{ color: 'var(--text-muted)', fontSize: 12, margin: 0 }}>Expires</p>
            <p style={{ color: 'var(--warning)', fontSize: 14, margin: '4px 0 0', fontWeight: 500 }}>
              {new Date(info.expiresAt).toLocaleString()}
            </p>
          </div>
        )}
      </div>
    </div>
  )
}
