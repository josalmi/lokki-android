/*
Copyright (c) 2014-2015 F-Secure
See LICENSE for details
*/
package com.fsecure.lokki.avatar;


import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ImageView;

import com.fsecure.lokki.utils.Utils;

import java.lang.ref.WeakReference;

public class AvatarLoader {

    private static final String TAG = "AvatarLoader";
    private Context context;

    public AvatarLoader(Context myContext) {

        context = myContext;
    }

    public static boolean cancelPotentialWork(String data, ImageView imageView) {

        BitmapWorkerTask task = getTaskFromView(imageView);

        if (task != null) {
            if (!task.data.equals(data)) {
                Log.e(TAG, "cancelPotentialWork: Cancel previous task"); // Cancel previous task
                task.cancel(true);
            } else {
                Log.e(TAG, "cancelPotentialWork: The same work is already in progress"); // The same work is already in progress
                return false;
            }
        }
        Log.e(TAG, "cancelPotentialWork: No task associated with the ImageView, or an existing task was cancelled"); // No task associated with the ImageView, or an existing task was cancelled
        return true;
    }

    private static BitmapWorkerTask getTaskFromView(ImageView imageView) {

        BitmapWorkerTask task = null;

        if (imageView != null) {
            //Log.e(TAG, "Tag: " + imageView.getTag());
            if (imageView.getTag() instanceof WeakReference) {
                task = ((WeakReference<BitmapWorkerTask>) (WeakReference) imageView.getTag()).get();
            }
        }
        return task;
    }

    public void load(String email, ImageView imageView) {

        Log.e(TAG, "1) load: " + email);
        if (cancelPotentialWork(email, imageView)) {
            Log.e(TAG, "load: Creating new task");
            final BitmapWorkerTask task = new BitmapWorkerTask(imageView);
            final WeakReference<BitmapWorkerTask> taskReference = new WeakReference<>(task);
            imageView.setTag(taskReference);
            task.execute(email);
        }
    }

    private Bitmap processData(String email) {

        Log.e(TAG, "processData");
        return Utils.getPhotoFromEmail(context, email);
    }

    class BitmapWorkerTask extends AsyncTask<String, Void, Bitmap> {

        private final WeakReference<ImageView> imageViewReference;
        private String data = "";

        public BitmapWorkerTask(ImageView imageView) {
            // Use a WeakReference to ensure the ImageView can be garbage collected
            imageViewReference = new WeakReference<>(imageView);
        }

        // Decode image in background.
        @Override
        protected Bitmap doInBackground(String... params) {

            Log.e(TAG, "BitmapWorkerTask: doInBackground: " + params[0]);
            data = params[0];
            return processData(data);
        }

        // Once complete, see if ImageView is still around and set bitmap.
        @Override
        protected void onPostExecute(Bitmap bitmap) {

            Log.e(TAG, "BitmapWorkerTask: onPostExecute");
            if (isCancelled()) {
                bitmap = null;
            }

            if (bitmap != null) {
                final ImageView imageView = imageViewReference.get();
                if (imageView != null) {

                    BitmapWorkerTask task = getTaskFromView(imageView);

                    if (this == task) {
                        imageView.setImageBitmap(bitmap);
                        imageView.setTag(data);
                    }
                }
            }
        }
    }

}
