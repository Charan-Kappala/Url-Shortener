-- Token bucket, evaluated atomically inside Redis (no other command can run between the read and the write).
-- KEYS[1]  bucket key, a hash with fields: tokens, ts (last refill time in ms)
-- ARGV[1]  capacity: max tokens, i.e. the largest burst allowed
-- ARGV[2]  refill rate in tokens per second
-- Returns  {1, 0} when allowed, {0, ms until one token is available} when rejected.
local capacity = tonumber(ARGV[1])
local rate_per_ms = tonumber(ARGV[2]) / 1000

-- Use Redis's clock, not the app's, so instances with skewed clocks still share one timeline.
local t = redis.call('TIME')
local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local state = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(state[1]) or capacity
local ts = tonumber(state[2]) or now

tokens = math.min(capacity, tokens + (now - ts) * rate_per_ms)

local allowed, retry_ms = 0, 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  retry_ms = math.ceil((1 - tokens) / rate_per_ms)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', now)
-- An idle bucket refills completely after capacity / rate; after that its state is not needed.
redis.call('PEXPIRE', KEYS[1], math.ceil(capacity / rate_per_ms))
return {allowed, retry_ms}
