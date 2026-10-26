import { PieChart, Pie, Cell, Tooltip, Legend, ResponsiveContainer } from 'recharts'

const COLORS = ['#6366f1', '#06b6d4', '#8b5cf6', '#10b981', '#f59e0b', '#ef4444']

const CustomTooltip = ({ active, payload }) => {
  if (!active || !payload?.length) return null
  return (
    <div style={{ background: 'var(--bg-card)', border: '1px solid var(--border)', borderRadius: 10, padding: '10px 16px' }}>
      <p style={{ margin: 0, color: 'var(--text-primary)', fontWeight: 700 }}>{payload[0].name}</p>
      <p style={{ margin: '4px 0 0', color: 'var(--accent-light)' }}>{payload[0].value} clicks</p>
    </div>
  )
}

export default function DeviceChart({ data = [] }) {
  if (!data.length) return (
    <div style={{ textAlign: 'center', padding: 48, color: 'var(--text-muted)' }}>No device data yet.</div>
  )
  const chartData = data.map((d) => ({ name: d.device, value: Number(d.count) }))

  return (
    <ResponsiveContainer width="100%" height={260}>
      <PieChart>
        <Pie data={chartData} cx="50%" cy="50%" innerRadius={60} outerRadius={100}
          paddingAngle={3} dataKey="value" nameKey="name">
          {chartData.map((_, i) => (
            <Cell key={i} fill={COLORS[i % COLORS.length]} />
          ))}
        </Pie>
        <Tooltip content={<CustomTooltip />} />
        <Legend
          formatter={(v) => <span style={{ color: 'var(--text-secondary)', fontSize: 13 }}>{v}</span>}
        />
      </PieChart>
    </ResponsiveContainer>
  )
}
