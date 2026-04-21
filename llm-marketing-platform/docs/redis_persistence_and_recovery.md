# Redis persistence and recovery notes

This project uses Redis for:
- lottery probability map cache (`lottery_map:{activityId}`)
- prize stock cache (`lottery_stock:{activityId}:{prizeId}`)
- request idempotency lock (`lottery_req_lock:{activityId}:{requestId}`)

## Current recommendation

For small data and non-critical loss tolerance:
- use **RDB-only** persistence first
- mount `/data` to Docker volume
- keep `appendonly no`

Reference files:
- `ops/redis/redis-rdb.conf`
- `ops/docker-compose.redis-rdb.yml`

## Start example

```powershell
cd ops
docker compose -f docker-compose.redis-rdb.yml up -d
```

## Verify persistence status

```powershell
docker exec redis redis-cli CONFIG GET save
docker exec redis redis-cli CONFIG GET appendonly
docker exec redis redis-cli INFO persistence
```

Expected:
- `save` has snapshot rules
- `appendonly` is `no` (RDB-only mode)

## Inspect data

- redis admin web: `http://localhost:8081`
- username/password (default in compose): `admin / admin`

Useful keys:

```redis
SCAN 0 MATCH lottery_* COUNT 200
HLEN lottery_map:1
GET lottery_stock:1:1
```

## Recovery behavior after restart

If Redis restarts and RDB is loaded correctly:
- cache keys can be restored from `dump.rdb`

If keys are still missing:
- call publish/preheat API again
- project startup auto-preheat also attempts to refill online activities (see Java initializer)

