package com.kushal.deskclock;

import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextClock;

public class CustomFontTextClock extends TextClock {
    private Typeface customTypeface;

    public CustomFontTextClock(Context context) {
        super(context);
    }

    public CustomFontTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomFontTextClock(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void setTypeface(Typeface tf) {
        super.setTypeface(tf);
        this.customTypeface = tf;
    }

    @Override
    public void setText(CharSequence text, BufferType type) {
        super.setText(text, type);
        if (customTypeface != null && getTypeface() != customTypeface) {
            super.setTypeface(customTypeface);
        }
    }
}