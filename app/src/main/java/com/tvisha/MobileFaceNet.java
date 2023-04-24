package com.tvisha;

import android.content.res.AssetManager;
import android.graphics.Bitmap;


import org.tensorflow.lite.Interpreter;

import java.io.IOException;

/**
 * face comparison
 */
public class MobileFaceNet {
    private static final String MODEL_FILE = "MobileFaceNet.tflite";

    public static final int INPUT_IMAGE_SIZE = 112; // The width and height of the placeholder image that needs feed data
    public static final float THRESHOLD = 0.8f; // Set a threshold, greater than this value is considered to be the same person

    private Interpreter interpreter;

    public MobileFaceNet(AssetManager assetManager) throws IOException {
        Interpreter.Options options = new Interpreter.Options();
        options.setNumThreads(4);
        interpreter = new Interpreter(MyUtil.loadModelFile(assetManager, MODEL_FILE), options);
    }

//    public float compare(Bitmap bitmap1, Bitmap bitmap2) {
//        // Resize the face to a size of 112X112, because the shape of the placeholder that needs feed data below is (2, 112, 112, 3)
//        Bitmap bitmapScale1 = Bitmap.createScaledBitmap(bitmap1, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);
//        Bitmap bitmapScale2 = Bitmap.createScaledBitmap(bitmap2, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);
//
//        float[][][][] datasets = getTwoImageDatasets(bitmapScale1, bitmapScale2);
//        float[][] embeddings = new float[2][192];
//        interpreter.run(datasets, embeddings);
//        MyUtil.l2Normalize(embeddings, 1e-10);
//        return evaluate(embeddings);
//    }

    public float compare(Bitmap bitmap1, Bitmap bitmap2) {
        // Resize the face to a size of 112X112, because the shape of the placeholder that needs feed data below is (2, 112, 112, 3)
        Bitmap bitmapScale1 = Bitmap.createScaledBitmap(bitmap1, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);
        Bitmap bitmapScale2 = Bitmap.createScaledBitmap(bitmap2, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);

        return evaluate(getImageEmbedding(bitmapScale1)[0], getImageEmbedding(bitmapScale2)[0]);
    }
//    /**
//     * Calculate the similarity of two pictures, using l2 loss
//     * @param embeddings
//     * @return
//     */

//    private float evaluate(float[][] embeddings) {
//        float[] embeddings1 = embeddings[0];
//        float[] embeddings2 = embeddings[1];
//        float dist = 0;
//        for (int i = 0; i < 192; i++) {
//            dist += Math.pow(embeddings1[i] - embeddings2[i], 2);
//        }
//        float same = 0;
//        for (int i = 0; i < 400; i++) {
//            float threshold = 0.01f * (i + 1);
//            if (dist < threshold) {
//                same += 1.0 / 400;
//            }
//        }
//        return same;
//    }
    private float evaluate(float[] embeddings1, float[] embeddings2) {

        float dist = 0;
        for (int i = 0; i < 192; i++) {
            dist += Math.pow(embeddings1[i] - embeddings2[i], 2);
        }
        float same = 0;
        for (int i = 0; i < 400; i++) {
            float threshold = 0.01f * (i + 1);
            if (dist < threshold) {
                same += 1.0 / 400;
            }
        }
        return same;
    }
    /**
     * Convert two images to normalized data
     * @param bitmap1
     * @param bitmap2
     * @return
     */
    private float[][][][] getTwoImageDatasets(Bitmap bitmap1, Bitmap bitmap2) {
        Bitmap[] bitmaps = {bitmap1, bitmap2};

        int[] ddims = {bitmaps.length, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, 3};
        float[][][][] datasets = new float[ddims[0]][ddims[1]][ddims[2]][ddims[3]];

        for (int i = 0; i < ddims[0]; i++) {
            Bitmap bitmap = bitmaps[i];
            datasets[i] = MyUtil.normalizeImage(bitmap);
        }
        return datasets;
    }

    /**
     * Convert image to normalized data
     * @param bitmap
     * @return
     */

    private float[][][][] getNormalizedImageDataset(Bitmap bitmap) {
        float[][][][] datasets = new float[2][INPUT_IMAGE_SIZE][INPUT_IMAGE_SIZE][3];
        datasets[0] = MyUtil.normalizeImage(bitmap);
        return datasets;
    }

    public float[][] getImageEmbedding(Bitmap bitmap) {
        Bitmap bitmapScale = Bitmap.createScaledBitmap(bitmap, INPUT_IMAGE_SIZE, INPUT_IMAGE_SIZE, true);
        float[][][][] dataset = getNormalizedImageDataset(bitmapScale);
        float[][] embedding = new float[2][192];
        interpreter.run(dataset, embedding);
        MyUtil.l2Normalize(embedding, 1e-10);
        return embedding;
    }
}
