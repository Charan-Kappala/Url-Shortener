import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
} from 'recharts'

const CustomTooltip = ({ active, payload, label }) => {
  if (!active || !payload?.length) return null
  return (
    <div style={{
      background: 'var(--bg-card)', border: '1px solid var(--border)',
      borderRadius: 10, padding: '10px 16px',
    }}>
      <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: 12 }}>{label}</p>
      <p style={{ margin: '4px 0 0', color: 'var(--accent-light)', fontWeight: 700, fontSize: 18 }}>
        {payload[0].value} clicks
      </p>
    </div>
  )
}

export default function ClicksChart({ data = [] }) {
  if (!data.length) return (
    <div style={{ textAlign: 'center', padding: 48, color: 'var(--text-muted)' }}>
      No click data yet for this time range.
    </div>
  )

  return (
    <ResponsiveContainer width="100%" height={280}>
      <LineChart data={data} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#2a2d3e" vertical={false} />
        <XAxis dataKey="date" stroke="#64748b" tick={{ fill: '#94a3b8', fontSize: 12 }} tickLine={false} />
        <YAxis stroke="#64748b" tick={{ fill: '#94a3b8', fontSize: 12 }} tickLine={false} axisLine={false} allowDecimals={false} />
        <Tooltip content={<CustomTooltip />} cursor={{ stroke: 'var(--accent)', strokeWidth: 1, strokeDasharray: '4 4' }} />
        <Line
          type="monotone"
          dataKey="count"
          stroke="url(#lineGradient)"
          strokeWidth={2.5}
          dot={{ fill: 'var(--accent)', r: 4, strokeWidth: 0 }}
          activeDot={{ fill: 'var(--accent-light)', r: 6, strokeWidth: 0 }}
        />
        <defs>
          <linearGradient id="lineGradient" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" stopColor="#6366f1" />
            <stop offset="100%" stopColor="#06b6d4" />
          </linearGradient>
        </defs>
      </LineChart>
    </ResponsiveContainer>
  )
}
