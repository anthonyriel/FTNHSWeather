# Weather requests

The cron expression remains `0 0/10 * * * ?` in `Asia/Manila`.
Automatic Open-Meteo requests are permitted from 05:00 inclusive to 18:00
exclusive Philippine Standard Time (UTC+8). Each request contains nine weather
variables (six current and three hourly), keeping it below Open-Meteo's
multi-call threshold. A normal uninterrupted schedule
therefore makes 78 requests per day, ending at 17:50, instead of 144.

Startup checks and `/api/health` use the same restriction. Each clock-aligned
ten-minute slot (:00–:09, :10–:19, etc.) permits one automatic request attempt,
including failed attempts. Overlapping cron and health checks share this guard.
Delivery variations such as 11:00:26 followed by 11:10:14 no longer skip a slot.
The cron continues running overnight; skipped requests do not create new readings.

The Diagnostics **Trigger Weather Fetch Now** button uses an admin-only POST
to `/test-weather` and bypasses both the time restriction and slot guard. A manual
request satisfies the current slot without delaying the next slot. It returns
to Diagnostics, where the last request result is shown without another API call.

Dashboard, forecast, and Diagnostics visits reuse the most recently fetched data.
The forecast cache is held in memory and resets on application restart; until the
next permitted or manual fetch, forecast pages show an unavailable message.
Previously saved weather observations remain in the database.

The existing `WEATHER_CRON_ENABLED` switch is unchanged. Enable it to run the
internal scheduler; external health checks still follow the request window.
The slot guard and cache are per application instance, so multiple replicas or restarts
can add requests. Manual requests also count toward API usage.

Records retain their actual observation-fetch timestamps. This guard does not
backfill missed observations or guarantee a record when a trigger is missing,
the app is offline, or an API/database operation fails.

Run the isolated scheduling tests with `mvn -Dtest=WeatherApiClientTests test`.
