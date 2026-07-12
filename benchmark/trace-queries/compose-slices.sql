WITH measurement_window AS (
  SELECT ts AS start_ts, ts + dur AS end_ts
  FROM slice
  WHERE name = 'measureBlock'
  LIMIT 1
)
SELECT
  s.name,
  COUNT(*) AS occurrences,
  ROUND(SUM(s.dur) / 1000000.0, 3) AS total_ms,
  ROUND(MAX(s.dur) / 1000000.0, 3) AS longest_ms
FROM slice s
JOIN thread_track tt ON s.track_id = tt.id
JOIN thread t ON tt.utid = t.utid
JOIN process p ON t.upid = p.upid
JOIN measurement_window w ON s.ts >= w.start_ts AND s.ts < w.end_ts
WHERE p.name = 'com.example.pocastcloni'
  AND s.dur > 0
  AND (
    s.name GLOB 'com.example.pocastcloni.ui*'
    OR s.name IN (
      'Recomposer:recompose',
      'Compose:recompose',
      'Recomposer:animation',
      'AndroidOwner:measureAndLayout'
    )
  )
GROUP BY s.name
ORDER BY occurrences DESC, total_ms DESC;
