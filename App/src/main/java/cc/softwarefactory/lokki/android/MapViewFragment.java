/*
Copyright (c) 2014-2015 F-Secure
See LICENSE for details
*/
package cc.softwarefactory.lokki.android;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;

import com.androidquery.AQuery;

import cc.softwarefactory.lokki.android.utils.MapUtils;
import cc.softwarefactory.lokki.android.utils.Utils;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.VisibleRegion;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;


public class MapViewFragment extends Fragment {

    private static final String TAG = "MapViewFragment";
    private SupportMapFragment fragment;
    private GoogleMap map;
    private HashMap<String, Marker> markerMap;
    private AQuery aq;
    private static Boolean cancelAsyncTasks = false;
    private Context context;
    private Boolean firstTimeZoom = true;
    private ArrayList<Circle> placesOverlay;
    private double radiusMultiplier = 0.9;  // Dont want to fill the screen from edge to edge...
    private Drawable d;

    public MapViewFragment() {
        markerMap = new HashMap<String, Marker>();
        placesOverlay = new ArrayList<Circle>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.activity_map, container, false);
        aq = new AQuery(getActivity(), rootView);
        context = getActivity().getApplicationContext();
        return rootView;

    }

    @Override
    public void onDestroyView() {
        // Trying to clean up properties (not to hold anything coming from the map (and avoid mem leaks).
        super.onDestroyView();
        fragment = null;
        map = null;
        aq = null;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) { // This method guarantees that the fragment is loaded in the parent activity!

        Log.e(TAG, "onActivityCreated");
        super.onActivityCreated(savedInstanceState);

        FragmentManager fm = getChildFragmentManager();
        fragment = (SupportMapFragment) fm.findFragmentById(R.id.map);
        if (fragment == null) {
            fragment = SupportMapFragment.newInstance();
            //fragment = SupportMapFragment.newInstance(new GoogleMapOptions().useViewLifecycleInFragment(true)); // The map is destroyed when fragment is destroyed. Releasing memory
            fm.beginTransaction().replace(R.id.map, fragment).commit();
        }

        //setHasOptionsMenu(true);
    }


    @Override
    public void onResume() { // onResume is called after onActivityCreated, when the fragment is loaded 100%

        Log.e(TAG, "onResume");
        super.onResume();
        if (map == null) {
            Log.e(TAG, "Map null. creating it.");
            setUpMap();
            setupAddPlacesOverlay();
        } else {
            Log.e(TAG, "Map already exists. Nothing to do.");
        }

        LocalBroadcastManager.getInstance(context).registerReceiver(mMessageReceiver, new IntentFilter("LOCATION-UPDATE"));
        LocalBroadcastManager.getInstance(context).registerReceiver(placesUpdateReceiver, new IntentFilter("PLACES-UPDATE"));


        //new UpdateMap().execute(0); // All users
        //new UpdateMap().execute(1); // All users
        new UpdateMap().execute(2); // All users
        cancelAsyncTasks = false;
        if (MainApplication.places != null) {
            updatePlaces();
        }
    }

    private void setUpMap() {

        map = fragment.getMap();

        if (map == null) {
            return;
        }

        removeMarkers();

        map.setMapType(MainApplication.mapTypes[MainApplication.mapType]);
        map.setInfoWindowAdapter(new MyInfoWindowAdapter()); // Set the windowInfo view for each marker
        map.setMyLocationEnabled(true);
        map.setIndoorEnabled(true);
        map.setBuildingsEnabled(true);
        map.getUiSettings().setZoomControlsEnabled(false);

        map.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (!marker.isInfoWindowShown()) {
                    marker.showInfoWindow();
                    MainApplication.emailBeingTracked = marker.getTitle();

                }
                return true;
            }
        });

        map.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                MainApplication.emailBeingTracked = null;
            }
        });

        // Set long click to add a place
        map.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {

                setAddPlacesVisible(true);
            }
        });
    }

    private void setupAddPlacesOverlay() {
        // todo these should probably be initialized once...
        Button cancelButton = (Button) getView().findViewById(R.id.cancel_add_place_button);
        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setAddPlacesVisible(false);
            }
        });

        Button addPlaceButton = (Button) getView().findViewById(R.id.add_place_button);
        addPlaceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int mapWidth = fragment.getView().getWidth();
                int mapHeight = fragment.getView().getHeight();

                Location middleSideLocation;
                if (mapWidth > mapHeight) {
                    middleSideLocation = MapUtils.convertToLocation(map.getProjection().fromScreenLocation(new Point(mapWidth / 2, 0)), "middleSide");
                } else {
                    middleSideLocation = MapUtils.convertToLocation(map.getProjection().fromScreenLocation(new Point(0, mapHeight / 2)), "middleSide");
                }

                LatLng centerLatLng = map.getProjection().getVisibleRegion().latLngBounds.getCenter();
                int radius = (int) middleSideLocation.distanceTo(MapUtils.convertToLocation(centerLatLng, "center"));
                Dialogs.addPlace(getActivity(), centerLatLng, (int) (radius * radiusMultiplier));
            }
        });
    }

    public void setAddPlacesVisible(boolean visible) {
        if (d != null) {
            ((ImageView)getView().findViewById(R.id.addPlaceCircle)).setImageDrawable(null);
        }
        if (visible) {
            d = new Drawable() {
                @Override
                public void draw(Canvas canvas) {
                    int mapCenterX = fragment.getView().getWidth() / 2;
                    int mapCenterY = fragment.getView().getHeight() / 2;
                    int radius = mapCenterX > mapCenterY ? mapCenterY : mapCenterX;

                    Paint fill = new Paint();
                    fill.setColor(Color.BLUE);
                    fill.setAlpha(25);
                    fill.setAntiAlias(true);
                    canvas.drawCircle(mapCenterX, mapCenterY, (int) (radius * radiusMultiplier), fill);

                    Paint border = new Paint();
                    border.setColor(Color.BLUE);
                    border.setAntiAlias(true);
                    border.setStyle(Paint.Style.STROKE);
                    canvas.drawCircle(mapCenterX, mapCenterY, (int) (radius * radiusMultiplier), border);
                }

                @Override
                public void setAlpha(int alpha) {

                }

                @Override
                public void setColorFilter(ColorFilter cf) {

                }

                @Override
                public int getOpacity() {
                    return 0;
                }
            };

            ((ImageView)getView().findViewById(R.id.addPlaceCircle)).setImageDrawable(d);
            getView().findViewById(R.id.add_place_overlay).setVisibility(View.VISIBLE);
        } else {
            getView().findViewById(R.id.add_place_overlay).setVisibility(View.INVISIBLE);
        }
    }

    private void removeMarkers() {

        Log.e(TAG, "removeMarkers");
        for (Marker m : markerMap.values()) {
            m.remove();
        }
        markerMap.clear();
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(context).unregisterReceiver(mMessageReceiver);
        LocalBroadcastManager.getInstance(context).unregisterReceiver(placesUpdateReceiver);
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "BroadcastReceiver onReceive");
            Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey("current-location")) {
                //    new UpdateMap().execute(0); // Only user
            } else {
                //    new UpdateMap().execute(1); // Only others (not user)
                new UpdateMap().execute(2); // All users
            }
        }
    };

    private BroadcastReceiver placesUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e(TAG, "placesUpdateReceiver onReceive");
            updatePlaces();
        }
    };

    private void updatePlaces() {

        Log.e(TAG, "updatePlaces");
        if (map == null) {
            return;
        }

        removePlaces();

        try {
            Iterator<String> keys = MainApplication.places.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject placeObj = MainApplication.places.getJSONObject(key);
                Circle circle = map.addCircle(new CircleOptions()
                        .center(new LatLng(placeObj.getDouble("lat"), placeObj.getDouble("lon")))
                        .radius(placeObj.getInt("rad"))
                        .strokeColor(Color.BLUE)
                        .strokeWidth(2)
                        .fillColor(0x330000ff)); // TODO move color out of here
                placesOverlay.add(circle);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void removePlaces() {

        Log.e(TAG, "removePlaces");
        for (Circle circle : placesOverlay) {
            circle.remove();
        }
        placesOverlay.clear();
    }

    class UpdateMap extends AsyncTask<Integer, Void, HashMap<String, Location>> {

        @Override
        protected HashMap<String, Location> doInBackground(Integer... params) {

            if (MainApplication.dashboard == null) {
                return null;
            }

            int who = params[0]; // 0 = user, 1 = others, 3 = all.
            Log.e(TAG, "UpdateMap update for all users: " + who);

            try {
                JSONObject iCanSee = MainApplication.dashboard.getJSONObject("icansee");
                JSONObject idMapping = MainApplication.dashboard.getJSONObject("idmapping");
                HashMap<String, Location> markerData = new HashMap<String, Location>();

                if (who == 0 || who == 2) {  // TODO Find out what is number two
                    markerData.put(MainApplication.userAccount, convertToLocation(MainApplication.dashboard.getJSONObject("location"))); // User himself
                }

                if (who == 1 || who == 2) {
                    Iterator keys = iCanSee.keys();
                    while (keys.hasNext()) {
                        String key = (String) keys.next();
                        JSONObject data = iCanSee.getJSONObject(key);
                        JSONObject location = data.getJSONObject("location");
                        String email = (String) idMapping.get(key);
                        Log.e(TAG, "I can see: " + email + " => " + data);

                        if (MainApplication.iDontWantToSee != null && MainApplication.iDontWantToSee.has(email)) {
                            Log.e(TAG, "I dont want to see: " + email);
                        } else {
                            Location loc = convertToLocation(location);
                            if (loc == null) {
                                Log.e(TAG, "No location could be parsed for: " + email);
                            }
                            markerData.put(email, loc);
                        }
                    }
                }
                return markerData;

            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(HashMap<String, Location> markerDataResult) {

            Log.e(TAG, "cancelAsyncTasks: " + cancelAsyncTasks);
            super.onPostExecute(markerDataResult);
            if (markerDataResult != null && !cancelAsyncTasks && isAdded()) {
                for (String email : markerDataResult.keySet()) {
                    Log.e(TAG, "marker to update: " + email);
                    if (markerDataResult.get(email) != null) {
                        new LoadMarkerAsync(markerDataResult.get(email), email).execute();
                    }
                }
            }
        }
    }

    private Location convertToLocation(JSONObject locationObj) {

        Location myLocation = new Location("fused");
        try {
            if (locationObj.length() == 0) {
                return null;
            }
            double lat = locationObj.getDouble("lat");
            double lon = locationObj.getDouble("lon");
            float acc = (float) locationObj.getDouble("acc");
            Long time = locationObj.getLong("time");
            myLocation.setLatitude(lat);
            myLocation.setLongitude(lon);
            myLocation.setAccuracy(acc);
            myLocation.setTime(time);
            return myLocation;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    class MyInfoWindowAdapter implements GoogleMap.InfoWindowAdapter {

        @Override
        public View getInfoWindow(Marker marker) {
            return null;
        }

        @Override
        public View getInfoContents(Marker marker) {

            if (!aq.isExist() || cancelAsyncTasks || !isAdded()) {
                return null;
            }

            View myContentsView = getActivity().getLayoutInflater().inflate(R.layout.map_info_window, null);
            AQuery aq = new AQuery(myContentsView);

            String name = Utils.getNameFromEmail(context, marker.getTitle());
            aq.id(R.id.contact_name).text(name);
            aq.id(R.id.timestamp).text(Utils.timestampText(marker.getSnippet()));

            return myContentsView;
        }
    }

    public Bitmap getMarkerBitmap(String email, Boolean accurate, Boolean recent) {

        Log.e(TAG, "getMarkerBitmap");

        // Add cache checking logic
        Bitmap markerImage = MainApplication.avatarCache.get(email + ":" + accurate + ":" + recent);
        if (markerImage != null) {
            Log.e(TAG, "Marker IN cache: " + email + ":" + accurate + ":" + recent);
            return markerImage;
        } else {
            Log.e(TAG, "Marker NOT in cache. Processing: " + email + ":" + accurate + ":" + recent);
        }

        Log.e(TAG, "AvatarLoader not in cache. Fetching it. Email: " + email);
        // Get avatars
        Bitmap userImage = Utils.getPhotoFromEmail(context, email);
        if (userImage == null) {
            userImage = BitmapFactory.decodeResource(getResources(), R.drawable.default_avatar);
        } else {
            userImage = Utils.getRoundedCornerBitmap(userImage, 50);
        }

        // Marker colors, etc.
        Log.e(TAG, "userImage size: " + userImage);
        View markerView = ((LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.map_marker, null);

        aq = new AQuery(markerView);
        aq.id(R.id.user_image).image(userImage);
        Log.e(TAG, "aq in place");

        if (email.equals(MainApplication.userAccount)) {
            aq.id(R.id.marker_frame).image(R.drawable.pointers_android_pointer_green);
        } else if (!recent || !accurate) {
            aq.id(R.id.marker_frame).image(R.drawable.pointers_android_pointer_orange);
        }

        Log.e(TAG, "Image set. Calling createDrawableFromView");

        markerImage = createDrawableFromView(markerView);
        MainApplication.avatarCache.put(email + ":" + accurate + ":" + recent, markerImage);
        return markerImage;
    }

    // Convert a view to bitmap
    public Bitmap createDrawableFromView(View view) {

        Log.e(TAG, "createDrawableFromView");
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        view.setLayoutParams(new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT));
        view.measure(displayMetrics.widthPixels, displayMetrics.heightPixels);
        view.layout(0, 0, displayMetrics.widthPixels, displayMetrics.heightPixels);
        view.buildDrawingCache();
        Bitmap bitmap = Bitmap.createBitmap(view.getMeasuredWidth(), view.getMeasuredHeight(), Bitmap.Config.ARGB_8888);

        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);

        return bitmap;
    }


    class LoadMarkerAsync extends AsyncTask<Void, Void, Bitmap> {

        Location position;
        LatLng latLng;
        String email;
        String time;
        Boolean accurate;
        Boolean recent;

        public LoadMarkerAsync(Location position, String email) {

            this.email = email;
            this.position = position;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {

            if (position == null || email == null) {
                return null;
            }
            Log.e(TAG, "LoadMarkerAsync - Email: " + email + ", Position: " + position);
            latLng = new LatLng(position.getLatitude(), position.getLongitude());
            time = String.valueOf(position.getTime());
            accurate = Math.round(position.getAccuracy()) < 100;
            recent = (System.currentTimeMillis() - position.getTime()) < 60 * 60 * 1000;

            try {
                return getMarkerBitmap(email, accurate, recent);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmapResult) {

            super.onPostExecute(bitmapResult);
            if (bitmapResult == null || cancelAsyncTasks || !isAdded() || map == null) {
                return;
            }
            Marker marker = markerMap.get(email);
            Boolean isNew = false;
            if (marker != null) {
                Log.e(TAG, "onPostExecute - updating marker: " + email);
                marker.setPosition(latLng);
                marker.setSnippet(time);
                marker.setIcon(BitmapDescriptorFactory.fromBitmap(bitmapResult));

            } else {
                Log.e(TAG, "onPostExecute - creating marker: " + email);
                marker = map.addMarker(new MarkerOptions().position(latLng).title(email).snippet(time).icon(BitmapDescriptorFactory.fromBitmap(bitmapResult)));
                Log.e(TAG, "onPostExecute - marker created");
                markerMap.put(email, marker);
                Log.e(TAG, "onPostExecute - marker in map stored. markerMap: " + markerMap.size());
                isNew = true;
            }

            if (marker.getTitle().equals(MainApplication.emailBeingTracked)) {
                marker.showInfoWindow();
                Log.e(TAG, "onPostExecute - showInfoWindow open");
                if (isNew) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 16));
                } else {
                    map.moveCamera(CameraUpdateFactory.newLatLng(marker.getPosition()));
                }
            } else if (firstTimeZoom && MainApplication.emailBeingTracked == null && MainApplication.userAccount != null && marker.getTitle().equals(MainApplication.userAccount)) {
                firstTimeZoom = false;
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 16));
            }
        }

    }

    @Override
    public void onDestroy() {

        // TODO: Cancel ALL Async tasks
        cancelAsyncTasks = true;
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.e(TAG, "---------------------------------------------------------");
        Log.e(TAG, "onLowMemory");
        Log.e(TAG, "---------------------------------------------------------");
        startActivityForResult(new Intent(context, FirstTimeActivity.class), -1);
    }
}
