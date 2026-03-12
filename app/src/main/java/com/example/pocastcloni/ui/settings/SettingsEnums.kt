package com.example.pocastcloni.ui.settings

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

enum class BufferMode {
    NORMAL, MAXIMAL
}

enum class AppColor(val hexValue: Long) {
    GREEN(0xFF4CAF50),
    RED(0xFFE53935),
    BLUE(0xFF2196F3),
    YELLOW(0xFFFFEB3B),
    PURPLE(0xFF9C27B0),
    ORANGE(0xFFFF9800),
    TURQUOISE(0xFF00BCD4);

    companion object {
        fun getByOrdinal(ordinal: Int): AppColor = entries.getOrElse(ordinal) { GREEN }
    }
}