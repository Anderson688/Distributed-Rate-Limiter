-- KEYS[1]: The unique identifier (e.g., rl:service:LOCAL:user_123)
-- ARGV[1]: Max requests allowed (limit)
-- ARGV[2]: Window duration in milliseconds
-- ARGV[3]: A unique request ID (UUID)

local key = KEYS[1]
local limit = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local unique_request_id = ARGV[3]

-- Get the exact time from the Redis server's clock
local redis_time = redis.call('TIME')
-- redis_time[1] is seconds, redis_time[2] is microseconds
local current_ms = math.floor((tonumber(redis_time[1]) * 1000) + (tonumber(redis_time[2]) / 1000))

local window_start = current_ms - window_ms

-- Step 1: Remove all timestamps older than the current window
redis.call('ZREMRANGEBYSCORE', key, 0, window_start)

-- Step 2: Count how many requests are left in the set
local current_count = redis.call('ZCARD', key)
local allowed = 0
local retry_after = 0

if current_count < limit then
    -- Step 3: If under limit, add current request and allow
    local unique_member = current_ms .. "-" .. unique_request_id
    redis.call('ZADD', key, current_ms, unique_member)
    allowed = 1
    current_count = current_count + 1
else
    -- Step 4: If over limit, find the oldest timestamp to calculate retry-after
    local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
    if oldest[2] ~= nil then
        retry_after = oldest[2] + window_ms - current_ms
    end
end

-- Refresh TTL so the set eventually cleans itself up
redis.call('PEXPIRE', key, window_ms)

-- Return format: [allowed_flag, retry_after_ms, count]
return {allowed, math.max(0, retry_after), current_count}