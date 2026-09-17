# Weather requests

The cron expression remains `0 0/10 * * * ?` in `Asia/Manila`.
Automatic Open-Meteo requests are permitted from 05:00 inclusive to 18:00
exclusive Philippine Standard Time (UTC+8). A normal uninterrupted schedule
therefore makes 78 requests per day, ending at 17:50, instead of 144.

Startup checks and `/api/health` use the same restriction. A shared ten-minute
cooldown between automatic request attempts prevents overlapping cron and health
checks from adding requests, including after API failures. The cron continues
running overnight; skipped requests do not create new weather readings.

The Diagnostics **Trigger Weather Fetch Now** button uses an admin-only POST
to `/test-weather` and bypasses both the time restriction and cooldown. It returns
to Diagnostics, where the last request result is shown without another API call.

Dashboard, forecast, and Diagnostics visits reuse the most recently fetched data.
The forecast cache is held in memory and resets on application restart; until the
next permitted or manual fetch, forecast pages show an unavailable message.
Previously saved weather observations remain in the database.

The existing `WEATHER_CRON_ENABLED` switch is unchanged. Enable it to run the
internal scheduler; external health checks still follow the request window.
Cooldown and cache are per application instance, so multiple replicas or restarts
can add requests. Manual requests also count toward API usage.

Run the isolated scheduling tests with `mvn -Dtest=WeatherApiClientTests test`.
