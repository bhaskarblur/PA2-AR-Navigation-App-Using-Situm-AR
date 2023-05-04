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
import es.situm.sdk.model.location.Coordinate;
import es.situm.sdk.model.location.Location;
import es.situm.sdk.location.LocationListener;
import es.situm.sdk.location.LocationRequest;

import android.Manifest;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import com.example.indoortracking.databinding.ActivityAractivityBinding;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Earth;
import com.google.ar.core.Frame;
import com.google.ar.core.FutureState;
import com.google.ar.core.GeospatialPose;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.VpsAvailability;
import com.google.ar.core.VpsAvailabilityFuture;
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
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import es.situm.sdk.SitumSdk;
import es.situm.sdk.location.LocationStatus;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.model.navigation.NavigationProgress;
import es.situm.sdk.navigation.NavigationListener;
import es.situm.sdk.navigation.NavigationRequest;
import es.situm.sdk.utils.Handler;

public class ARActivity extends AppCompatActivity implements Scene.OnUpdateListener{
    // to be changed with intent data
    private String buildingId = "13347";
    private String targetFloorId = "42059";
    private String arrow_uri="https://raw.githubusercontent.com/2wizstudio/indoorNav/main/arrow.gltf";
    private Coordinate targetCoordinate =
            new Coordinate(30.919247, 75.831841);
    private boolean isNavigating = false;
    private boolean arrived=false;
    private ActivityAractivityBinding binding;
    // To store the building information after we retrieve it
    private BuildingInfo buildingInfo_;
    private String POIid;
    private Session mSession;
    private boolean mUserRequestedInstall = true;
    private boolean locationFound= false;
    private boolean routeFound= false;
    private boolean isModelPlaced= false;
    private ArFragment arFragment;
    private AnchorNode anchorNode;
    private customARFrag customARFrag;
    private Node node;
    private int TTS_CHECK_CODE=111;
    private TextToSpeech tts;
    AnchorNode startNode;
    AnchorNode endNode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAractivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SitumSdk.init(this);
        manageUI();
        setupTTs();

        maybeEnableArButton();
       // fillbuildingData();


        // We ask for location and BLE permissions
        // Warning! This is a naive way to ask for permissions for the example's simplicity
        // Check https://developer.android.com/training/permissions/requesting to know how
        // to ask for permissions

        // We retrieve the information first, to make sure that
        // we have all the required info afterwards.


        SitumSdk.communicationManager().fetchBuildingInfo(buildingId, new Handler<BuildingInfo>() {
            @Override
            public void onSuccess(BuildingInfo buildingInfo) {
                buildingInfo_ = buildingInfo;
                Collection<Floor> floors = buildingInfo.getFloors();
                Collection<Poi> indoorPOIs = buildingInfo.getIndoorPOIs();

                // ... and start positioning in that building
                LocationRequest locationRequest = new LocationRequest.Builder().
                        useWifi(true).useBle(true).
                        buildingIdentifier(buildingId).
                        useDeadReckoning(true).
                        build();
                Log.i(TAG, "BuildingBasicInfo = [" + buildingInfo + "]");
                for (Floor floor : floors) {
                    Log.i(TAG, "Floor Data = [" + floor + "]");
                }
                for (Poi indoorPOI : indoorPOIs) {
                    Log.d("_", "indoorPoi = [" + indoorPOI + "]");
                }
                  SitumSdk.locationManager().
                        requestLocationUpdates(locationRequest, locationListener);
                  changeDirection("straight");

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

    private void setupTTs() {
        PackageManager pm = getPackageManager();
        Intent installIntent = new Intent();
        installIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        ResolveInfo resolveInfo = pm.resolveActivity( installIntent, PackageManager.MATCH_DEFAULT_ONLY );

        if( resolveInfo == null ) {
            Toast.makeText(this, "Voice commands not supported!", Toast.LENGTH_SHORT).show();
            // Not able to find the activity which should be started for this intent
        } else {
            startActivityForResult(installIntent, TTS_CHECK_CODE);
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
                    Toast.makeText(ARActivity.this, String.valueOf(status), Toast.LENGTH_SHORT).show();
                }



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
    private void fillbuildingData() {
        // get data from intent;
        Intent intent=getIntent();
        Bundle bundle= intent.getBundleExtra("POIdata");
        if(bundle!=null) {
            buildingId = bundle.getString("buildingId");
            targetFloorId = bundle.getString("floorId");
            POIid = bundle.getString("poiId");
            targetCoordinate = new Coordinate(bundle.getDouble("PoicoordinateX"), bundle.getDouble("PoicoordinateY"));
        }
    }

    private void manageUI() {
        arFragment= (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        binding.close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                overridePendingTransition(R.anim.slide_in_right,R.anim.slide_out_right);
            }
        });


    }

    private void setPOIModel() {
        Earth earth = mSession.getEarth();
        VpsAvailabilityFuture future = mSession.checkVpsAvailabilityAsync(targetCoordinate.getLatitude(), targetCoordinate.getLongitude(), new Consumer<VpsAvailability>() {
            @Override
            public void accept(VpsAvailability vpsAvailability) {

            }
        });

// Poll VpsAvailabilityFuture later, for example, in a render loop.
        if (future.getState() == FutureState.DONE) {
            switch (future.getResult()) {
                case AVAILABLE:
                    // VPS is available at this location.

                    if (earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
                        GeospatialPose cameraGeospatialPose = earth.getCameraGeospatialPose();
                        if (earth.getTrackingState() == TrackingState.TRACKING) {
                           Anchor terrainAnchor =
                                    earth.resolveAnchorOnTerrain(
                                            /* Locational values */
                                            targetCoordinate.getLatitude(),
                                            targetCoordinate.getLongitude(),
                                            2f,
                                            /* Rotational pose values */
                                            1,
                                            1,
                                            1,
                                            1);

                            switch (terrainAnchor.getTerrainAnchorState()) {
                                case SUCCESS:
                                    if (terrainAnchor.getTrackingState() == TrackingState.TRACKING) {
                                        //renderAnchoredContent(terrainAnchor);
                                        earth.createAnchor( targetCoordinate.getLatitude(),
                                                targetCoordinate.getLongitude(),
                                                2f,
                                                /* Rotational pose values */
                                                1,
                                                1,
                                                1,
                                                1);

                                    }
                                    break;
                                case TASK_IN_PROGRESS:
                                    // ARCore is contacting the ARCore API to resolve the Terrain anchor's pose.
                                    // Display some waiting UI.
                                    break;
                                case ERROR_UNSUPPORTED_LOCATION:
                                    // The requested anchor is in a location that isn't supported by the Geospatial API.
                                    break;
                                case ERROR_NOT_AUTHORIZED:
                                    // An error occurred while authorizing your app with the ARCore API. See
                                    // https://developers.google.com/ar/reference/java/com/google/ar/core/Anchor.TerrainAnchorState#error_not_authorized
                                    // for troubleshooting steps.
                                    break;
                                case ERROR_INTERNAL:
                                    // The Terrain anchor could not be resolved due to an internal error.
                                    break;
                                case NONE:
                                    // This Anchor isn't a Terrain anchor or it became invalid because the Geospatial Mode was
                                    // disabled.
                                    break;
                            }
                            // This anchor can't be used immediately; check its TrackingState
                            // and TerrainAnchorState before rendering content on this anchor.
                        }
                        // cameraGeospatialPose contains geodetic location, rotation, and confidences values.
                    }
                    break;
                case UNAVAILABLE:
                    // VPS is unavailable at this location.
                    break;
                case ERROR_NETWORK_CONNECTION:
                    // The external service could not be reached due to a network connection error.
                    break;

                // Handle other error states, e.g. ERROR_RESOURCE_EXHAUSTED, ERROR_INTERNAL, ...
            }
        }
        
    }

    public void setupImage(Config config, Session session) {
        Bitmap image= BitmapFactory.decodeResource(getResources(),R.drawable.arrow);
        AugmentedImageDatabase database= new AugmentedImageDatabase(session);
        database.addImage("arrow",image);
        config.setAugmentedImageDatabase(database);
    }

    private void createLineBetweenAnchors() {

        Vector3 startWorldPosition = startNode.getWorldPosition();
        Vector3 endWorldPosition = endNode.getWorldPosition();
        Vector3 difference = Vector3.subtract(endWorldPosition, startWorldPosition);
        float length = difference.length();

        // Create a cylinder with a height equal to the distance between anchors.
        MaterialFactory.makeOpaqueWithColor(this,
                        new Color(getResources().getColor(R.color.redPrimary)))
                .thenAccept(material -> {
                    ModelRenderable model = ShapeFactory.makeCylinder(0.04f, length, new Vector3(0f, length / 2, 0f), material);
                    Node node = new Node();
                    node.setRenderable(model);
                    node.setParent(startNode);

                    // Rotate the cylinder to align it with the line between anchors.
                    Quaternion rotation = Quaternion.lookRotation(difference, Vector3.up());
                    node.setWorldRotation(rotation);
                });
    }

    private void changeDirection(String rotate) {

        arFragment.setOnTapArPlaneListener(new BaseArFragment.OnTapArPlaneListener() {
            @Override
            public void onTapPlane(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
                Toast.makeText(ARActivity.this, plane.getType().ordinal(), Toast.LENGTH_SHORT).show();
            }
        });
        ModelRenderable.builder().
                setSource(
                        ARActivity.this,
                        RenderableSource
                                .builder()
                                .setSource(ARActivity.this, Uri.parse(
                                                arrow_uri)
                                        , RenderableSource.SourceType.GLTF2)
                                .setScale(2.2f)
                                .build())
                .setRegistryId(arrow_uri)
                .build()
                .thenAccept(modelRenderable -> addModeltoScene(null, modelRenderable, rotate))
                .exceptionally(throwable -> {
                    //   Toast.makeText(ARActivity.this, "error:"+throwable.getCause(), Toast.LENGTH_SHORT).show();
                    return null;
                });
    }

    private void addModeltoScene(Anchor anchor, ModelRenderable modelRenderable, String rotate) {
        //AnchorNode node= new AnchorNode(anchor);
        List<Node> children = new ArrayList<>(arFragment.getArSceneView().getScene().getChildren());
        for (Node node_ : children) {
            if (node_ instanceof AnchorNode) {
                if (((AnchorNode) node_).getAnchor() != null) {
                     ((AnchorNode) node_).getAnchor().detach();

                }
            }
            if (!(node_ instanceof Camera) && !(node_ instanceof Sun)) {
                    node_.setParent(null);
            }
        }
        node = new Node();
        node.setParent(arFragment.getArSceneView().getScene());
        node.setRenderable(modelRenderable);
      //  AnchorNode anchorNode1=new AnchorNode(anchor);
        //anchorNode1.setRenderable(modelRenderable);
        arFragment.getArSceneView().getScene().addChild(node);
        arFragment.getArSceneView().getScene().addOnUpdateListener(new Scene.OnUpdateListener() {
            @Override
            public void onUpdate(FrameTime frameTime) {
                Camera camera=arFragment.getArSceneView().getScene().getCamera();
                Ray ray= camera.screenPointToRay(1580/2f, 1700);
                Vector3 newpos= ray.getPoint(2f);
                node.setLocalPosition(newpos);
                if(rotate.equals("straight")) {
                    node.setLocalRotation(Quaternion.axisAngle(new Vector3(-.8f, 1.8f, 1.5f), 90f));
                }
                if(rotate.equals("left")) {
                    node.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 180f));
                }
                if(rotate.equals("back")) {
                    node.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 270f));
                }
                if(rotate.equals("right")) {
                    node.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 0f));
                }

            }
        });



    }


    private LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(@NonNull Location location) {
            Toast.makeText(ARActivity.this, "Hello2", Toast.LENGTH_SHORT).show();
            Log.i(TAG, "Updating User navigation with: location = [" + location + "]");
            if (isNavigating) {
                SitumSdk.navigationManager().updateWithLocation(location);
                if(locationFound==false) {
                    binding.statusText.setText("Calculating distance to your destination.");
                    locationFound=true;
                }
            } else {
                 startNavigation(location);
                if(locationFound==false) {
                    binding.statusText.setText("Calculating distance to your destination.");
                    locationFound=true;
                }

            }
        }

        @Override
        public void onStatusChanged(@NonNull LocationStatus locationStatus) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                //    binding.statusText.setText(locationStatus.toString());
                }
            });

            Log.i(TAG, "User Location changed called with: status = [" + locationStatus + "]");
            if(locationStatus.toString().toLowerCase().contains("building")) {
                binding.statusText.setText("You are not in the building!");
            }

        }

        @Override
        public void onError(@NonNull Error error) {
            Toast.makeText(ARActivity.this, error.getMessage(), Toast.LENGTH_SHORT).show();
            binding.statusText.setText("Couldn't access user location");
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
        SitumSdk.directionsManager().requestDirections(directionsRequest, new Handler<Route>() {
            @Override
            public void onSuccess(Route route) {
                // When the route is computed, we configure the NavigationRequest
                NavigationRequest navigationRequest = new NavigationRequest.Builder().
                        // Navigation will take place along this route
                                route(route).
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
                if(routeFound==false) {
                    binding.statusText.setText("Finding shortest route to your destination.");
                    speakTTs("Finding shortest route to your destination.");
                    routeFound=true;
                }
                changeDirection("straight");
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
                      //  startActivity(new Intent(ARActivity.this, mapActivity.class));
                      //  finish();
                       // overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_right);

                    }
                });
                builder.setTitle("Destination Reached!");
                builder.setMessage("You've arrived at your destination, press OK to exit.").show();
                SitumSdk.navigationManager().removeUpdates();
                arrived=true;
                speakTTs("Destination Reached!");

            }
        }

        @Override
        public void onProgress(NavigationProgress navigationProgress) {
            Log.i(TAG, "Route advances");
            //binding.arrow2d.setVisibility(View.VISIBLE);
            binding.statusText.setText("Follow the route.");
            binding.progressBar.setVisibility(View.GONE);
            Log.d("current indication", navigationProgress.getCurrentIndication().toString());

            if(String.valueOf(navigationProgress.getDistanceToClosestPointInRoute()).toString().length()>3) {
                binding.distanceleftText.setText(String.valueOf(navigationProgress.getDistanceToClosestPointInRoute())
                        .substring(0, 4) + "M");
            }
            else{
                binding.distanceleftText.setText(String.valueOf(navigationProgress.getDistanceToClosestPointInRoute())
                        .substring(0, 3) + "M");
            }
           // isModelPlaced=false;
            if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("left")) {
                binding.arrow2d.setRotation(-90f);
                isModelPlaced=false;
               changeDirection("left");

            }
            else if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("straight")) {
                isModelPlaced=false;
                changeDirection("straight");
                binding.arrow2d.setRotation(0f);
            }
            else if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("backward")) {
                binding.indicatorText.setText("");
                isModelPlaced=false;
                changeDirection("back");
                binding.indicatorText.setText("Take a U-turn");
                binding.arrow2d.setRotation(180f);
            }
            else if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("right")){
                binding.arrow2d.setRotation(90f);
                isModelPlaced=false;
                changeDirection("right");
            }

            binding.indicatorText.setText(navigationProgress.getCurrentIndication().toText(ARActivity.this));
            speakTTs(navigationProgress.getCurrentIndication().toText(ARActivity.this).toString());


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
            Log.i(TAG, "User outside of route");
            binding.statusText.setText(  "You are going off route! Please follow the route.");

            isNavigating = false;
            SitumSdk.navigationManager().removeUpdates();
        }
    };

    public void checkPermission(String permission, int requestCode)
    {
        // Checking if permission is not granted
        if (ContextCompat.checkSelfPermission(ARActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            ActivityCompat.requestPermissions(ARActivity.this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            Manifest.permission.CAMERA,
                            android.Manifest.permission.BLUETOOTH_SCAN,
                            android.Manifest.permission.BLUETOOTH_CONNECT}, requestCode);

        }
    }

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

            mSession = new Session(this);
            Config config = mSession.getConfig();
            config.setGeospatialMode(Config.GeospatialMode.ENABLED);
            mSession.configure(config);
        } catch (UnavailableArcoreNotInstalledException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
          //  throw new RuntimeException(ex);
        } catch (UnavailableApkTooOldException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
            //throw new RuntimeException(ex);
        } catch (UnavailableSdkTooOldException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
          //  throw new RuntimeException(ex);
        } catch (UnavailableDeviceNotCompatibleException ex) {
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_SHORT).show();
          //  throw new RuntimeException(ex);
        }

        // Create a session config.
        Config config = new Config(mSession);

        // Do feature-specific operations here, such as enabling depth or turning on
        // support for Augmented Faces.

        // Configure the session.
        mSession.configure(config);
    }
    private boolean isARCoreSupportedAndUpToDate() {
        ArCoreApk.Availability availability = ArCoreApk.getInstance().checkAvailability(this);
        switch (availability) {
            case SUPPORTED_INSTALLED:
                createSession();
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
                            createSession();
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
    protected void onDestroy() {
        super.onDestroy();
        SitumSdk.navigationManager().removeUpdates();
        if(tts!=null) {
            tts.stop();
            tts.shutdown();
        }
        arrived=true;
    }

    @Override
    public void onUpdate(FrameTime frameTime) {

        Frame frame= arFragment.getArSceneView().getArFrame();
        Collection<AugmentedImage> images= frame.getUpdatedTrackables(AugmentedImage.class);
        for(AugmentedImage image: images) {
            if(image.getTrackingState()== TrackingState.TRACKING) {
                if(image.getName().equals("arrow")) {
                    Anchor anchor = image.createAnchor(image.getCenterPose());
                    createModel(anchor);
                }
            }
        }

    }

    private void createModel(Anchor anchor) {
        ModelRenderable.builder()
                .setSource(this, Uri.parse(arrow_uri))
                .build()
                .thenAccept(modelRenderable -> {
                    placeModel(anchor, modelRenderable);
                }).exceptionally(new Function<Throwable, Void>() {
                    @Override
                    public Void apply(Throwable throwable) {
                        return null;
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            CameraPermissionHelper.requestCameraPermission(this);
            return;
        }
        try {
            if (mSession == null) {
                switch (ArCoreApk.getInstance().requestInstall(this, mUserRequestedInstall)) {
                    case INSTALLED:
                        // Success: Safe to create the AR session.
                        mSession = new Session(this);
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
        } catch (UnavailableSdkTooOldException e) {
            throw new RuntimeException(e);
        } catch (UnavailableArcoreNotInstalledException e) {
            throw new RuntimeException(e);
        } catch (UnavailableApkTooOldException e) {
            throw new RuntimeException(e);
        }

    }

    private void placeModel(Anchor anchor, ModelRenderable modelRenderable) {
        AnchorNode anchorNode1=new AnchorNode(anchor);
        anchorNode1.setRenderable(modelRenderable);
        arFragment.getArSceneView().getScene().addChild(anchorNode1);
    }
}