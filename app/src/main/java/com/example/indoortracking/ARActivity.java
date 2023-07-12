package com.example.indoortracking;

import static android.content.ContentValues.TAG;


import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import es.situm.sdk.directions.DirectionsRequest;
import es.situm.sdk.error.Error;
import es.situm.sdk.location.util.CoordinateConverter;
import es.situm.sdk.model.cartography.Floor;
import es.situm.sdk.model.cartography.Poi;
import es.situm.sdk.model.cartography.Point;
import es.situm.sdk.model.directions.Route;
import es.situm.sdk.model.directions.RouteSegment;
import es.situm.sdk.model.location.CartesianCoordinate;
import es.situm.sdk.model.location.Coordinate;
import es.situm.sdk.model.location.Location;
import es.situm.sdk.location.LocationListener;
import es.situm.sdk.location.LocationRequest;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.example.indoortracking.databinding.ActivityAractivityBinding;
import com.google.android.gms.maps.model.LatLng;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Config;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;

import com.google.ar.core.exceptions.UnavailableApkTooOldException;
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException;
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.core.exceptions.UnavailableSdkTooOldException;
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Camera;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.Sun;
import com.google.ar.sceneform.assets.RenderableSource;
import com.google.ar.sceneform.collision.Ray;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import es.situm.sdk.SitumSdk;
import es.situm.sdk.location.LocationStatus;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.model.navigation.NavigationProgress;
import es.situm.sdk.navigation.NavigationListener;
import es.situm.sdk.navigation.NavigationRequest;
import es.situm.sdk.utils.Handler;

public class ARActivity extends AppCompatActivity implements SensorEventListener {
    // to be changed with intent data
    private String buildingId = "13278"; //13347 - 9640
    private String targetFloorId = "42059"; //136775 - 42059
    private String arrow_uri = "https://raw.githubusercontent.com/2wizstudio/indoorNav/main/arrow.gltf";
    private String poi_uri = "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/master/1.0/SmilingFace/glTF/SmilingFace.gltf";

    private Coordinate targetCoordinate =
            new Coordinate(30.919247, 75.831841);
    private boolean isNavigating = false;
    private boolean arrived = false;
    private ActivityAractivityBinding binding;
    private BuildingInfo buildingInfo_;
    private String POIid;
    private Session mSession;
    private boolean mUserRequestedInstall = true;
    private boolean locationFound = false;

    private boolean routeFound = false;
    private boolean isModelPlaced = false;
    private ArFragment arFragment;
    private int TTS_CHECK_CODE = 111;
    private TextToSpeech tts;
    AnchorNode startNode;
    AnchorNode endNode;
    private AnchorNode lastAnchorNode = new AnchorNode();

    private boolean reachedPOI_ = false;
    private LatLng currentLatLng;
    private List<LatLng> routeNav = new ArrayList<>();
    private boolean routeIterated = false;
    private SharedPreferences sharedPreferences;
    boolean voice;
    private boolean NotoutsideRoute = true;
    private boolean routeDrawn = false;
    private double bearingFirst;

    private SensorManager sensorManager;
    private Sensor sensor;

    CoordinateConverter coordinateConverter;
    float Rot[]=null; //for gravity rotational data
    //don't use R because android uses that for other stuff
    float I[]=null; //for magnetic rotational data
    float accels[]=new float[3];
    float mags[]=new float[3];
    float[] values = new float[3];

    float azimuth;
    float pitch;
    float roll;
    private Boolean isUpdated=false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAractivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SitumSdk.init(this);
        SitumSdk.configuration().setCacheMaxAge(30, TimeUnit.SECONDS);
        sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE);
        voice = sharedPreferences.getBoolean("voice", false);
        fillbuildingData();
        setupTTs();
        maybeEnableArButton();
        manageUI();

        SitumSdk.communicationManager().fetchIndoorPOIsFromBuilding(buildingId, new Handler<Collection<Poi>>() {
            @Override
            public void onSuccess(Collection<Poi> pois) {
                for(Poi poi: pois) {
                    Log.d("Pois:", ":"+poi.getName().toString()+","
                    +poi.getIdentifier().toString());
                }
            }

            @Override
            public void onFailure(Error error) {

            }
        });
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);

        SitumSdk.communicationManager().fetchBuildingInfo(buildingId, new Handler<BuildingInfo>() {
            @Override
            public void onSuccess(BuildingInfo buildingInfo) {
                buildingInfo_ = buildingInfo;
                Collection<Floor> floors = buildingInfo.getFloors();
                Collection<Poi> indoorPOIs = buildingInfo.getIndoorPOIs();

                // ... and start positioning in that building
                for(Poi poi: indoorPOIs) {
                    if(poi.getIdentifier().equals(POIid)) {
                        targetCoordinate = poi.getCoordinate();
                        LocationRequest locationRequest = new LocationRequest.Builder().
                                useWifi(true)
                                .useBle(true).
                                useGps(true).
                                buildingIdentifier(buildingId).
                                useBatterySaver(true).
                                useDeadReckoning(true)
                                .useForegroundService(false)
                                .build();
                        locationRequest.autoEnableBleDuringPositioning();


                        SitumSdk.locationManager().
                                requestLocationUpdates(locationRequest, locationListener);
                    }
                }


            }

            @Override
            public void onFailure(Error error) {
                binding.statusText.setText(error.getMessage());
                Toast.makeText(ARActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
                Log.d("communication", error.getMessage());
                binding.progressBar.setVisibility(View.GONE);
            }
        });



    }


    private void placePOI(AnchorNode reference, float azim_) {
        ModelRenderable.builder().
                setSource(
                        ARActivity.this,
                        RenderableSource
                                .builder()
                                .setSource(ARActivity.this, Uri.parse(
                                                poi_uri)
                                        , RenderableSource.SourceType.GLTF2)
                                .setScale(.2f)
                                .build())
                .setRegistryId(poi_uri)
                .build()
                .thenAccept(modelRenderable -> addPOItoSceneFinal( modelRenderable,reference, azim_ ))
                .exceptionally(throwable -> {
                       Toast.makeText(ARActivity.this, "error:"+throwable.getCause(), Toast.LENGTH_SHORT).show();
                    return null;
                });
    }

    public void testingSitumFunc(List<CartesianCoordinate> coordinates,Float azim_) {
        final boolean[] isPlaced = {false};
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        arFragment.setOnTapArPlaneListener(new BaseArFragment.OnTapArPlaneListener() {
            @Override
            public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
                if (!isPlaced[0]) {
                    Anchor anchor= hitResult.createAnchor();
                    AnchorNode anchorNode = new AnchorNode(anchor);
                    binding.instrText.setText("Please wait while we calculate the route! Don't move!");
                    placeSampleLine(anchorNode, false);
                    placePOI(anchorNode, azim_);

                    for(int i=0; i<coordinates.size(); i++) {
                        AnchorNode repNode= new AnchorNode(null);
                            repNode.setLocalPosition(
                                    new Vector3((float) (anchorNode.getLocalPosition()
                                            .x + coordinates.get(i).getX()), anchorNode.getLocalPosition()
                                            .y, (float) (anchorNode.getLocalPosition()
                                            .z - coordinates.get(i).getY())));

                    placeSampleLine(repNode, true);
                    }
                    isPlaced[0] = true;
                }

                routeIterated=true;
            }
        });



    }

    private void placeSampleLine(AnchorNode anchorNode, boolean place_) {
        if (lastAnchorNode != null) {
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            Vector3 point1, point2;
            point1 = lastAnchorNode.getWorldPosition();
            point2 = anchorNode.getWorldPosition();

        /*
            First, find the vector extending between the two points and define a look rotation
            in terms of this Vector.
        */

            if(place_) {
            final Vector3 difference = Vector3.subtract(point1, point2);
            final Vector3 directionFromTopToBottom = difference.normalized();
            final Quaternion rotationFromAToB =
                    Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
            Texture.Sampler sampler = Texture.Sampler.builder()
                    .setMinFilter(Texture.Sampler.MinFilter.LINEAR_MIPMAP_LINEAR)
                    .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
                    .build();


            Texture.builder()
                    .setSource(() -> getApplicationContext().getAssets().open("arrow_texture.png"))
                    .setSampler(sampler)
                    .build().thenAccept(texture -> {
                        MaterialFactory.makeTransparentWithTexture(getApplicationContext(), texture) //new Color(0, 255, 244))
                                .thenAccept(
                                        material -> {
/* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
       to extend to the necessary length.  */
                                            ModelRenderable model = ShapeFactory.makeCube(
                                                    new Vector3(.3f, .006f, difference.length()),
                                                    Vector3.zero(), material);
/* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
       the midpoint between the given points . */
                                            AnchorNode node = new AnchorNode();
                                            node.setParent(anchorNode);
                                            node.setRenderable(model);
                                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                                            node.setWorldRotation(rotationFromAToB);
                                        }
                                );
                    });

        }
            if(!place_) {
//                Toast.makeText(this, anchorNode.getLocalPosition().toString(), Toast.LENGTH_SHORT).show();
            }

            Log.d("position:",anchorNode.getLocalPosition().toString());
            lastAnchorNode = anchorNode;

        }
    }

    private void addPOItoSceneFinal(ModelRenderable modelRenderable, AnchorNode reference, float  azim_) {

                      AnchorNode anchorNode1=new AnchorNode(null);
                    anchorNode1.setRenderable(modelRenderable);
                    arFragment.getArSceneView().getScene().addChild(anchorNode1);
                    CartesianHelper cartesianHelper=new CartesianHelper(currentLatLng);
                    com.example.indoortracking.Coordinate Convcoordinate_ =
                            cartesianHelper.GetLocalCoordinates(new LatLng(
                                    targetCoordinate.getLatitude(),
                                    targetCoordinate.getLongitude()
                            ), azim_);


                    CartesianCoordinate Singlecoordinate=
                            new CartesianCoordinate(Convcoordinate_.x, Convcoordinate_.y);

                    anchorNode1.setLocalPosition(new Vector3(
                            (float) (reference.getLocalPosition().x+ Singlecoordinate.getX()),
                            reference.getLocalPosition().y+.15f,
                            (float) (reference.getLocalPosition().z-Singlecoordinate.getY())
                    ));

                    anchorNode1.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, -1f, 0f), 30f));
                    binding.instrText.setVisibility(View.GONE);

    }



    private void setupTTs() {
        PackageManager pm = getPackageManager();
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        ResolveInfo resolveInfo = pm.resolveActivity(installIntent, PackageManager.MATCH_DEFAULT_ONLY);

        if (resolveInfo == null) {
            Toast.makeText(this, "Voice commands not supported!", Toast.LENGTH_SHORT).show();
            // Not able to find the activity which should be started for this intent
        } else {
            //startActivityForResult(installIntent, TTS_CHECK_CODE);
        }

        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    // To Choose language of speech
                    tts.setLanguage(Locale.ENGLISH);
                    tts.setEngineByPackageName("com.google.android.tts");
                } else {
                    Toast.makeText(ARActivity.this, String.valueOf(status), Toast.LENGTH_SHORT).show();
                }


            }
        });


    }

    private void speakTTs(String text) {
        if (voice) {
            if (!tts.isSpeaking()) {
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


    }

    private void fillbuildingData() {
        // get data from intent;
        if (getIntent() != null) {
            Intent intent = getIntent();
            Bundle bundle = intent.getBundleExtra("POIdata");
            if (bundle != null) {
                buildingId = bundle.getString("buildingId");
                targetFloorId = bundle.getString("floorId");
                POIid = bundle.getString("poiId");
                String poiname = bundle.getString("poiName");
                binding.poiText.setText(poiname);
                targetCoordinate = new Coordinate(bundle.getDouble("PoicoordinateX"), bundle.getDouble("PoicoordinateY"));
            }
        }
    }

    private void manageUI() {
        binding.recenter2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(arFragment!=null) {
                routeIterated=false;
                List<Node> children = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
                for (Node node_ : children) {
                    if (node_ instanceof AnchorNode) {
                        if (((AnchorNode) node_).getAnchor() != null) {
                            node_.setParent(null);
                            ((AnchorNode) node_).getAnchor().detach();
                            arFragment.getArSceneView().getScene().removeChild(node_);

                        }
                    }
                    if (!(node_ instanceof Camera) && !(node_ instanceof Sun)) {
                        node_.setParent(null);
                    }
                }
            }
            }
        });
        binding.close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(ARActivity.this, mapActivity.class));
                finish();
                overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);
            }
        });




    }

    private Boolean sampleYes=false;
    private LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(@NonNull Location location) {


            Log.i(TAG, "Updating User navigation with: location = [" + location + "]");
            if(!location.getFloorIdentifier().toString().equals("-1")) {
               // Toast.makeText(ARActivity.this, location.getFloorIdentifier(), Toast.LENGTH_SHORT).show();
                if (isNavigating) {
                    currentLatLng = new LatLng(location.getCoordinate().getLatitude(),
                            location.getCoordinate().getLongitude());


                    SitumSdk.navigationManager().updateWithLocation(location);
                    if (locationFound == false) {
                        binding.statusText.setText("Calculating distance to your destination.");
                        locationFound = true;

                    }
                } else {
                    currentLatLng = new LatLng(location.getCoordinate().getLatitude(),
                            location.getCoordinate().getLongitude());
                   // VirtualPOI();
                    startNavigation(location);

                    if (locationFound == false) {
                        binding.statusText.setText("Calculating distance to your destination.");
                        locationFound = true;
                    }

                }
            }

        }

        @Override
        public void onStatusChanged(@NonNull LocationStatus locationStatus) {

            Log.i(TAG, "User Location changed called with: status = [" + locationStatus + "]");

        }

        @Override
        public void onError(@NonNull Error error) {
            Toast.makeText(ARActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            binding.statusText.setText("Failed to create navigation due to location issue.");
            binding.progressBar.setVisibility(View.GONE);
            Log.e(TAG, "User Location error called with: error = [" + error + "]");
        }
    };


    // Computes a route from the current location to the targetCoordinate and
    // starts navigation along that route
    private void startNavigation(Location location) {
        // Removes previous navigation (just in case)
        SitumSdk.navigationManager().removeUpdates();
        // First, we build the coordinate converter ...
        coordinateConverter = new CoordinateConverter(buildingInfo_.getBuilding().getDimensions(),
                buildingInfo_.getBuilding().getCenter(), buildingInfo_.getBuilding().getRotation());
        // ... which allows us to build the destination point of the route
        Point pointB = new Point(buildingId, targetFloorId, targetCoordinate, coordinateConverter.toCartesianCoordinate(targetCoordinate));
        // ... while the origin point is just our current location
        Coordinate coordinateA = new Coordinate(location.getCoordinate().getLatitude(), location.getCoordinate().getLongitude());
        Point pointA = new Point(location.getBuildingIdentifier(), location.getFloorIdentifier(), coordinateA, coordinateConverter.toCartesianCoordinate(coordinateA));
        // The DirectionsRequest allows us to configure the route calculation: source point, destination point, other options...
        DirectionsRequest directionsRequest = new DirectionsRequest.Builder().from(pointA, null).to(pointB).build();
        // Then, we compute the route
        SitumSdk.directionsManager().requestDirections(directionsRequest, new Handler<Route>() {
            @Override
            public void onSuccess(Route route) {
                binding.instrText.setVisibility(View.VISIBLE);
                NavigationRequest navigationRequest = new NavigationRequest.Builder().
                        // Navigation will take place along this route
                                route(route).
                        //indicationsInterval(300)
                        distanceToChangeIndicationThreshold(3).

                        // ... stopping when we're closer than 4 meters to the destination
                                // change this later
                                distanceToGoalThreshold(4).
                        // ... or we're farther away than 10 meters from the route
                                outsideRouteThreshold(3).
                        // Low quality locations will not be taken into account when updating the navigation state
                                ignoreLowQualityLocations(true).
                        // ... neither locations computed at unexpected floors if the user
                        // is less than 1000 consecutive milliseconds in those floors
                                timeToIgnoreUnexpectedFloorChanges(1000).
                        build();
                // We start the navigation with this NavigationRequest,
                // which allows us to receive updates while the user moves along the route
                isNavigating = true;
                if(routeFound==false) {

                    binding.statusText.setText("Finding shortest route to your destination.");
                    speakTTs("Finding shortest route to your destination.");
                    routeFound=true;
                }
                SitumSdk.navigationManager().requestNavigationUpdates(navigationRequest, navigationListener);

            }

            @Override
            public void onFailure(Error error) {
                binding.statusText.setText("Couldn't find any possible route!");
                speakTTs("Couldn't find any possible route!");
                binding.progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Navigation Failed" + error);
            }
        });
    }


    // Receives 3 navigation events when a new location is fed to the NavigationManager
    // onDestinationReached -> The user has arrived to the destination
    // onProgress -> The user has moved along the route
    // onUserOutsideRoute -> The user went off-route
    // Receives 3 navigation events when a new location is fed to the NavigationManager
    // onDestinationReached -> The user has arrived to the destination
    // onProgress -> The user has moved along the route
    // onUserOutsideRoute -> The user went off-route
    private NavigationListener navigationListener = new NavigationListener() {
        @Override
        public void onDestinationReached() {
            Log.i(TAG, "Destination Reached");
            binding.statusText.setText("Destination Reached.");
            isNavigating = false;
            binding.progressBar.setVisibility(View.GONE);
            if(!arrived) {
                AlertDialog.Builder builder = new AlertDialog.Builder(ARActivity.this);
                builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivity(new Intent(ARActivity.this, mapActivity.class));
                        finish();
                        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);


                    }
                });
                builder.setTitle("Destination Reached!");
                builder.setMessage("You've arrived at your destination, press OK to exit.").show();
                SitumSdk.navigationManager().removeUpdates();
                speakTTs("Destination Reached!");
                arrived=true;



            }
        }

        @Override
        public void onProgress(NavigationProgress navigationProgress) {
            //Toast.makeText(ARActivity.this, "Route advances", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Route advances");
            //binding.arrow2d.setVisibility(View.VISIBLE);
            binding.statusText.setText("Follow the route.");
            binding.progressBar.setVisibility(View.GONE);
            NotoutsideRoute=true;
            Log.d("current indication", navigationProgress.getCurrentIndication().toString());
            if(String.valueOf(navigationProgress.getDistanceToClosestPointInRoute()).toString().length()>5) {
                binding.distanceleftText.setText(String.valueOf(navigationProgress.getDistanceToGoal() * 3.281)
                        .substring(0, 5) + "Ft");
            }
            else{
                binding.distanceleftText.setText(String.valueOf(navigationProgress.getDistanceToGoal() * 3.281)
                        .substring(0, 4) + "Ft");
            }
            boolean distanceLeftTrue = (int) navigationProgress.getDistanceToGoal()<5;
            //here assuming the walk speed at 1.5m/sec
            binding.timeleftText.setText(String.valueOf((navigationProgress.getTimeToGoal()/1.5)/60)
                    .toString().substring(0, 4)+"min");


            if(navigationProgress.getCurrentIndication()
                    .toString().toLowerCase().contains("straight") && distanceLeftTrue) {
                reachedPOI_=true;
            }
            else {
                reachedPOI_=false;
            }
            binding.indicText.setText(navigationProgress.getCurrentIndication().getOrientationType().toString());

            if(!isUpdated) {
                speakTTs(navigationProgress.getNextIndication().toText(ARActivity.this));
                new android.os.Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        isUpdated=false;
                    }
                }, 6000);
            }

            isUpdated=true;

            Log.d("Route Step", navigationProgress.getClosestLocationInRoute().getPosition().toString());


            if(!routeIterated) {
                List<CartesianCoordinate> coordinates = new ArrayList<>();
                CartesianHelper cartesianHelper=new CartesianHelper(currentLatLng);

                for (RouteSegment segment : navigationProgress.getSegments()) {
                    Log.i(TAG, "   Floor Id: " + segment.getFloorIdentifier());
                    for (Point point : segment.getPoints()) {
                        float yaw= azimuth;


                        com.example.indoortracking.Coordinate Convcoordinate_ =
                                cartesianHelper.GetLocalCoordinates(new LatLng(
                                        point.getCoordinate().getLatitude(),
                                        point.getCoordinate().getLongitude()
                                ), yaw);


                        CartesianCoordinate Singlecoordinate=
                                new CartesianCoordinate(Convcoordinate_.x, Convcoordinate_.y);

                        coordinates.add(Singlecoordinate);

                    }
                    Log.i(TAG, "    ----");

                }

                testingSitumFunc(coordinates, azimuth);

            }
        }

        @Override
        public void onUserOutsideRoute() {
            AlertDialog.Builder builder = new AlertDialog.Builder(ARActivity.this);
            if(NotoutsideRoute) {
                builder.setNeutralButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {


                    }
                });
                builder.setTitle("Outside Route!");
                builder.setMessage("You're going outside route. Follow the route").show();
                Toast.makeText(ARActivity.this, "You are going outside the route", Toast.LENGTH_SHORT).show();
                isNavigating = false;
                NotoutsideRoute=false;
                SitumSdk.navigationManager().removeUpdates();
            }

        }
    };


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show();
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                CameraPermissionHelper.launchPermissionSettings(this);
            }
            //finish();
        }
    }

   private void maybeEnableArButton() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        if (availability.isTransient()) {
            // Continue to query availability at 5Hz while compatibility is checked in the background.
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    maybeEnableArButton();
                }
            },200);
        }
        if (availability.isSupported()) {
           // Toast.makeText(this, "AR supported", Toast.LENGTH_SHORT).show();
            isARCoreSupportedAndUpToDate();

        } else { // The device is unsupported or unknown.
         //   Toast.makeText(this, "AR Not supported", Toast.LENGTH_SHORT).show();
        }
    }

    public void createSession() {
        // Create a new ARCore session.
        try {
            if (!CameraPermissionHelper.hasCameraPermission(this)) {
                CameraPermissionHelper.requestCameraPermission(this);
                return;
            }

            mSession = new Session(ARActivity.this,  EnumSet.of(Session.Feature.SHARED_CAMERA));
            Config config = mSession.getConfig();
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setGeospatialMode(Config.GeospatialMode.ENABLED);
            boolean isDepthSupported = mSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC);
            if (isDepthSupported) {
                // These three settings are needed to use Geospatial Depth.
                config.setDepthMode(Config.DepthMode.AUTOMATIC);

                //  config.setStreetscapeGeometryMode(Config.StreetscapeGeometryMode.ENABLED);
            }
            mSession.configure(config);


        } catch (UnavailableDeviceNotCompatibleException e) {
            throw new RuntimeException(e);
        } catch (UnavailableSdkTooOldException e) {
            throw new RuntimeException(e);
        } catch (UnavailableArcoreNotInstalledException e) {
            throw new RuntimeException(e);
        } catch (UnavailableApkTooOldException e) {
            throw new RuntimeException(e);
        }


    }
    private boolean isARCoreSupportedAndUpToDate() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        switch (availability) {
            case SUPPORTED_INSTALLED:
//                createSession();
                return true;

            case SUPPORTED_APK_TOO_OLD:
            case SUPPORTED_NOT_INSTALLED:
                try {
                    // Request ARCore installation or update if needed.
                    ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(ARActivity.this, true);
                    switch (installStatus) {
                        case INSTALL_REQUESTED:
                            ArCoreApk.InstallStatus installStatus_ = ArCoreApk.getInstance().requestInstall(ARActivity.this, true);
                            Toast.makeText(this, "AR Core required installation", Toast.LENGTH_SHORT).show();
                            Log.i(TAG, "ARCore installation requested.");
                            return false;
                        case INSTALLED:
                            //createSession();
                            return true;
                    }
                } catch (UnavailableException e) {
                    Log.e(TAG, "ARCore not installed", e);
                    Toast.makeText(this, "Ar error:" +e.getMessage(), Toast.LENGTH_SHORT).show();
                }
                return false;

            case UNSUPPORTED_DEVICE_NOT_CAPABLE:
                // This device is not supported for AR.
                return false;

            case UNKNOWN_CHECKING:
                // ARCore is checking the availability with a remote query.
                // This function should be called again after waiting 200 ms to determine the query result.
            case UNKNOWN_ERROR:
            case UNKNOWN_TIMED_OUT:
                // There was an error checking for AR availability. This may be due to the device being offline.
                // Handle the error appropriately.
        }
        return false;
    }


    @Override
    public void finish() {
        SitumSdk.locationManager().removeUpdates();
        SitumSdk.navigationManager().removeUpdates();
        locationListener=null;
        if(tts!=null) {
            tts.stop();
            tts.shutdown();
        }
        arrived=true;
        super.finish();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SitumSdk.locationManager().removeUpdates();
        SitumSdk.navigationManager().removeUpdates();
        locationListener=null;
        if(tts!=null) {
            tts.stop();
            tts.shutdown();
        }
        arrived=true;
        sensorManager.unregisterListener(this);
        sensorManager= null;
        sensor=null;
    }


    @Override
    protected void onResume() {
        super.onResume();
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }
        Sensor magneticField = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magneticField != null) {
            sensorManager.registerListener(this, magneticField,
                    SensorManager.SENSOR_DELAY_NORMAL, SensorManager.SENSOR_DELAY_UI);
        }

        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        // Success: Safe to create the AR session.
                    //  createSession();

                   //   mSession.resume();

                        break;
                    case INSTALL_REQUESTED:
                        // When this method returns `INSTALL_REQUESTED`:
                        // 1. ARCore pauses this activity.
                        // 2. ARCore prompts the user to install or update Google Play
                        //    Services for AR (market://details?id=com.google.ar.core).
                        // 3. ARCore downloads the latest device profile data.
                        // 4. ARCore resumes this activity. The next invocation of
                        //    requestInstall() will either return `INSTALLED` or throw an
                        //    exception if the installation or update did not succeed.
                        mUserRequestedInstall = false;
                        return;
                }
            }
        } catch (UnavailableUserDeclinedInstallationException e) {
            // Display an appropriate message to the user and return gracefully.
            Toast.makeText(this, "TODO: handle exception " + e, Toast.LENGTH_LONG)
                    .show();
            return;
        } catch (UnavailableDeviceNotCompatibleException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        switch (sensorEvent.sensor.getType())
        {
            case Sensor.TYPE_MAGNETIC_FIELD:
                mags = sensorEvent.values.clone();
                break;
            case Sensor.TYPE_ACCELEROMETER:
                accels = sensorEvent.values.clone();
                break;
        }

        if (mags != null && accels != null) {
            Rot = new float[9];
            I= new float[9];
            SensorManager.getRotationMatrix(Rot, I, accels, mags);
            // Correct if screen is in Landscape

            float[] outR = new float[9];
            SensorManager.remapCoordinateSystem(Rot, SensorManager.AXIS_X,SensorManager.AXIS_Z, outR);
            SensorManager.getOrientation(outR, values);

//            azimuth= values[0];
            azimuth = values[0] * 57.2957795f; //looks like we don't need this one
            pitch =values[1] * 57.2957795f;
            roll = values[2] * 57.2957795f;
            mags = null; //retrigger the loop when things are repopulated
            accels = null; ////retrigger the loop when things are repopulated
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }
}