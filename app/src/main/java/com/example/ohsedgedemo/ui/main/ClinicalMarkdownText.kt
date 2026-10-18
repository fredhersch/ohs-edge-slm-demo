package com.example.ohsedgedemo.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ClinicalMarkdownView(
    text: String,
    modifier: Modifier = Modifier
) {
    val items = text.split("\n").filter { it.isNotBlank() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { rawLine ->
            ClinicalLineItem(line = rawLine.trim())
        }
    }
}

@Composable
private fun ClinicalLineItem(line: String) {
    val (bulletType, cleanContent) = extractBulletAndContent(line)

    // Extract grounding citation if present, e.g. [Grounded in MNH-02 § ANC-04]
    val groundingRegex = Regex("\\[(Grounded[^\\]]+)\\]", RegexOption.IGNORE_CASE)
    val groundingMatch = groundingRegex.find(cleanContent)
    val groundingTag = groundingMatch?.groupValues?.get(1)
    val textWithoutTag = if (groundingMatch != null) {
        cleanContent.removeRange(groundingMatch.range).replace(" :", ":").replace("  ", " ").trim()
    } else {
        cleanContent
    }

    val isDanger = textWithoutTag.contains("immediate referral", ignoreCase = true) ||
            textWithoutTag.contains("emergency", ignoreCase = true) ||
            textWithoutTag.contains("pre-eclampsia", ignoreCase = true) ||
            textWithoutTag.contains("critical", ignoreCase = true) ||
            textWithoutTag.contains("forbid", ignoreCase = true) ||
            textWithoutTag.contains("fatal", ignoreCase = true)

    val isWarning = textWithoutTag.contains("danger", ignoreCase = true) ||
            textWithoutTag.contains("overdue", ignoreCase = true) ||
            textWithoutTag.contains("anemia", ignoreCase = true) ||
            textWithoutTag.contains("headache", ignoreCase = true) ||
            textWithoutTag.contains("148/96", ignoreCase = true) ||
            textWithoutTag.contains("6.4", ignoreCase = true)

    val itemBgColor = when {
        isDanger -> Color(0xFFFEF2F2)
        isWarning -> Color(0xFFFFFBEB)
        else -> Color(0xFFF8FAFC)
    }

    val itemBorderColor = when {
        isDanger -> Color(0xFFFECACA)
        isWarning -> Color(0xFFFDE68A)
        else -> Color(0xFFE2E8F0)
    }

    Surface(
        color = itemBgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = itemBorderColor, shape = RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (bulletType != null) {
                BulletBadge(bullet = bulletType, isDanger = isDanger, isWarning = isWarning)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (groundingTag != null) {
                    Surface(
                        color = Color(0xFFECFDF5),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF6EE7B7)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("🌿", fontSize = 10.sp)
                            Text(
                                text = groundingTag,
                                color = Color(0xFF047857),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = buildClinicalAnnotatedString(textWithoutTag),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color(0xFF1E293B)
                )
            }
        }
    }
}

@Composable
private fun BulletBadge(bullet: String, isDanger: Boolean, isWarning: Boolean) {
    val badgeBg = when {
        isDanger -> Color(0xFFDC2626)
        isWarning -> Color(0xFFD97706)
        else -> Color(0xFF0284C7)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(22.dp)
            .background(badgeBg, CircleShape)
    ) {
        Text(
            text = bullet,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
    }
}

private fun extractBulletAndContent(line: String): Pair<String?, String> {
    // Strip header hashes e.g. "### Header"
    val headerRegex = Regex("^#{1,6}\\s*(.*)")
    val headerMatch = headerRegex.find(line)
    if (headerMatch != null) {
        return Pair("§", headerMatch.groupValues[1])
    }

    val numberedRegex = Regex("^(\\d+)\\.\\s*(.*)")
    val bulletRegex = Regex("^[•\\-*]\\s*(.*)")

    val numberedMatch = numberedRegex.find(line)
    if (numberedMatch != null) {
        val num = numberedMatch.groupValues[1]
        val content = numberedMatch.groupValues[2]
        return Pair(num, content)
    }

    val bulletMatch = bulletRegex.find(line)
    if (bulletMatch != null) {
        val content = bulletMatch.groupValues[1]
        return Pair("•", content)
    }

    return Pair(null, line)
}

fun buildClinicalAnnotatedString(rawText: String): AnnotatedString {
    // 1. Remove markdown bold markers (**text**) and record bold ranges
    val boldRanges = mutableListOf<IntRange>()
    val cleanBuilder = StringBuilder()
    var i = 0

    while (i < rawText.length) {
        if (i + 1 < rawText.length && rawText[i] == '*' && rawText[i + 1] == '*') {
            val endIdx = rawText.indexOf("**", i + 2)
            if (endIdx != -1) {
                val boldContent = rawText.substring(i + 2, endIdx)
                val start = cleanBuilder.length
                cleanBuilder.append(boldContent)
                val end = cleanBuilder.length
                boldRanges.add(start until end)
                i = endIdx + 2
                continue
            } else {
                // Unclosed bold marker during streaming
                val boldContent = rawText.substring(i + 2)
                val start = cleanBuilder.length
                cleanBuilder.append(boldContent)
                val end = cleanBuilder.length
                boldRanges.add(start until end)
                break
            }
        }
        cleanBuilder.append(rawText[i])
        i++
    }

    val plainText = cleanBuilder.toString()

    return buildAnnotatedString {
        append(plainText)

        // Apply bold spans
        boldRanges.forEach { range ->
            addStyle(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                ),
                start = range.first,
                end = range.last + 1
            )
        }

        // Apply Smart Clinical Color Highlights
        val dangerKeywords = listOf(
            "148/96 mmHg", "148/96", "Pre-Eclampsia", "pre-eclampsia",
            "Immediate Referral", "Emergency", "emergency", "CRITICAL WARNING",
            "fatal bleeding", "hemorrhage", "strictly FORBID", "NEVER perform",
            "Severe Anemia", "6.4 g/dL", "Critically Low", "Critically low"
        )

        val warningKeywords = listOf(
            "headache", "Proteinuria", "proteinuria", "1+ urine", "104 bpm",
            "Tachycardia", "pallor", "overdue", "convulsions", "visual disturbances"
        )

        val safeGuardedKeywords = listOf(
            "negative", "fully resolved", "Normal", "Decline Antimalarials",
            "will not help", "routine oral iron", "District Hospital", "Obstetric Center"
        )

        val groundedKeywords = listOf(
            "Grounded in", "Grounded:", "Safety Boundary", "Protocol Action",
            "Absolute Contraindication", "Triage Criteria", "MOH Protocol", "eCHIS"
        )

        // Highlight Critical Danger
        highlightWords(plainText, dangerKeywords, Color(0xFFDC2626), Color(0xFFFEE2E2))

        // Highlight Warnings
        highlightWords(plainText, warningKeywords, Color(0xFFB45309), Color(0xFFFEF3C7))

        // Highlight Safeguards / Protocols
        highlightWords(plainText, safeGuardedKeywords, Color(0xFF1D4ED8), Color(0xFFEFF6FF))

        // Highlight Grounded Protocol Citations
        highlightWords(plainText, groundedKeywords, Color(0xFF047857), Color(0xFFECFDF5))
    }
}

private fun AnnotatedString.Builder.highlightWords(
    text: String,
    keywords: List<String>,
    textColor: Color,
    bgColor: Color
) {
    for (keyword in keywords) {
        var startIdx = text.indexOf(keyword, ignoreCase = true)
        while (startIdx != -1) {
            val endIdx = startIdx + keyword.length
            addStyle(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    color = textColor,
                    background = bgColor
                ),
                start = startIdx,
                end = endIdx
            )
            startIdx = text.indexOf(keyword, startIndex = endIdx, ignoreCase = true)
        }
    }
}
