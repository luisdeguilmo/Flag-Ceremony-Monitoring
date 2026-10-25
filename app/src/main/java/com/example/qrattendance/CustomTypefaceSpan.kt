package com.example.qrattendance

import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.TypefaceSpan

class CustomTypefaceSpan(private val newTypeface: Typeface) : TypefaceSpan("") {
    override fun updateDrawState(ds: TextPaint) {
        applyCustomTypeface(ds, newTypeface)
    }

    override fun updateMeasureState(paint: TextPaint) {
        applyCustomTypeface(paint, newTypeface)
    }

    private fun applyCustomTypeface(paint: Paint, typeface: Typeface) {
        paint.apply {
            this.typeface = typeface
            isFakeBoldText = false
        }
    }
}

