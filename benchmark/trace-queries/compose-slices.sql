SELECT
  name,
  COUNT(*) AS occurrences,
  ROUND(SUM(dur) / 1000000.0, 3) AS total_ms,
  ROUND(MAX(dur) / 1000000.0, 3) AS longest_ms
FROM slice
WHERE dur > 0
  AND (
    name GLOB '*compose*' COLLATE NOCASE
    OR name GLOB '*Screen*'
    OR name GLOB '*MiniPlayer*'
    OR name GLOB '*ProgressBar*'
  )
GROUP BY name
ORDER BY occurrences DESC, total_ms DESC;
