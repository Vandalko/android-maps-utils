package com.google.maps.android.kml;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.v4.util.LruCache;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.Marker;
import com.google.maps.android.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Renders all visible KmlPlacemark and KmlGroundOverlay objects onto the GoogleMap as Marker,
 * Polyline, Polygon, GroundOverlay objects. Also removes objects from the map.
 */
public class DefaultKmlRenderer extends AbsKmlRenderer {

    private static final String LOG_TAG = "DefaultKmlRenderer";

    private static final int LRU_CACHE_SIZE = 50;

    private final LruCache<String, Bitmap> mImagesCache;

    public DefaultKmlRenderer(GoogleMap map, Context context) {
        super(map, context);
        mImagesCache = new LruCache<>(LRU_CACHE_SIZE);
    }

    @Override
    public void downloadMarkerIcon(String markerIconUrl) {
        new MarkerIconImageDownload(markerIconUrl).execute();
    }

    @Override
    public void downloadGroundOverlay(String groundOverlayUrl) {
        new GroundOverlayImageDownload(groundOverlayUrl).execute();
    }

    @Override
    public Bitmap getCachedBitmap(String url) {
        return mImagesCache.get(url);
    }

    @Override
    void addLayerToMap() {
        super.addLayerToMap();
        createInfoWindow();
    }

    @Override
    void removeLayerFromMap() {
        super.removeLayerFromMap();
        getMap().setInfoWindowAdapter(null);
    }

    /**
     * Creates a new InfoWindowAdapter and sets text if marker snippet or title is set. This allows
     * the info window to have custom HTML.
     */
    private void createInfoWindow() {
        getMap().setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter() {

            public View getInfoWindow(Marker arg0) {
                return null;
            }

            public View getInfoContents(Marker arg0) {
                View view =  LayoutInflater.from(getContext()).inflate(R.layout.amu_info_window, null);
                TextView infoWindowText = (TextView) view.findViewById(R.id.window);
                if (arg0.getSnippet() != null) {
                    infoWindowText.setText(Html.fromHtml(arg0.getTitle() + "<br>" + arg0.getSnippet()));
                } else {
                    infoWindowText.setText(Html.fromHtml(arg0.getTitle()));
                }
                return view;
            }
        });
    }

    /**
     * Downloads images for use as marker icons
     */
    private class MarkerIconImageDownload extends ImageDownloadTask {

        /**
         * Creates a new IconImageDownload object
         *
         * @param iconUrl URL of the marker icon to download
         */
        public MarkerIconImageDownload(String iconUrl) {
            super(iconUrl);
        }

        /**
         * Adds the bitmap to the cache and adds the bitmap to the markers
         *
         * @param bitmap bitmap downloaded
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null) {
                onMarkerIconDownloaded(mImageUrl);
            }
        }
    }

    /**
     * Downloads images for use as ground overlays
     */
    private class GroundOverlayImageDownload extends ImageDownloadTask {

        public GroundOverlayImageDownload(String groundOverlayUrl) {
            super(groundOverlayUrl);
        }

        /**
         * Adds the bitmap to the ground overlay and places it on a map
         *
         * @param bitmap bitmap downloaded
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            if (bitmap != null) {
                onGroundOverlayDownloaded(mImageUrl);
            }
        }
    }

    private class ImageDownloadTask extends AsyncTask<String, Void, Bitmap> {

        final String mImageUrl;

        public ImageDownloadTask(String imageUrl) {
            mImageUrl = imageUrl;
        }

        /** Do our best to decode image. */
        private Bitmap tryDecodeLocalBitmap() {
            InputStream stream = null;
            try {
                stream = getContext().getAssets().open(mImageUrl);
                return BitmapFactory.decodeStream(stream);
            } catch (IOException assetsIOE) {
                try {
                    stream = getContext().getContentResolver().openInputStream(Uri.parse(mImageUrl));
                    return BitmapFactory.decodeStream(stream);
                } catch (IOException contentIOE) {
                    return BitmapFactory.decodeFile(mImageUrl);
                }
            } finally {
                closeQuitely(stream);
            }
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            InputStream stream = null;
            Bitmap bitmap = null;
            try {
                try {
                    stream = (InputStream) new URL(mImageUrl).getContent();
                    bitmap = BitmapFactory.decodeStream(stream);
                } catch (MalformedURLException e) {
                    // Content is not a URL.
                    bitmap = tryDecodeLocalBitmap();
                }
            } catch (IOException e) {
                Log.e(LOG_TAG, "Image [" + mImageUrl + "] download issue", e);
            } finally {
                closeQuitely(stream);
            }

            return bitmap;
        }

        private void closeQuitely(InputStream stream) {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore.
                }
            }
        }

        /**
         * Adds the bitmap to the ground overlay and places it on a map
         *
         * @param bitmap bitmap downloaded
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap == null) {
                Log.e(LOG_TAG, "Image at this URL could not be found " + mImageUrl);
            } else {
                mImagesCache.put(mImageUrl, bitmap);
            }
        }
    }
}
