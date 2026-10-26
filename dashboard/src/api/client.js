import axios from 'axios'

const BASE = import.meta.env.VITE_API_BASE_URL || ''

const api = axios.create({
  baseURL: BASE,
  timeout: 10000,
  headers: { 'Content-Type': 'application/json' },
})

/** POST /api/v1/urls — create short URL */
export const createShortUrl = (payload) =>
  api.post('/api/v1/urls', payload).then((r) => r.data)

/** GET /api/v1/urls/:shortCode — URL info + click count */
export const getUrlInfo = (shortCode) =>
  api.get(`/api/v1/urls/${shortCode}`).then((r) => r.data)

/** GET /api/v1/urls/:shortCode/analytics — full analytics */
export const getAnalytics = (shortCode, from, to) => {
  const params = {}
  if (from) params.from = from
  if (to) params.to = to
  return api.get(`/api/v1/urls/${shortCode}/analytics`, { params }).then((r) => r.data)
}

/** GET /api/v1/stats — global stats */
export const getGlobalStats = () =>
  api.get('/api/v1/stats').then((r) => r.data)

export default api
