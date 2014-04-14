package org.lawrence.common.util;

import android.content.Context;
import android.util.TypedValue;

public class DimensionUtil {

    public static int dipToPix(Context context, float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }
    
    public static int spToPix(Context context, float textSize) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSize, 
                context.getResources().getDisplayMetrics());
    }
}
