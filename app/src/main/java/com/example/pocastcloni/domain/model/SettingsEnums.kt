package com.example.pocastcloni.domain.model

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

enum class BufferMode {
    NORMAL,
    MAXIMAL
}

enum class GradientDirection {
    TOP_TO_BOTTOM,
    BOTTOM_TO_TOP,
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
    TOP_LEFT_TO_BOTTOM_RIGHT,
    BOTTOM_RIGHT_TO_TOP_LEFT,
    TOP_RIGHT_TO_BOTTOM_LEFT,
    BOTTOM_LEFT_TO_TOP_RIGHT
}

enum class AppColor(val hexValue: Long) {
    GREEN(0xFF4CAF50),
    RED(0xFFE53935),
    BLUE(0xFF2196F3),
    YELLOW(0xFFFFEB3B),
    PURPLE(0xFF9C27B0),
    ORANGE(0xFFFF9800),
    TURQUOISE(0xFF00BCD4)
    ;

    companion object {
        fun getByOrdinal(ordinal: Int): AppColor = entries.getOrElse(ordinal) { GREEN }
    }
}
