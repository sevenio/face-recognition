package com.tvisha.mtcnn;

import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Point;

/**
 * Face Alignment Correction
 */
public class Align {

    /**
     * Affine transformation
     * @param bitmap original image
     * @param landmarks landmarks
     * @return transformed image
     */
    public static Bitmap face_align(Bitmap bitmap, Point[] landmarks) {
        float diffEyeX = landmarks[1].x - landmarks[0].x;
        float diffEyeY = landmarks[1].y - landmarks[0].y;

        float fAngle;
        if (Math.abs(diffEyeY) < 1e-7) {
            fAngle = 0.f;
        } else {
            fAngle = (float) (Math.atan(diffEyeY / diffEyeX) * 180.0f / Math.PI);
        }
        Matrix matrix = new Matrix();
        matrix.setRotate(-fAngle);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}
