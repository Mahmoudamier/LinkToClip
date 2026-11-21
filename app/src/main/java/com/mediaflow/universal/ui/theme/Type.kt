package com.mediaflow.universal.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Every style explicitly uses the Arabic font. Leaving the defaults untouched made small
 * labels fall back to Roboto, which is why Arabic text mixed with a Latin-looking font.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = ArabicFont, fontSize = 48.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontFamily = ArabicFont, fontSize = 38.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontFamily = ArabicFont, fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontFamily = ArabicFont, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontFamily = ArabicFont, fontSize = 24.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = ArabicFont, fontSize = 21.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = ArabicFont, fontSize = 19.sp, fontWeight = FontWeight.Medium),
    titleMedium = TextStyle(fontFamily = ArabicFont, fontSize = 16.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = ArabicFont, fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = ArabicFont, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = ArabicFont, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = ArabicFont, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = ArabicFont, fontSize = 15.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = ArabicFont, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = ArabicFont, fontSize = 11.sp, fontWeight = FontWeight.Medium)
)

val AppShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp)
)
