package com.indooratlas.android.sdk.examples.mapsoverlay;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Looper;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.view.View;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.GroundOverlay;
import com.google.android.gms.maps.model.GroundOverlayOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.indooratlas.android.sdk.IALocation;
import com.indooratlas.android.sdk.IALocationListener;
import com.indooratlas.android.sdk.IALocationManager;
import com.indooratlas.android.sdk.IALocationRequest;
import com.indooratlas.android.sdk.IARegion;
import com.indooratlas.android.sdk.examples.R;
import com.indooratlas.android.sdk.examples.SdkExample;
import com.indooratlas.android.sdk.resources.IAFloorPlan;
import com.indooratlas.android.sdk.resources.IALatLng;
import com.indooratlas.android.sdk.resources.IALocationListenerSupport;
import com.indooratlas.android.sdk.resources.IAResourceManager;
import com.indooratlas.android.sdk.resources.IAResult;
import com.indooratlas.android.sdk.resources.IAResultCallback;
import com.indooratlas.android.sdk.resources.IATask;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.RequestCreator;
import com.squareup.picasso.Target;

@SdkExample(description = R.string.example_googlemaps_overlay_description)
public class MapsOverlayActivity extends FragmentActivity {

    /* used to decide when bitmap should be downscaled */
    private static final int MAX_DIMENSION = 2048;

    private GoogleMap map; // Might be null if Google Play services APK is not available.
    private Marker currentMarker;
    private Marker marker1;
    private Marker marker2;
    private Marker marker3;
    private IARegion floorPlanOverlay = null;
    private GroundOverlay groundOverlay = null;
    private IALocationManager IALocationManager;
    private IAResourceManager IAResourceManager;
    private IATask<IAFloorPlan> IAFetchFloorPlanTask;
    private Target loadTarget;
    private boolean cameraPositionNeedsUpdating = true; // update on first location

    /**
     * Listener that handles location change events.
     */
    private IALocationListener listener = new IALocationListenerSupport() {

        /**
         * Location changed, move currentMarker and camera position.
         */
        @Override
        public void onLocationChanged(IALocation location) {

            if (map == null) {
                // location received before map is initialized, ignoring update here
                return;
            }

            LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
            LatLng latLng1 = new LatLng(29.533844, -98.574450);
            LatLng latLng2 = new LatLng(29.533895, -98.574490);
            LatLng latLng3 = new LatLng(29.533923, -98.574518);

            if (currentMarker == null) {
                // first location, add currentMarker
                currentMarker = map.addMarker(new MarkerOptions().position(currentLatLng)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                marker1 = map.addMarker(new MarkerOptions().position(latLng1)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                marker2 = map.addMarker(new MarkerOptions().position(latLng2)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
                marker3 = map.addMarker(new MarkerOptions().position(latLng3)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));
            } else {
                // move existing currentMarkers position to received location
                currentMarker.setPosition(currentLatLng);
                marker1.setPosition(latLng1);
            }



            // our camera position needs updating if location has significantly changed
            if (cameraPositionNeedsUpdating) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 17.5f));
                cameraPositionNeedsUpdating = false;
            }
        }
    };

    /**
     * Listener that changes overlay if needed
     */
    private IARegion.Listener regionListener = new IARegion.Listener() {

        @Override
        public void onEnterRegion(IARegion region) {
            if (region.getType() == 2) {
                final String newId = region.getId();
                //final String newId  = "44cc622c-3dd7-42b0-8817-676651662a84";

                // Are we entering a new floor plan or coming back the floor plan we just left?
                if (groundOverlay == null || !region.equals(floorPlanOverlay)) {
                    cameraPositionNeedsUpdating = true; // entering new fp, need to move camera
                    if (groundOverlay != null) {
                        groundOverlay.remove();
                        groundOverlay = null;
                    }
                    floorPlanOverlay = region; // overlay will be this (unless error in loading)
                    fetchFloorPlan("3d0c12fd-3511-4f14-a31a-789dacee3f7d");
                } else {
                    groundOverlay.setTransparency(0.0f);
                }
            }
            showInfo("Enter " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

        @Override
        public void onExitRegion(IARegion region) {
            if (groundOverlay != null) {
                // Indicate we left this floor plan but leave it there for reference
                // If we enter another floor plan, this one will be removed and another one loaded
                groundOverlay.setTransparency(0.5f);
            }
            showInfo("Enter " + (region.getType() == IARegion.TYPE_VENUE
                    ? "VENUE "
                    : "FLOOR_PLAN ") + region.getId());
        }

    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        // prevent the screen going to sleep while app is on foreground
        findViewById(android.R.id.content).setKeepScreenOn(true);

        // instantiate IALocationManager and IAResourceManager
        IALocationManager = IALocationManager.create(this);
        IAResourceManager = IAResourceManager.create(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        IALocationManager.destroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (map == null) {
            // Try to obtain the map from the SupportMapFragment.
            map = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            map.setMyLocationEnabled(true);
        }

        // start receiving location updates & monitor region changes
        IALocationManager.requestLocationUpdates(IALocationRequest.create(), listener);
        IALocationManager.registerRegionListener(regionListener);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // unregister location & region changes
        IALocationManager.removeLocationUpdates(listener);
        IALocationManager.registerRegionListener(regionListener);
    }


    /**
     * Sets bitmap of floor plan as ground overlay on Google Maps
     */
    private void setupGroundOverlay(IAFloorPlan floorPlan, Bitmap bitmap) {

        if (groundOverlay != null) {
            groundOverlay.remove();
        }

        if (map != null) {
            BitmapDescriptor bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(bitmap);
            IALatLng iaLatLng = floorPlan.getCenter();
            LatLng center = new LatLng(iaLatLng.latitude, iaLatLng.longitude);
            GroundOverlayOptions fpOverlay = new GroundOverlayOptions()
                    .image(bitmapDescriptor)
                    .position(center, floorPlan.getWidthMeters(), floorPlan.getHeightMeters())
                    .bearing(floorPlan.getBearing());

            groundOverlay = map.addGroundOverlay(fpOverlay);
        }
    }

    /**
     * Fetches floor plan data from IndoorAtlas server.
     */
    private void fetchFloorPlan(String id) {

        // if there is already running task, cancel it
        cancelPendingNetworkCalls();

        final IATask<IAFloorPlan> task = IAResourceManager.fetchFloorPlanWithId("3d0c12fd-3511-4f14-a31a-789dacee3f7d");

        task.setCallback(new IAResultCallback<IAFloorPlan>() {

            @Override
            public void onResult(IAResult<IAFloorPlan> result) {

                if (result.isSuccess() && result.getResult() != null) {
                    // retrieve bitmap for this floor plan metadata
                    fetchFloorPlanBitmap(result.getResult());
                } else {
                    // ignore errors if this task was already canceled
                    if (!task.isCancelled()) {
                        // do something with error
                        showInfo("Loading floor plan failed: " + result.getError());
                        floorPlanOverlay = null;
                    }
                }
            }
        }, Looper.getMainLooper()); // deliver callbacks using main looper

        // keep reference to task so that it can be canceled if needed
        IAFetchFloorPlanTask = task;

    }

    /**
     * Download floor plan using Picasso library.
     */
    private void fetchFloorPlanBitmap(final IAFloorPlan floorPlan) {

        final String url = floorPlan.getUrl();

        if (loadTarget == null) {
            loadTarget = new Target() {

                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                    setupGroundOverlay(floorPlan, bitmap);
                }

                @Override
                public void onPrepareLoad(Drawable placeHolderDrawable) {
                    // N/A
                }

                @Override
                public void onBitmapFailed(Drawable placeHolderDraweble) {
                    showInfo("Failed to load bitmap");
                    floorPlanOverlay = null;
                }
            };
        }

        RequestCreator request = Picasso.with(this).load(url);

        final int bitmapWidth = floorPlan.getBitmapWidth();
        final int bitmapHeight = floorPlan.getBitmapHeight();

        if (bitmapHeight > MAX_DIMENSION) {
            request.resize(0, MAX_DIMENSION);
        } else if (bitmapWidth > MAX_DIMENSION) {
            request.resize(MAX_DIMENSION, 0);
        }

        request.into(loadTarget);
    }

    /**
     * Helper method to cancel current task if any.
     */
    private void cancelPendingNetworkCalls() {
        if (IAFetchFloorPlanTask != null && !IAFetchFloorPlanTask.isCancelled()) {
            IAFetchFloorPlanTask.cancel();
        }
    }

    private void showInfo(String text) {
        final Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), text,
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction(R.string.button_close, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                snackbar.dismiss();
            }
        });
        snackbar.show();
    }
}
