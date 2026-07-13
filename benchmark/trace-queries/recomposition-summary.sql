WITH measurement_window AS (
  SELECT ts AS start_ts, ts + dur AS end_ts, dur
  FROM slice
  WHERE name = 'measureBlock'
  LIMIT 1
),
app_slices AS (
  SELECT s.name, s.dur
  FROM slice s
  JOIN thread_track tt ON s.track_id = tt.id
  JOIN thread t ON tt.utid = t.utid
  JOIN process p ON t.upid = p.upid
  JOIN measurement_window w ON s.ts >= w.start_ts AND s.ts < w.end_ts
  WHERE p.name = 'com.example.pocastcloni'
    AND s.dur > 0
)
SELECT
  ROUND((SELECT dur FROM measurement_window) / 1000000.0, 3) AS window_ms,
  COALESCE(SUM(name = 'Recomposer:recompose'), 0) AS recompose_frames,
  ROUND(COALESCE(SUM(CASE WHEN name = 'Recomposer:recompose' THEN dur ELSE 0 END), 0) / 1000000.0, 3) AS recompose_ms,
  COALESCE(SUM(name = 'Compose:recompose'), 0) AS actual_recompose_slices,
  ROUND(COALESCE(SUM(CASE WHEN name = 'Compose:recompose' THEN dur ELSE 0 END), 0) / 1000000.0, 3) AS actual_recompose_ms,
  COALESCE(SUM(name = 'Recomposer:animation'), 0) AS animation_frames,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.MiniPlayerProgressBar (*'), 0) AS mini_progress,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.MiniPlayer (*'), 0) AS mini_player,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.MiniPlayerContent (*'), 0) AS mini_content,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerTimeLabels *'), 0) AS full_time_labels,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerScreen (*'), 0) AS full_player_screen,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerControls (*'), 0) AS full_controls,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerMetadataFlexibleCover (*'), 0) AS full_metadata_cover,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerProgressBar (*'), 0) AS full_progress,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.player.CustomProgressBar (*'), 0) AS custom_progress,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.home.feed.HomeScreen (*'), 0) AS home_screen,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.home.detail.PodcastDetailScreen (*'), 0) AS detail_screen,
  COALESCE(SUM(name GLOB 'com.example.pocastcloni.ui.home.common.PodcastGridItem (*'), 0) AS podcast_grid,
  COALESCE(SUM(name = 'AndroidOwner:measureAndLayout'), 0) AS measure_layout
FROM app_slices;
