package com.example.indoortracking;

import static android.content.ContentValues.TAG;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.example.indoortracking.common.floorselector.FloorSelectorView;
import com.example.indoortracking.databinding.ActivityMapBinding;
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
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MapStyleOptions;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import es.situm.sdk.SitumSdk;
import es.situm.sdk.directions.DirectionsRequest;
import es.situm.sdk.error.Error;
import es.situm.sdk.location.LocationListener;
import es.situm.sdk.location.LocationManager;
import es.situm.sdk.location.LocationRequest;
import es.situm.sdk.location.LocationStatus;
import es.situm.sdk.location.util.CoordinateConverter;
import es.situm.sdk.model.cartography.Building;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.model.cartography.Floor;
import es.situm.sdk.model.cartography.Poi;
import es.situm.sdk.model.cartography.Point;
import es.situm.sdk.model.directions.Route;
import es.situm.sdk.model.directions.RouteSegment;
import es.situm.sdk.model.location.CartesianCoordinate;
import es.situm.sdk.model.location.Coordinate;
import es.situm.sdk.model.location.Location;
import es.situm.sdk.model.navigation.NavigationProgress;
import es.situm.sdk.navigation.NavigationListener;
import es.situm.sdk.navigation.NavigationRequest;
import es.situm.wayfinding.LibrarySettings;
import es.situm.wayfinding.SitumMapView;
import es.situm.wayfinding.SitumMapsLibrary;

public class mapActivity extends AppCompatActivity implements OnMapReadyCallback {
    private SitumMapsLibrary mapsLibrary = null;
    private LibrarySettings librarySettings;
    private SitumMapView mapsView=null;
    private String buildingId = "13347";
    private String targetFloorId = "42059";
    private String EmrFloorId = "42059";
    private String selectedPoiName;
    // 42059
    private Boolean routesFound=false;
    private LocationManager locationManager;
    private Coordinate targetCoordinate =
            new Coordinate(30.919247, 75.831841);
    private List<Polyline> polylines = new ArrayList<>();

    private CoordinateConverter coordinateConverter;
    private commons commons;
    private boolean isNavigating = false;
    private boolean arrived=false;
    private boolean poiplaced=false;
    private GoogleMap googleMap;
    private LatLng current;
    private LocationListener locationListener;
    private ActivityMapBinding binding;
    private Circle circle;
    // To store the building information after we retrieve it
    private BuildingInfo buildingInfo_;
    private GetPoisUseCase getPoisUseCase;
    private String selectedCoordinateX;
    private String selectedCoordinateY;
    private String selectedPoiId;
    private boolean showAllRoutes= true;
    private GetPoiCategoryIconUseCase getPoiCategoryIconUseCase = new GetPoiCategoryIconUseCase();
    private ProgressBar progressBar;
    private Point pointOrigin;
    private int TTS_CHECK_CODE=111;
    private TextToSpeech tts;
    private Marker markerName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        commons = new commons();
        enableLoc();
        SitumSdk.init(mapActivity.this);
      //  loadMap();
        load_map2();
        setupTTs();


    }
    private void setupTTs() {
        PackageManager pm = getPackageManager();
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        ResolveInfo resolveInfo = pm.resolveActivity( installIntent, PackageManager.MATCH_DEFAULT_ONLY );

        if( resolveInfo == null ) {
            Toast.makeText(this, "Voice commands not supported!", Toast.LENGTH_SHORT).show();
            // Not able to find the activity which should be started for this intent
        } else {
           // startActivityForResult(installIntent, TTS_CHECK_CODE);
        }

        tts= new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status!=TextToSpeech.ERROR){
                    // To Choose language of speech
                    tts.setLanguage(Locale.ENGLISH);
                    tts.setEngineByPackageName("com.google.android.tts");
                }
                else {
                    Toast.makeText(mapActivity.this, String.valueOf(status), Toast.LENGTH_SHORT).show();
                }



            }
        });
        binding.emrText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
           //     speakTTs("hi there!");
            }
        });


    }

    private void speakTTs(String text) {
        if(!tts.isSpeaking()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Log.d(TAG, "Speak new API");

                Bundle bundle = new Bundle();
                bundle.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, "1");
            } else {
                Log.d(TAG, "Speak old API");
                HashMap<String, String> param = new HashMap<>();
                param.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, param);

            }

        }

    }
    private void manageLogic() {
        binding.arCamButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Bundle bundle= new Bundle();
                bundle.putString("buildingId",buildingId);
                bundle.putString("floorId",targetFloorId);
                bundle.putString("poiId", selectedPoiId);
                bundle.putString("poiName", selectedPoiName);
                // these are raw coordinates, not cartiesian.
                bundle.putString("poiCoordinateX",selectedCoordinateX);
                bundle.putString("poiCoordinateY", selectedCoordinateY);
                Intent intent=new Intent(mapActivity.this, ARActivity.class);
                intent.putExtra("POIdata",bundle);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left,R.anim.slide_out_left);

            }
        });

        binding.backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // selects the next POI for exit and then calculates the route.
                clearMap();
                binding.buttonsLayout.setVisibility(View.GONE);
                showAllRoutes=true;
                poiplaced=false;

            }
        });

        binding.recenter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                poiplaced=false;
            }
        });
        googleMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(@NonNull Marker marker) {
                Marker marker1= marker;
                Coordinate coordinate= new Coordinate(
                        marker1.getPosition().latitude, marker1.getPosition()
                        .longitude);
                showProgress();
                binding.buttonsLayout.setVisibility(View.GONE);
               // showAllRoutes=true;

                SitumSdk.communicationManager().fetchIndoorPOIsFromBuilding(buildingId, new es.situm.sdk.utils.Handler<Collection<Poi>>() {
                    @Override
                    public void onSuccess(Collection<Poi> pois) {
                        for(Poi poi:pois) {
                            if(marker1.getTitle().toString().equals(poi.getName())) {

                                selectedCoordinateX=String.valueOf(marker1.getPosition().latitude);
                                selectedCoordinateY=String.valueOf(marker1.getPosition().longitude);
                                selectedPoiId= poi.getIdentifier();
                                selectedPoiName= poi.getName().toString();
                                CartesianCoordinate cartesianCoordinate = coordinateConverter.toCartesianCoordinate(coordinate);
                                //Toast.makeText(mapActivity.this, marker.getTitle(), Toast.LENGTH_SHORT).show();
                                targetCoordinate= coordinate;
                                clearMap();
                                arrived=false;
                                showAllRoutes=false;
                               // Toast.makeText(mapActivity.this, "switched to single", Toast.LENGTH_SHORT).show();
                                binding.buttonsLayout.setVisibility(View.VISIBLE);
                                // Navigate the user now, use navigation start and listener.
                                //calculateSingleRoute(current,coordinate,cartesianCoordinate);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Error error) {

                    }
                });

              return false;
            }
        });

    }

    private void load_map2() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        locationManager=SitumSdk.locationManager();
    }

    private void loadMap() {
        locationManager=SitumSdk.locationManager();
        librarySettings = new LibrarySettings();
        librarySettings.setMinZoom(25);
        librarySettings.setHasSearchView(false);
        librarySettings.setApiKey(commons.getAPI_EMAIL(), commons.getAPI_KEY());
        librarySettings.setUserPositionIcon("situm_position_icon.png");
        librarySettings.setUserPositionArrowIcon("itum_position_icon_arrow.png");
        mapsLibrary = new SitumMapsLibrary(R.id.maps_library_target, this, librarySettings);
        mapsLibrary.load();



        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mapsLibrary.enableOneBuildingMode(buildingId);
                mapsLibrary.startPositioning(buildingId);
                startLocation();
                librarySettings.setShowNavigationIndications(true);
            }
        },500);

    }


    private void startNavigation(Location location) {
            // Removes previous navigation (just in case)
            SitumSdk.navigationManager().removeUpdates();

            // First, we build the coordinate converter ...
            CoordinateConverter coordinateConverter = new CoordinateConverter(buildingInfo_.getBuilding().getDimensions(),
                    buildingInfo_.getBuilding().getCenter(), buildingInfo_.getBuilding().getRotation());
            // ... which allows us to build the destination point of the route
            Point pointB = new Point(buildingId, targetFloorId, targetCoordinate, coordinateConverter.toCartesianCoordinate(targetCoordinate));
            // ... while the origin point is just our current location
            Coordinate coordinateA = new Coordinate(location.getCoordinate().getLatitude(), location.getCoordinate().getLongitude());
            Point pointA = new Point(location.getBuildingIdentifier(), location.getFloorIdentifier(), coordinateA, coordinateConverter.toCartesianCoordinate(coordinateA));
            // The DirectionsRequest allows us to configure the route calculation: source point, destination point, other options...
            DirectionsRequest directionsRequest = new DirectionsRequest.Builder().from(pointA, null).to(pointB).build();
            // Then, we compute the route
            SitumSdk.directionsManager().requestDirections(directionsRequest, new es.situm.sdk.utils.Handler<Route>() {
                @Override
                public void onSuccess(Route route) {
                    // When the route is computed, we configure the NavigationRequest
                    NavigationRequest navigationRequest = new NavigationRequest.Builder().
                            // Navigation will take place along this route
                                    route(route).
                            distanceToChangeIndicationThreshold(2).
                            // ... stopping when we're closer than 4 meters to the destination
                                    distanceToGoalThreshold(4).
                            // ... or we're farther away than 10 meters from the route
                                    outsideRouteThreshold(5).
                            // Low quality locations will not be taken into account when updating the navigation state
                                    ignoreLowQualityLocations(true).
                            // ... neither locations computed at unexpected floors if the user
                            // is less than 1000 consecutive milliseconds in those floors
                                    timeToIgnoreUnexpectedFloorChanges(1000).
                            build();
                    // We start the navigation with this NavigationRequest,
                    // which allows us to receive updates while the user moves along the route
                    isNavigating = true;
                    SitumSdk.navigationManager().requestNavigationUpdates(navigationRequest, navigationListener);

                }

                @Override
                public void onFailure(Error error) {
                    Toast.makeText(mapActivity.this, "You are not inside building", Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Navigation Failed" + error);
                }
            });
        }

    private NavigationListener navigationListener = new NavigationListener() {
        @Override
        public void onDestinationReached() {
            Log.i(TAG, "Destination Reached");
            isNavigating = false;
            hideProgress();
            if(!arrived) {
                AlertDialog.Builder builder = new AlertDialog.Builder(mapActivity.this);
                builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        showAllRoutes=true;


                    }
                });
                builder.setTitle("Destination Reached!");
                builder.setMessage("You've arrived at your destination, press OK to exit.").show();

                SitumSdk.navigationManager().removeUpdates();
                //locationManager.removeUpdates(locationListener);
                //clearMap();

                poiplaced=false;
                arrived=true;
                removePolylines();
            }
        }

        @Override
        public void onProgress(NavigationProgress navigationProgress) {
            if(!arrived) {
               // Toast.makeText(mapActivity.this,"current floor:"+
                 //       navigationProgress.getClosestLocationInRoute().getFloorIdentifier(), Toast.LENGTH_SHORT).show();
                Log.i(TAG, "Route advances");
                hideProgress();
                Toast.makeText(mapActivity.this, navigationProgress.getCurrentIndication().toText(mapActivity.this), Toast.LENGTH_SHORT).show();
                //   FloorSelectorView floorSelectorView = findViewById(R.id.situm_floor_selector);
                //  floorSelectorView.setFloorSelector(buildingInfo_.getBuilding(), googleMap, targetFloorId);
                //  getPoisUseCase= new GetPoisUseCase(buildingInfo_.getBuilding());
                //  getPois(googleMap);
                Log.d("navigation", navigationProgress.toString());
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        //speakTTs(navigationProgress.getCurrentIndication().toText(mapActivity.this));
                    }
                },6000);

                drawChosenRoute(navigationProgress.getSegments());
            }
//            Log.i(TAG, "Current indication " + navigationProgress.getCurrentIndication().toText(ARActivity.this));
//            Log.i(TAG, "Next indication " + navigationProgress.getNextIndication().toText(ARActivity.this));
//            Log.i(TAG, "");
//            Log.i(TAG, " Distance to goal: " + navigationProgress.getDistanceToGoal());
//            Log.i(TAG, " Time to goal: " + navigationProgress.getTimeToGoal());
//            Log.i(TAG, " Closest location in route: " + navigationProgress.getClosestLocationInRoute());
//            Log.i(TAG, " Distance to closest location in route: " + navigationProgress.getDistanceToClosestPointInRoute());
//            Log.i(TAG, " Remaining segments: ");
//            for (RouteSegment segment : navigationProgress.getSegments()) {
//                Log.i(TAG, "   Floor Id: " + segment.getFloorIdentifier());
//                for (Point point : segment.getPoints()) {
//                    Log.i(TAG, "    Point: BuildingId " + point.getFloorIdentifier() + " FloorId " + point.getFloorIdentifier() + " Latitude " + point.getCoordinate().getLatitude() + " Longitude " + point.getCoordinate().getLongitude());
//                }
//                Log.i(TAG, "    ----");
//            }
//            Log.i(TAG, "--------");

        }

        @Override
        public void onUserOutsideRoute() {
            Toast.makeText(mapActivity.this, "You are going outside the route", Toast.LENGTH_SHORT).show();
            isNavigating = false;
            SitumSdk.navigationManager().removeUpdates();
        }
    };

    private void startLocation() {

        if (locationManager.isRunning()) {
            return;
        }
        locationListener = new LocationListener(){

            @Override
            public void onLocationChanged(@NonNull Location location) {
                //place marker on map when location loaded!
                LatLng latLng = new LatLng(location.getCoordinate().getLatitude(),
                        location.getCoordinate().getLongitude());
                current=latLng;

                //current floor at which user is
              //  Toast.makeText(mapActivity.this, "Hello", Toast.LENGTH_SHORT).show();

                if(!location.getFloorIdentifier().equals("-1")) {
                    targetFloorId=location.getFloorIdentifier();

                    if (!poiplaced) {
                        SitumSdk.communicationManager().fetchBuildingInfo(buildingId, new es.situm.sdk.utils.Handler<BuildingInfo>() {
                            @Override
                            public void onSuccess(BuildingInfo buildingInfo) {
                                for (Floor floor : buildingInfo.getFloors()) {

                                    if (floor.getIdentifier().equals(targetFloorId)) {
                                        binding.buildingInfo.setText(buildingInfo.getBuilding().getName() + ", floor: " + floor.getName().toString());
                                        binding.emrText.setText("There is an emergency in " + buildingInfo.getBuilding().getName() + " at floor " +
                                                floor.getName() + ". Please choose the closest route to exit.");
                                        coordinateConverter = new CoordinateConverter(buildingInfo.getBuilding().getDimensions(),
                                                buildingInfo.getBuilding().getCenter(), buildingInfo.getBuilding().getRotation());
                                        buildingInfo_ = buildingInfo;
                                        FloorSelectorView floorSelectorView = findViewById(R.id.situm_floor_selector);
                                        floorSelectorView.setFloorSelector(buildingInfo.getBuilding(), googleMap, targetFloorId);
                                        getPoisUseCase = new GetPoisUseCase(buildingInfo.getBuilding());
                                        getPois(googleMap);
                                        poiplaced = true;
                                        hideProgress();

                                    }

                                }

                            }

                            @Override
                            public void onFailure(Error error) {

                            }
                        });
                    }

//                    if (markerName != null) {
//                        markerName.setPosition(latLng);
//                        markerName.setRotation((float) location.getBearing().degrees());
//                    }
//                    else {
//                        Bitmap icon = BitmapFactory.decodeResource(getResources(),
//                                R.drawable.arrow_user);
//                        LatLngBounds.Builder builder = new LatLngBounds.Builder();
//
//                        MarkerOptions markerOptions = new MarkerOptions()
//                                .position(latLng)
//                                .title("You");
//
//                        markerOptions.icon(BitmapDescriptorFactory.fromBitmap(icon));
//                        markerOptions.draggable(false);
//                        markerName = googleMap.addMarker(new MarkerOptions().position(latLng).title("Title")
//                                .icon(BitmapDescriptorFactory.fromBitmap(icon))
//                                .draggable(false));
//                    }
//                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 25));
                    hideProgress();

                    SitumSdk.communicationManager().fetchIndoorPOIsFromBuilding(buildingId, new es.situm.sdk.utils.Handler<Collection<Poi>>() {
                        @Override
                        public void onSuccess(Collection<Poi> pois) {
                            for (Poi poi : pois) {
                                if (poi.getFloorIdentifier().equals(targetFloorId)) {
                                    if (latLng != null) {
                                        if (showAllRoutes) {
                                          //  removePolylines();
                                            calculateRoute(markerName.getPosition(), poi.getCoordinate(), poi.getCartesianCoordinate());
                                        } else {
                                            if (isNavigating) {
                                                SitumSdk.navigationManager().updateWithLocation(location);

                                            } else {
                                                startNavigation(location);
                                            }
                                        }
                                    }
                                }
                            }

                        }

                        @Override
                        public void onFailure(Error error) {

                        }
                    });

                }
                else {
                    showProgress();
                }

                if (markerName != null) {
                    markerName.setPosition(latLng);
                    markerName.setRotation((float) location.getBearing().degrees());
                }
                else {
                    Bitmap icon = BitmapFactory.decodeResource(getResources(),
                            R.drawable.arrow_user);
                    LatLngBounds.Builder builder = new LatLngBounds.Builder();

                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(latLng)
                            .title("You");

                    markerOptions.icon(BitmapDescriptorFactory.fromBitmap(icon));
                    markerOptions.draggable(false);
                    markerName = googleMap.addMarker(new MarkerOptions().position(latLng).title("Title")
                            .icon(BitmapDescriptorFactory.fromBitmap(icon))
                            .draggable(false));
                }
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 25));
            }


            @Override
            public void onStatusChanged(@NonNull LocationStatus locationStatus) {
            }

            @Override
            public void onError(@NonNull Error error) {
            }
        };
        LocationRequest locationRequest = new LocationRequest.Builder()
                .useWifi(true)
                .useBle(true)
                .useDeadReckoning(true)
                .useForegroundService(true)
                .build();
        locationManager.requestLocationUpdates(locationRequest, locationListener);

    }
    private void stopLocation() {
        if (!locationManager.isRunning()) {
            return;
        }
        locationManager.removeUpdates(locationListener);
    }


    private void getPois(final GoogleMap googleMap){
        getPoisUseCase.get(new GetPoisUseCase.Callback() {
            @Override
            public void onSuccess(Building building, Collection<Poi> pois) {
                if (pois.isEmpty()){
                    Toast.makeText(mapActivity.this, "There isnt any poi in the building: " + building.getName() + ". Go to the situm dashboard and create at least one poi before execute again this example", Toast.LENGTH_LONG).show();
                }else {
                    for (final Poi poi : pois) {
                        getPoiCategoryIconUseCase.getUnselectedIcon(poi, new GetPoiCategoryIconUseCase.Callback() {
                            @Override
                            public void onSuccess(Bitmap bitmap) {
                                drawPoi(poi, bitmap);
                            }

                            @Override
                            public void onError(String error) {
                                Log.d("Error fetching poi icon", error);
                                drawPoi(poi);
                            }
                        });
                    }

                }
            }

            private void drawPoi(Poi poi, Bitmap bitmap) {
                LatLngBounds.Builder builder = new LatLngBounds.Builder();
                LatLng latLng = new LatLng(poi.getCoordinate().getLatitude(),
                        poi.getCoordinate().getLongitude());
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(latLng)
                        .title(poi.getName());
                if (bitmap != null) {
                    markerOptions.icon(BitmapDescriptorFactory.fromBitmap(bitmap));
                }
                googleMap.addMarker(markerOptions);
                builder.include(latLng);
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
            }

            private void drawPoi(Poi poi) {
                drawPoi(poi, null);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(mapActivity.this, error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void hideProgress() {
                binding.progressBar2.setVisibility(View.GONE);
                binding.mapsLibraryTarget.setVisibility(View.VISIBLE);
                binding.emrLayout.setVisibility(View.VISIBLE);
    }

    private void showProgress() {
        binding.progressBar2.setVisibility(View.VISIBLE);
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

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        manageLogic();
        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(false);
        googleMap.setBuildingsEnabled(false);
        googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                        this, R.raw.google_map_style));
        startLocation();


    }

    private void calculateRoute(LatLng current, Coordinate destination, CartesianCoordinate _cartesianDestination) {
        pointOrigin = createPoint(current);
        Point pointDestination =  new Point(buildingId,targetFloorId,destination,_cartesianDestination);
        DirectionsRequest directionsRequest = new DirectionsRequest.Builder()
                .from(pointOrigin, null)
                .to(pointDestination)
                .build();
        SitumSdk.directionsManager().requestDirections(directionsRequest, new es.situm.sdk.utils.Handler<Route>() {
            @Override
            public void onSuccess(Route route) {
                removePolylines();
                drawRoute(route);
                centerCamera(route);
                hideProgress();
                pointOrigin = null;
            }

            @Override
            public void onFailure(Error error) {
                hideProgress();
                clearMap();
                pointOrigin = null;
                Toast.makeText(mapActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void calculateSingleRoute(LatLng current, Coordinate destination, CartesianCoordinate _cartesianDestination) {
        pointOrigin = createPoint(current);
        Point pointDestination =  new Point(buildingId,targetFloorId,destination,_cartesianDestination);
        DirectionsRequest directionsRequest = new DirectionsRequest.Builder()
                .from(pointOrigin, null)
                .to(pointDestination)
                .build();
        SitumSdk.directionsManager().requestDirections(directionsRequest, new es.situm.sdk.utils.Handler<Route>() {
            @Override
            public void onSuccess(Route route) {
               // drawChosenRoute(route);
                centerCamera(route);
                hideProgress();
                pointOrigin = null;
            }

            @Override
            public void onFailure(Error error) {
                hideProgress();
                clearMap();
                pointOrigin = null;
                Toast.makeText(mapActivity.this, error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void clearMap(){
        removePolylines();
    }

    private Point createPoint(LatLng latLng) {
        Coordinate coordinate = new Coordinate(latLng.latitude, latLng.longitude);
        CartesianCoordinate cartesianCoordinate= coordinateConverter.toCartesianCoordinate(coordinate);
        Point point = new Point(buildingId, targetFloorId,coordinate,cartesianCoordinate );
        return point;
    }

    private void removePolylines() {
        for (Polyline polyline : polylines) {
            polyline.remove();
        }
        polylines.clear();
    }

    private void drawChosenRoute(List<RouteSegment> route) {
        for (RouteSegment segment : route) {
            //For each segment you must draw a polyline
            //Add an if to filter and draw only the current selected floor
            List<LatLng> latLngs = new ArrayList<>();
            for (Point point : segment.getPoints()) {
                latLngs.add(new LatLng(point.getCoordinate().getLatitude(), point.getCoordinate().getLongitude()));
            }

            PolylineOptions polyLineOptions = new PolylineOptions()
                    .color(getResources().getColor(es.situm.components.R.color.situm_red))
                    .width(12f)
                    .clickable(true)
                    .addAll(latLngs);
            polylines.add(googleMap.addPolyline(polyLineOptions));
        }
    }
    private void drawRoute(Route route) {
        for (RouteSegment segment : route.getSegments()) {
            //For each segment you must draw a polyline
            //Add an if to filter and draw only the current selected floor
            List<LatLng> latLngs = new ArrayList<>();
            for (Point point : segment.getPoints()) {
                latLngs.add(new LatLng(point.getCoordinate().getLatitude(), point.getCoordinate().getLongitude()));
            }

            PolylineOptions polyLineOptions = new PolylineOptions()
                    .color(getResources().getColor(es.situm.components.R.color.situm_dark_gray))
                    .width(12f)
                    .addAll(latLngs);
            polylines.add(googleMap.addPolyline(polyLineOptions));
        }
    }

    private void centerCamera(Route route) {
        Coordinate from = route.getFrom().getCoordinate();
        Coordinate to = route.getTo().getCoordinate();

        LatLngBounds.Builder builder = new LatLngBounds.Builder()
                .include(new LatLng(from.getLatitude(), from.getLongitude()))
                .include(new LatLng(to.getLatitude(), to.getLongitude()));
        googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 100));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(locationManager!=null) {
            startLocation();
        }

    }

    @Override
    public void finish() {
        getPoisUseCase.cancel();
        stopLocation();
        locationManager.removeAllLocationListeners();
        SitumSdk.navigationManager().removeUpdates();
        super.finish();
    }
}

