-- KEYS[1]: The unique identifier
-- ARGV[1]: Bucket capacity (limit)
-- ARGV[2]: Time to refill the entire bucket in ms (window)
-- ARGV[3]: A unique request ID (UUID) (unused in basic token bucket but kept for signature consistency)

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refill_time_total = tonumber(ARGV[2])

-- Get the exact time from the Redis server's clock
local redis_time = redis.call('TIME')
-- redis_time[1] is seconds, redis_time[2] is microseconds
local current_ms = math.floor((tonumber(redis_time[1]) * 1000) + (tonumber(redis_time[2]) / 1000))

-- Time required to generate a single token
local time_per_token = refill_time_total / capacity

local bucket = redis.call('HMGET', key, 't', 'l')
local tokens = tonumber(bucket[1]) or capacity
local last_refill = tonumber(bucket[2]) or current_ms

-- Calculate how many tokens were generated since the last request
local delta = math.max(0, current_ms - last_refill)
local refilled_tokens = math.floor(delta / time_per_token)

-- Update state
tokens = math.min(capacity, tokens + refilled_tokens)
if refilled_tokens > 0 then
    -- Move the refill clock forward by the number of tokens actually added
    last_refill = last_refill + (refilled_tokens * time_per_token)
end

local allowed = 0
if tokens > 0 then
    tokens = tokens - 1
    allowed = 1
end

redis.call('HMSET', key, 't', tokens, 'l', last_refill)
redis.call('PEXPIRE', key, refill_time_total)

local retry_after = 0
if allowed == 0 then
    -- Wait time is the remaining duration until the next token is generated
    retry_after = time_per_token - (current_ms - last_refill)
end

return {allowed, math.max(0, retry_after), tokens}