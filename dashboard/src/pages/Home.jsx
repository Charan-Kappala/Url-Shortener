import GlobalStats from '../components/GlobalStats'
import UrlShortener from '../components/UrlShortener'

export default function Home() {
  return (
    <div style={{ maxWidth: 800, margin: '0 auto', padding: '48px 24px' }}>
      {/* Hero */}
      <div style={{ textAlign: 'center', marginBottom: 48 }}>
        <div style={{
          display: 'inline-block', padding: '4px 16px', borderRadius: 999,
          background: 'var(--accent-glow)', border: '1px solid var(--accent)',
          color: 'var(--accent-light)', fontSize: 12, fontWeight: 700,
          letterSpacing: '0.08em', textTransform: 'uppercase', marginBottom: 20,
        }}>
          Production-Grade URL Shortener
        </div>
        <h1 style={{ fontSize: 'clamp(36px,5vw,56px)', fontWeight: 900, margin: 0, lineHeight: 1.1 }}>
          <span className="gradient-text">Shorten</span>{' '}
          <span style={{ color: 'var(--text-primary)' }}>any link,</span><br />
          <span style={{ color: 'var(--text-secondary)', fontWeight: 400 }}>instantly.</span>
        </h1>
        <p style={{ color: 'var(--text-muted)', fontSize: 16, marginTop: 16 }}>
          Powered by Spring Boot · Redis · PostgreSQL · AWS
        </p>
      </div>

      {/* Stats */}
      <div style={{ marginBottom: 36 }}>
        <h2 style={{ fontSize: 14, fontWeight: 700, color: 'var(--text-muted)',
          textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 16 }}>
          Live Stats
        </h2>
        <GlobalStats />
      </div>

      {/* Shortener form */}
      <UrlShortener />

      {/* Feature pills */}
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, marginTop: 32, justifyContent: 'center' }}>
        {['Redis Cache ⚡', 'Rate Limiting 🛡️', 'Click Analytics 📊', 'Custom Codes ✏️', 'Link Expiry ⏰', 'SQS Pipeline 🔄'].map((f) => (
          <span key={f} className="badge badge-accent">{f}</span>
        ))}
      </div>
    </div>
  )
}
