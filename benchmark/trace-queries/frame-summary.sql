SELECT
  process.name AS process_name,
  COUNT(*) AS frame_count,
  ROUND(AVG(actual_frame_timeline_slice.dur) / 1000000.0, 3) AS average_ms,
  ROUND(MAX(actual_frame_timeline_slice.dur) / 1000000.0, 3) AS longest_ms,
  SUM(CASE WHEN actual_frame_timeline_slice.dur > 16670000 THEN 1 ELSE 0 END) AS over_16_67_ms
FROM actual_frame_timeline_slice
JOIN process USING (upid)
WHERE process.name = 'com.example.pocastcloni'
GROUP BY process.name;
