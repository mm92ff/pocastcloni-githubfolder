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
  SUM(name = 'Recomposer:recompose') AS recompose_frames,
  ROUND(SUM(CASE WHEN name = 'Recomposer:recompose' THEN dur ELSE 0 END) / 1000000.0, 3) AS recompose_ms,
  SUM(name = 'Compose:recompose') AS actual_recompose_slices,
  ROUND(SUM(CASE WHEN name = 'Compose:recompose' THEN dur ELSE 0 END) / 1000000.0, 3) AS actual_recompose_ms,
  SUM(name = 'Recomposer:animation') AS animation_frames,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.MiniPlayerProgressBar (*') AS mini_progress,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.MiniPlayer (*') AS mini_player,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.MiniPlayerContent (*') AS mini_content,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerTimeLabels *') AS full_time_labels,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerScreen (*') AS full_player_screen,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerControls (*') AS full_controls,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerMetadataFlexibleCover (*') AS full_metadata_cover,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.FullPlayerProgressBar (*') AS full_progress,
  SUM(name GLOB 'com.example.pocastcloni.ui.player.CustomProgressBar (*') AS custom_progress,
  SUM(name GLOB 'com.example.pocastcloni.ui.home.feed.HomeScreen (*') AS home_screen,
  SUM(name GLOB 'com.example.pocastcloni.ui.home.detail.PodcastDetailScreen (*') AS detail_screen,
  SUM(name GLOB 'com.example.pocastcloni.ui.home.common.PodcastGridItem (*') AS podcast_grid,
  SUM(name = 'AndroidOwner:measureAndLayout') AS measure_layout
FROM app_slices;
