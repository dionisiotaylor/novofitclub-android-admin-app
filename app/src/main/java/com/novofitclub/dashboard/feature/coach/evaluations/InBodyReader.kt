package com.novofitclub.dashboard.feature.coach.evaluations

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max

/**
 * Reads the numbers of an InBody result — a screenshot of the InBody app or
 * a photo of the printed sheet — from text recognized on the phone (ML Kit).
 * Each value is the unsigned number nearest below (or right of) its label;
 * the "-0.0" change badges, the dates and the scale words are skipped.
 * Same rules as the iOS app's InBodyReader.
 */
object InBodyReader {
    /** A recognized line; its box is normalized 0–1 from the top left. */
    class Line(val text: String, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val height get() = bottom - top
        val midY get() = (top + bottom) / 2
    }

    enum class Field(val label: String, val synonyms: List<String>, val range: ClosedFloatingPointRange<Double>) {
        Weight("Peso", listOf("peso", "peso corporal", "weight", "body weight"), 20.0..300.0),
        Muscle(
            "Músculo",
            listOf(
                "masa muscular", "masa muscular esqueletica", "masa musculo esqueletica", "masa de musculo esqueletico",
                "musculo esqueletico", "skeletal muscle mass", "muscle mass", "smm",
            ),
            5.0..120.0,
        ),
        FatKg("Grasa kg", listOf("grasa corporal", "masa grasa corporal", "masa de grasa corporal", "masa grasa", "body fat mass", "fat mass"), 1.0..200.0),
        FatPct(
            "Grasa %",
            listOf("porcentaje de grasa corporal", "porcentaje grasa corporal", "% de grasa corporal", "pgc", "percent body fat", "pbf", "body fat percentage"),
            2.0..75.0,
        ),
        Visceral("Visceral", listOf("nivel de grasa visceral", "nivel grasa visceral", "grasa visceral", "visceral fat level", "visceral fat"), 1.0..30.0),
        Height("Altura", listOf("altura", "estatura", "height"), 100.0..230.0),
    }

    /** Recognizes the image's text on the phone and reads the values. */
    suspend fun read(bitmap: Bitmap): Map<Field, Double> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val lines = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                Line(line.text, box.left / w, box.top / h, box.right / w, box.bottom / h)
            }
            return parse(lines)
        } finally {
            recognizer.close()
        }
    }

    fun parse(lines: List<Line>): Map<Field, Double> {
        val numbers = lines.mapNotNull { line -> leadingNumber(line.text)?.let { line to it } }
        val result = mutableMapOf<Field, Double>()
        for (field in Field.entries) {
            var best: Pair<Float, Double>? = null
            for (label in lines.filter { isLabel(it.text, field) }) {
                // "Peso 71.6 kg" on one line (printed sheets).
                val inline = trailingNumber(label.text, field)
                if (inline != null && inline in field.range) {
                    best = 0f to inline
                    break
                }
                val height = max(label.height, 0.01f)
                for ((line, value) in numbers) {
                    if (value !in field.range) continue
                    val below = line.midY > label.bottom - height * 0.3f && line.top - label.bottom < height * 4 &&
                        abs(line.left - label.left) < 0.2f
                    val beside = abs(line.midY - label.midY) < height * 0.8f && line.left > label.right - 0.01f &&
                        line.left - label.right < 0.5f
                    if (!below && !beside) continue
                    val score = if (below) max(0f, line.top - label.bottom) + abs(line.left - label.left) * 0.2f else (line.left - label.right) * 0.5f
                    if (best == null || score < best.first) best = score to value
                }
            }
            best?.let { result[field] = it.second }
        }
        return result
    }

    fun fold(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
            .replace('(', ' ').replace(')', ' ')
            .trim().split(Regex("\\s+")).joinToString(" ")

    /**
     * The line is this field's label: exactly a synonym, or a synonym followed
     * by a unit or the value ("Peso (kg)", "PGC 40.2 %"). Longer labels of other
     * fields don't count: "Porcentaje de grasa corporal" isn't "Grasa corporal".
     */
    fun isLabel(text: String, field: Field): Boolean {
        val folded = fold(text)
        return field.synonyms.any { synonym ->
            if (folded != synonym && !folded.startsWith("$synonym ")) return@any false
            val rest = folded.drop(synonym.length).trim()
            if (field == Field.FatKg && rest.contains('%')) return@any false
            rest.isEmpty() || rest.first().isDigit() || listOf("kg", "%", "lv", "cm", "nivel").any { rest.startsWith(it) }
        }
    }

    private val leading = Regex("""^(\d{1,3}(?:[.,]\d{1,2})?)(?![\d.,]*\d)""")

    /** "71.6 kg" → 71.6; "-0.0", "+1.2" and dates ("04.09.2025") → null. */
    fun leadingNumber(text: String): Double? {
        val trimmed = text.trim()
        if (trimmed.firstOrNull()?.isDigit() != true) return null
        return leading.find(trimmed)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
    }

    private fun trailingNumber(text: String, field: Field): Double? {
        val folded = fold(text)
        val synonym = field.synonyms.firstOrNull { folded.startsWith("$it ") } ?: return null
        return leadingNumber(folded.drop(synonym.length))
    }
}
