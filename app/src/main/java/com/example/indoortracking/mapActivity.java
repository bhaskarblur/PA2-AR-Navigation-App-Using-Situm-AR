package com.example.indoortracking;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationRequest;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import com.example.indoortracking.databinding.ActivityAractivityBinding;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.Collection;
import java.util.List;

import es.situm.maps.library.domain.interceptor.Interceptor;
import es.situm.sdk.SitumSdk;
import es.situm.sdk.directions.DirectionsRequest;
import es.situm.sdk.error.Error;
import es.situm.sdk.model.cartography.Building;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.model.cartography.Floor;
import es.situm.sdk.model.cartography.Poi;
import es.situm.sdk.model.location.Coordinate;
import es.situm.wayfinding.LibrarySettings;
import es.situm.wayfinding.OnLoadBuildingsListener;
import es.situm.wayfinding.OnPoiSelectionListener;
import es.situm.wayfinding.SitumMap;
import es.situm.wayfinding.SitumMapView;
import es.situm.wayfinding.SitumMapsLibrary;
import es.situm.wayfinding.navigation.Navigation;
import es.situm.wayfinding.navigation.NavigationError;
import es.situm.wayfinding.navigation.OnNavigationListener;

public class mapActivity extends AppCompatActivity {
    private SitumMapsLibrary mapsLibrary = null;
    private LibrarySettings librarySettings;
    private SitumMapView mapsView=null;
    private String buildingId = "13347";
    private String targetFloorId = "42059";
    private Boolean routesFound=false;
    private Coordinate targetCoordinate =
            new Coordinate(30.919247, 75.831841);
    private commons commons;
    private boolean isNavigating = false;
    private boolean arrived=false;

    private ActivityAractivityBinding binding;
    // To store the building information after we retrieve it
    private BuildingInfo buildingInfo_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        commons = new commons();
        enableLoc();
        loadMap();
        manageLogic();

    }

    private void loadMap() {
        SitumSdk.init(mapActivity.this);
        librarySettings = new LibrarySettings();

        librarySettings.setHasSearchView(false);
        librarySettings.setApiKey(commons.getAPI_EMAIL(), commons.getAPI_KEY());
        librarySettings.setUserPositionIcon("situm_position_icon.png");
        librarySettings.setUserPositionArrowIcon("itum_position_icon_arrow.png");
        mapsLibrary = new SitumMapsLibrary(R.id.maps_library_target, this, librarySettings);
        mapsLibrary.load();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                librarySettings.setMinZoom(25);
                mapsLibrary.setOnLoadBuildingsListener(new OnLoadBuildingsListener() {
                    @Override
                    public void onBuildingLoaded(Building building) {

                    }

                    @Override
                    public void onBuildingsLoaded(List<Building> list) {

                    }

                    @Override
                    public void onFloorImageLoaded(Building building, Floor floor) {
                        mapsLibrary.enableOneBuildingMode(buildingId);
                        mapsLibrary.startPositioning(building.getIdentifier());
                        librarySettings.setShowNavigationIndications(true);
                        SitumSdk.communicationManager()
                                .fetchIndoorPOIsFromBuilding(building.getIdentifier(), new es.situm.sdk.utils.Handler<Collection<Poi>>() {
                                    @Override
                                    public void onSuccess(Collection<Poi> pois) {
                                        if(!routesFound) {
                                            for (Poi poi : pois) {
                                                  mapsLibrary.findRouteToPoi(poi);
                                            }
                                            routesFound = true;
                                        }
                                    }

                                    @Override
                                    public void onFailure(Error error) {
                                        Toast.makeText(mapActivity.this,
                                                "Error fetching building data.", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                });
            }
        },500);

    }

    private void showPOIRoutes() {
        mapsLibrary.setOnPoiSelectionListener(new OnPoiSelectionListener() {
            @Override
            public void onPoiSelected(Poi poi, Floor floor, Building building) {
                Toast.makeText(mapActivity.this, "POI selected: "+poi.getName(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onPoiDeselected(Building building) {
                routesFound=false;
            }
        });
    }


    private void manageLogic() {

    }

    private void enableLoc() {

        com.google.android.gms.location.LocationRequest locationRequest = com.google.android.gms.location.LocationRequest.create();
        locationRequest.setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);
        locationRequest.setInterval(30 * 1000);
        locationRequest.setFastestInterval(5 * 1000);


        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        builder.setAlwaysShow(true);

        Task<LocationSettingsResponse> result =
                LocationServices.getSettingsClient(this).checkLocationSettings(builder.build());

        result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {


            @Override
            public void onComplete(Task<LocationSettingsResponse> task) {
                try {
                    LocationSettingsResponse response = task.getResult(ApiException.class);
                    // All location settings are satisfied. The client can initialize location
                    // requests here.

                } catch (ApiException exception) {
                    switch (exception.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            // Location settings are not satisfied. But could be fixed by showing the
                            // user a dialog.
                            try {
                                // Cast to a resolvable exception.
                                ResolvableApiException resolvable = (ResolvableApiException) exception;
                                // Show the dialog by calling startResolutionForResult(),
                                // and check the result in onActivityResult().
                                resolvable.startResolutionForResult(
                                        mapActivity.this,
                                        101);
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        startActivity(new Intent(mapActivity.this, ARActivity.class));
                                        finish();
                                        overridePendingTransition(R.anim.slide_in_left,R.anim.slide_out_left);
                                    }
                                },1200);
                            } catch (IntentSender.SendIntentException e) {
                                // Ignore the error.
                            } catch (ClassCastException e) {
                                // Ignore, should be an impossible error.
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            // Location settings are not satisfied. However, we have no way to fix the
                            // settings so we won't show the dialog.
                            break;
                    }
                }
            }
        });

    }

    @Override
    public void onBackPressed() {
        if (mapsLibrary != null) {
            if (mapsLibrary.onBackPressed()) {
                return;
            }
        }
        super.onBackPressed();
    }
}

