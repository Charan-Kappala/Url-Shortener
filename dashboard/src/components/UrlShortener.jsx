import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { createShortUrl } from '../api/client'

export default function UrlShortener() {
  const [originalUrl, setOriginalUrl] = useState('')
  const [customCode, setCustomCode] = useState('')
  const [expiresInDays, setExpiresInDays] = useState('')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [copied, setCopied] = useState(false)
  const navigate = useNavigate()

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError(null)
    setResult(null)
    setLoading(true)
    try {
      const payload = { originalUrl }
      if (customCode.trim()) payload.customCode = customCode.trim()
      if (expiresInDays) payload.expiresInDays = Number(expiresInDays)
      const data = await createShortUrl(payload)
      setResult(data)
    } catch (err) {
      setError(err.response?.data?.error || 'Failed to shorten URL')
    } finally {
      setLoading(false)
    }
  }

  const copy = async () => {
    await navigator.clipboard.writeText(result.shortUrl)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  return (
    <div className="glass-card" style={{ padding: 32 }}>
      <h2 style={{ margin: '0 0 4px', fontSize: 22, fontWeight: 700 }}>✂️ Shorten a URL</h2>
      <p style={{ color: 'var(--text-muted)', margin: '0 0 24px', fontSize: 14 }}>
        Paste a long URL and get a short link instantly.
      </p>

      <form onSubmit={handleSubmit}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <input
            className="input-field"
            id="url-input"
            type="url"
            placeholder="https://example.com/very/long/url"
            value={originalUrl}
            onChange={(e) => setOriginalUrl(e.target.value)}
            required
          />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <input
              className="input-field"
              id="custom-code-input"
              type="text"
              placeholder="Custom code (optional)"
              value={customCode}
              onChange={(e) => setCustomCode(e.target.value)}
              maxLength={10}
            />
            <input
              className="input-field"
              id="expires-input"
              type="number"
              placeholder="Expires in days (optional)"
              value={expiresInDays}
              onChange={(e) => setExpiresInDays(e.target.value)}
              min={1}
            />
          </div>
          <button className="btn-primary" id="shorten-btn" type="submit" disabled={loading} style={{ alignSelf: 'flex-start' }}>
            {loading ? 'Shortening…' : '⚡ Shorten'}
          </button>
        </div>
      </form>

      {error && (
        <div style={{
          marginTop: 16, padding: '12px 16px', borderRadius: 10,
          background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.3)',
          color: 'var(--error)', fontSize: 14,
        }}>
          ⚠️ {error}
        </div>
      )}

      {result && (
        <div style={{
          marginTop: 20, padding: 20, borderRadius: 12,
          background: 'rgba(99,102,241,0.08)', border: '1px solid rgba(99,102,241,0.25)',
          animation: 'fadeIn 0.3s ease',
        }}>
          <p style={{ color: 'var(--text-muted)', fontSize: 12, margin: '0 0 8px', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
            Your short URL
          </p>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <a
              href={result.shortUrl}
              target="_blank"
              rel="noopener noreferrer"
              style={{ color: 'var(--accent-light)', fontSize: 20, fontWeight: 700, textDecoration: 'none' }}
            >
              {result.shortUrl}
            </a>
            <button
              onClick={copy}
              style={{
                background: copied ? 'var(--success)' : 'var(--accent-glow)',
                color: copied ? 'white' : 'var(--accent-light)',
                border: `1px solid ${copied ? 'var(--success)' : 'var(--accent)'}`,
                borderRadius: 8, padding: '6px 14px', cursor: 'pointer',
                fontSize: 13, fontWeight: 600, transition: 'all 0.2s',
              }}
            >
              {copied ? '✓ Copied!' : '📋 Copy'}
            </button>
            <button
              onClick={() => navigate(`/analytics/${result.shortCode}`)}
              style={{
                background: 'transparent', color: 'var(--text-secondary)',
                border: '1px solid var(--border)', borderRadius: 8,
                padding: '6px 14px', cursor: 'pointer', fontSize: 13, fontWeight: 600,
              }}
            >
              📊 Analytics
            </button>
          </div>
          {result.expiresAt && (
            <p style={{ color: 'var(--text-muted)', fontSize: 12, marginTop: 8 }}>
              Expires {new Date(result.expiresAt).toLocaleDateString()}
            </p>
          )}
        </div>
      )}
    </div>
  )
}
