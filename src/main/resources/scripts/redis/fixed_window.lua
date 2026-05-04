-- KEYS[1]: The unique identifier
-- ARGV[1]: Max requests allowed (limit)
-- ARGV[2]: Window duration in milliseconds
-- ARGV[3]: Current timestamp (unused in basic fixed window but kept for signature consistency)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])

local current_count = redis.call('INCR', key)

-- If this is the first request in the window, set the expiration
if current_count == 1 then
    redis.call('PEXPIRE', key, window_ms)
end

local allowed = 0
if current_count <= limit then
    allowed = 1
else
    allowed = 0
end

-- The retry_after is simply the remaining TTL of the key
local ttl = redis.call('PTTL', key)

return {allowed, math.max(0, ttl), current_count}