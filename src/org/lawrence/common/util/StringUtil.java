package org.lawrence.common.util;

import android.graphics.Rect;
import android.text.TextPaint;

public class StringUtil {

	/**
     * get the width for a string with specified paint.
     * @Title: getStringsWidth 
     * @Description: TODO
     * @param paint
     * @param text
     * @return string width
     * @return: int
     */
    public static int getStringsWidth(TextPaint paint, String text) {
        Rect rect = new Rect();
        paint.getTextBounds(text, 0, text.length(), rect);
        return rect.width();
    }
}
