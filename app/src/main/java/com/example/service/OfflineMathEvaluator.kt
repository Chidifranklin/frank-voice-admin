package com.example.service

import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.pow
import kotlin.math.sqrt

data class MathEvalResult(
    val success: Boolean,
    val spokenAnswer: String,
    val displayText: String
)

object OfflineMathEvaluator {

    private val df = DecimalFormat("#,##0.##")

    /**
     * Attempts to parse and evaluate an offline math or unit conversion query.
     * Returns null if the query is not a recognized mathematical or conversion pattern.
     */
    fun evaluate(query: String): MathEvalResult? {
        val clean = query.trim().lowercase()
            .removePrefix("what is ")
            .removePrefix("what's ")
            .removePrefix("calculate ")
            .removePrefix("how much is ")
            .removePrefix("evaluate ")
            .removePrefix("solve ")
            .removeSuffix("?")
            .trim()

        if (clean.isBlank()) return null

        // 1. Check unit conversions
        val conversion = evaluateConversion(clean)
        if (conversion != null) return conversion

        // 2. Percentages: "15 percent of 200", "20% of 500", "what is 25% of 80"
        val percentMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:percent of|% of|%)\\s*(\\d+(?:\\.\\d+)?)").find(clean)
        if (percentMatch != null) {
            val pct = percentMatch.groupValues[1].toDoubleOrNull() ?: return null
            val total = percentMatch.groupValues[2].toDoubleOrNull() ?: return null
            val result = (pct / 100.0) * total
            val formatted = df.format(result)
            return MathEvalResult(
                success = true,
                spokenAnswer = "$pct percent of $total is $formatted.",
                displayText = "$pct% of $total = $formatted"
            )
        }

        // 3. Square root: "square root of 144", "sqrt of 81", "sqrt 25"
        val sqrtMatch = Regex("(?:square root of|sqrt of|sqrt)\\s*(\\d+(?:\\.\\d+)?)").find(clean)
        if (sqrtMatch != null) {
            val num = sqrtMatch.groupValues[1].toDoubleOrNull() ?: return null
            if (num < 0) return MathEvalResult(false, "Cannot calculate square root of a negative number.", "Error: sqrt($num)")
            val result = sqrt(num)
            val formatted = df.format(result)
            return MathEvalResult(
                success = true,
                spokenAnswer = "The square root of $num is $formatted.",
                displayText = "√$num = $formatted"
            )
        }

        // 4. Power: "2 to the power of 8", "3 squared", "5 cubed", "2 power 10"
        if (clean.contains("squared")) {
            val num = clean.removeSuffix("squared").trim().toDoubleOrNull()
            if (num != null) {
                val res = num * num
                val formatted = df.format(res)
                return MathEvalResult(true, "$num squared is $formatted.", "$num² = $formatted")
            }
        }
        if (clean.contains("cubed")) {
            val num = clean.removeSuffix("cubed").trim().toDoubleOrNull()
            if (num != null) {
                val res = num * num * num
                val formatted = df.format(res)
                return MathEvalResult(true, "$num cubed is $formatted.", "$num³ = $formatted")
            }
        }
        val powerMatch = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:to the power of|power of|power|\\^)\\s*(\\d+(?:\\.\\d+)?)").find(clean)
        if (powerMatch != null) {
            val base = powerMatch.groupValues[1].toDoubleOrNull() ?: return null
            val exp = powerMatch.groupValues[2].toDoubleOrNull() ?: return null
            val result = base.pow(exp)
            val formatted = df.format(result)
            return MathEvalResult(true, "$base to the power of $exp is $formatted.", "$base^$exp = $formatted")
        }

        // 5. Binary arithmetic: addition, subtraction, multiplication, division
        // Normalize words to operators
        var expr = clean
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("x", "*")
            .replace("divided by", "/")
            .replace("over", "/")
            .replace("plus", "+")
            .replace("and", "+")
            .replace("minus", "-")
            .replace("subtracted from", "SUB_FROM")
            .replace("subtract", "-")
            .replace(" ", "")

        if (expr.contains("SUB_FROM")) {
            val parts = expr.split("SUB_FROM")
            if (parts.size == 2) {
                val a = parts[0].toDoubleOrNull()
                val b = parts[1].toDoubleOrNull()
                if (a != null && b != null) {
                    val res = b - a
                    val formatted = df.format(res)
                    return MathEvalResult(true, "$b minus $a is $formatted.", "$b - $a = $formatted")
                }
            }
        }

        // Simple binary operations: a op b
        val binaryMatch = Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(-?\\d+(?:\\.\\d+)?)").matchEntire(expr)
        if (binaryMatch != null) {
            val a = binaryMatch.groupValues[1].toDoubleOrNull() ?: return null
            val op = binaryMatch.groupValues[2]
            val b = binaryMatch.groupValues[3].toDoubleOrNull() ?: return null
            val result = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> {
                    if (b == 0.0) return MathEvalResult(false, "Cannot divide by zero.", "Division by zero")
                    a / b
                }
                else -> return null
            }
            val formatted = df.format(result)
            val opName = when (op) {
                "+" -> "plus"
                "-" -> "minus"
                "*" -> "times"
                "/" -> "divided by"
                else -> op
            }
            return MathEvalResult(
                success = true,
                spokenAnswer = "$a $opName $b is $formatted.",
                displayText = "$a $op $b = $formatted"
            )
        }

        return null
    }

    private fun evaluateConversion(query: String): MathEvalResult? {
        val match = Regex("(?:convert )?(\\d+(?:\\.\\d+)?)\\s*([a-z]+)\\s*(?:to|in|into)\\s*([a-z]+)").find(query)
            ?: return null

        val value = match.groupValues[1].toDoubleOrNull() ?: return null
        val fromUnit = match.groupValues[2]
        val toUnit = match.groupValues[3]

        // Miles <-> Kilometers
        if ((fromUnit in listOf("mile", "miles", "mi")) && (toUnit in listOf("km", "kilometer", "kilometers", "kilometres"))) {
            val res = value * 1.60934
            return result(value, "miles", df.format(res), "kilometers")
        }
        if ((fromUnit in listOf("km", "kilometer", "kilometers", "kilometres")) && (toUnit in listOf("mile", "miles", "mi"))) {
            val res = value / 1.60934
            return result(value, "kilometers", df.format(res), "miles")
        }

        // Feet <-> Meters
        if ((fromUnit in listOf("feet", "foot", "ft")) && (toUnit in listOf("meter", "meters", "m"))) {
            val res = value * 0.3048
            return result(value, "feet", df.format(res), "meters")
        }
        if ((fromUnit in listOf("meter", "meters", "m")) && (toUnit in listOf("feet", "foot", "ft"))) {
            val res = value / 0.3048
            return result(value, "meters", df.format(res), "feet")
        }

        // Inches <-> Centimeters
        if ((fromUnit in listOf("inch", "inches", "in")) && (toUnit in listOf("cm", "centimeter", "centimeters"))) {
            val res = value * 2.54
            return result(value, "inches", df.format(res), "centimeters")
        }
        if ((fromUnit in listOf("cm", "centimeter", "centimeters")) && (toUnit in listOf("inch", "inches", "in"))) {
            val res = value / 2.54
            return result(value, "centimeters", df.format(res), "inches")
        }

        // Celsius <-> Fahrenheit
        if ((fromUnit in listOf("c", "celsius", "centigrade")) && (toUnit in listOf("f", "fahrenheit"))) {
            val res = (value * 9.0 / 5.0) + 32.0
            return result(value, "°C", df.format(res), "°F")
        }
        if ((fromUnit in listOf("f", "fahrenheit")) && (toUnit in listOf("c", "celsius", "centigrade"))) {
            val res = (value - 32.0) * 5.0 / 9.0
            return result(value, "°F", df.format(res), "°C")
        }

        // Kilograms <-> Pounds
        if ((fromUnit in listOf("kg", "kilogram", "kilograms", "kilos")) && (toUnit in listOf("lb", "lbs", "pound", "pounds"))) {
            val res = value * 2.20462
            return result(value, "kg", df.format(res), "lbs")
        }
        if ((fromUnit in listOf("lb", "lbs", "pound", "pounds")) && (toUnit in listOf("kg", "kilogram", "kilograms", "kilos"))) {
            val res = value / 2.20462
            return result(value, "lbs", df.format(res), "kg")
        }

        // Hours <-> Minutes
        if ((fromUnit in listOf("hour", "hours", "hr", "hrs")) && (toUnit in listOf("minute", "minutes", "min", "mins"))) {
            val res = value * 60
            return result(value, "hours", df.format(res), "minutes")
        }
        if ((fromUnit in listOf("minute", "minutes", "min", "mins")) && (toUnit in listOf("hour", "hours", "hr", "hrs"))) {
            val res = value / 60
            return result(value, "minutes", df.format(res), "hours")
        }

        return null
    }

    private fun result(fromVal: Double, fromUnit: String, toVal: String, toUnit: String): MathEvalResult {
        return MathEvalResult(
            success = true,
            spokenAnswer = "$fromVal $fromUnit is equal to $toVal $toUnit.",
            displayText = "$fromVal $fromUnit = $toVal $toUnit"
        )
    }
}
