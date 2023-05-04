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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedImage;
import com.google.ar.core.AugmentedImageDatabase;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
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
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAractivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SitumSdk.init(this);
        manageUI();
        setupTTs();
        checkPermission("",103);
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

              //  for(Floor floor: floors) {
                //    Toast.makeText(ARActivity.this, "FloorID: "
                  //          +floors, Toast.LENGTH_SHORT).show();
                //}

                // ... and start positioning in that building
                LocationRequest locationRequest = new LocationRequest.Builder().
                        useWifi(true).useBle(true).
                        buildingIdentifier(buildingId).
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

    private void fillbuildingData() {
        // get data from intent;
        Intent intent=getIntent();
        Bundle bundle= intent.getBundleExtra("POIdata");
        buildingId= bundle.getString("buildingId");
        targetFloorId= bundle.getString("floorId");
        POIid= bundle.getString("poiId");

        targetCoordinate= new Coordinate(bundle.getDouble("PoicoordinateX"), bundle.getDouble("coordinateY"));
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


        binding.startButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                speakTTs("hello");
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


    }

    private void speakTTs(String text) {
         if(!tts.isSpeaking()) {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                 Log.v(TAG, "Speak new API");
                 Bundle bundle = new Bundle();
                 bundle.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC);
                 tts.speak(text, TextToSpeech.QUEUE_FLUSH, bundle, null);
             } else {
                 Log.v(TAG, "Speak old API");
                 HashMap<String, String> param = new HashMap<>();
                 param.put(TextToSpeech.Engine.KEY_PARAM_STREAM, String.valueOf(AudioManager.STREAM_MUSIC));
                 tts.speak(text, TextToSpeech.QUEUE_FLUSH, param);
             }
        }

    }
    public void setupImage(Config config, Session session) {
        Bitmap image= BitmapFactory.decodeResource(getResources(),R.drawable.arrow);
        AugmentedImageDatabase database= new AugmentedImageDatabase(session);
        database.addImage("arrow",image);
        config.setAugmentedImageDatabase(database);
    }

    private void changeDirection(String rotate) {

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
                Ray ray= camera.screenPointToRay(1180/2f, 1920/2f);
                Vector3 newpos= ray.getPoint(1.8f);
                node.setLocalPosition(newpos);
                if(rotate.equals("straight")) {
                    node.setLocalRotation(Quaternion.axisAngle(new Vector3(0f, 1f, 0f), 90f));
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


        if(rotate.equals("straight")) {
          //  anchorNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 0, 1f), -90f));
        }
        else if(rotate.equals("back")) {
          //  anchorNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 0, 1f), 90f));
        }
        else if(rotate.equals("left")) {
            //anchorNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 0, 1f), 180f));
        }
        else {
            //anchorNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 0, 1f), -90f));
        }

        //Toast.makeText(this, "Placed", Toast.LENGTH_SHORT).show();

    }


    private LocationListener locationListener = new LocationListener() {

        @Override
        public void onLocationChanged(@NonNull Location location) {
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

            Log.i(TAG, "User Location changed called with: status = [" + locationStatus + "]");
            if(locationStatus.toString().toLowerCase().contains("building")) {
                binding.statusText.setText("You are not in the building!");
            }

        }

        @Override
        public void onError(@NonNull Error error) {
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
            binding.statusText.setText("AR supported");
            Toast.makeText(this, "AR supported", Toast.LENGTH_SHORT).show();
        } else { // The device is unsupported or unknown.
            binding.statusText.setText("AR Not supported");
            Toast.makeText(this, "AR Not supported", Toast.LENGTH_SHORT).show();
        }
    }

    public void createSession() {
        // Create a new ARCore session.
        try {
            mSession = new Session(this);
        } catch (UnavailableArcoreNotInstalledException ex) {
            throw new RuntimeException(ex);
        } catch (UnavailableApkTooOldException ex) {
            throw new RuntimeException(ex);
        } catch (UnavailableSdkTooOldException ex) {
            throw new RuntimeException(ex);
        } catch (UnavailableDeviceNotCompatibleException ex) {
            throw new RuntimeException(ex);
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
                return true;

            case SUPPORTED_APK_TOO_OLD:
            case SUPPORTED_NOT_INSTALLED:
                try {
                    // Request ARCore installation or update if needed.
                    ArCoreApk.InstallStatus installStatus = ArCoreApk.getInstance().requestInstall(this, true);
                    switch (installStatus) {
                        case INSTALL_REQUESTED:
                            Log.i(TAG, "ARCore installation requested.");
                            return false;
                        case INSTALLED:
                            return true;
                    }
                } catch (UnavailableException e) {
                    Log.e(TAG, "ARCore not installed", e);
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

    private void placeModel(Anchor anchor, ModelRenderable modelRenderable) {
        AnchorNode anchorNode1=new AnchorNode(anchor);
        anchorNode1.setRenderable(modelRenderable);
        arFragment.getArSceneView().getScene().addChild(anchorNode1);
    }
}