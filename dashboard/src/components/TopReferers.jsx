export default function TopReferers({ data = [] }) {
  if (!data.length) return (
    <div style={{ textAlign: 'center', padding: 32, color: 'var(--text-muted)', fontSize: 14 }}>
      No referrer data yet.
    </div>
  )

  const max = Math.max(...data.map((d) => Number(d.count)), 1)

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
      {data.map((item, i) => {
        const pct = Math.round((Number(item.count) / max) * 100)
        return (
          <div key={i}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 5 }}>
              <span style={{ color: 'var(--text-primary)', fontSize: 14, fontWeight: 500,
                overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '70%' }}>
                {item.referer || '(direct)'}
              </span>
              <span style={{ color: 'var(--accent-light)', fontSize: 14, fontWeight: 700 }}>
                {Number(item.count).toLocaleString()}
              </span>
            </div>
            <div style={{ height: 6, borderRadius: 3, background: 'var(--border)', overflow: 'hidden' }}>
              <div style={{
                height: '100%', width: `${pct}%`, borderRadius: 3,
                background: 'linear-gradient(90deg, #6366f1, #06b6d4)',
                transition: 'width 0.6s ease',
              }} />
            </div>
          </div>
        )
      })}
    </div>
  )
}
