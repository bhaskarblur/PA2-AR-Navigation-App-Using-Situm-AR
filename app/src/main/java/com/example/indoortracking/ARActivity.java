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
import es.situm.sdk.model.location.Coordinate;
import es.situm.sdk.model.location.Location;
import es.situm.sdk.location.LocationListener;
import es.situm.sdk.location.LocationRequest;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

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
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.BaseArFragment;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Function;

import es.situm.sdk.SitumSdk;
import es.situm.sdk.location.LocationStatus;
import es.situm.sdk.model.cartography.BuildingInfo;
import es.situm.sdk.model.navigation.NavigationProgress;
import es.situm.sdk.navigation.NavigationListener;
import es.situm.sdk.navigation.NavigationRequest;
import es.situm.sdk.utils.Handler;
import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender;
import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper;

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
    private LocationScene locationScene;
    private int TTS_CHECK_CODE=111;
    private TextToSpeech tts;
    AnchorNode startNode;
    AnchorNode endNode;
    ModelRenderable andyRenderable;
    private AnchorNode lastAnchorNode= new AnchorNode();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAractivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        SitumSdk.init(this);
        setupTTs();
        maybeEnableArButton();
        manageUI();
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
                  //changeDirection("straight");

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

    private Node nodeForLine;

    private void createTestRoute() {
        List<Point> points=new ArrayList<>();
        //points.add(new Point());

        for(Point point:points) {
            float[] position = {(float) point.getCartesianCoordinate().getX(),
                    (float) point.getCartesianCoordinate().getY(), (float)-1};      //  { x, y, z } position
            float[] rotation = { 0, 0, 0, 1 };
            Pose pose= new Pose(position, rotation);
           // addLineBetweenHits_2(point, pose);
        }
    }
    private void addLineBetweenHits_2(Point point, Pose pose) {
       // Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(arFragment.getArSceneView().getArFrame().getCamera().getPose());

        Anchor anchor = arFragment.getArSceneView().getSession().createAnchor(pose);
        AnchorNode anchorNode = new AnchorNode(anchor);

        if (lastAnchorNode != null) {
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            Vector3 point1, point2;
            point1 = lastAnchorNode.getWorldPosition();
            point2 = anchorNode.getWorldPosition();

        /*
            First, find the vector extending between the two points and define a look rotation
            in terms of this Vector.
        */
            final Vector3 difference = Vector3.subtract(point1, point2);
            final Vector3 directionFromTopToBottom = difference.normalized();
            final Quaternion rotationFromAToB =
                    Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
            Texture.Sampler sampler = Texture.Sampler.builder()
                    .setMinFilter(Texture.Sampler.MinFilter.LINEAR_MIPMAP_LINEAR)
                    .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
                    // .setWrapModeR(Texture.Sampler.WrapMode.REPEAT)
                    // .setWrapModeS(Texture.Sampler.WrapMode.REPEAT)
                    // .setWrapModeT(Texture.Sampler.WrapMode.REPEAT)
                    .build();

            // 1. Make a texture
            Texture.builder()
                    .setSource(() ->    getApplicationContext().getAssets().open("arrow_texture.png"))
                    .setSampler(sampler)
                    .build().thenAccept(texture -> {
                        MaterialFactory.makeTransparentWithTexture(getApplicationContext(),texture) //new Color(0, 255, 244))
                                .thenAccept(
                                        material -> {
/* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
       to extend to the necessary length.  */
                                            ModelRenderable model = ShapeFactory.makeCube(
                                                    new Vector3(.2f, .008f, difference.length()),
                                                    Vector3.zero(), material);
/* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
       the midpoint between the given points . */
                                            Node node = new Node();
                                            node.setParent(anchorNode);
                                            node.setRenderable(model);
                                            node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                                            node.setWorldRotation(rotationFromAToB);
                                        }
                                );
                    });
            lastAnchorNode = anchorNode;
        }
    }



    private void placePOI() {
        CompletableFuture<ModelRenderable> andy = ModelRenderable.builder()
                .setSource(
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
                .exceptionally(throwable -> {
                       Toast.makeText(ARActivity.this, "error:"+throwable.getCause(), Toast.LENGTH_SHORT).show();
                    return null;
                });

        Toast.makeText(this, "first", Toast.LENGTH_SHORT).show();
        CompletableFuture.allOf(andy)
                .handle(
                        (notUsed, throwable) ->
                        {
                            if (throwable != null) {
                                Toast.makeText(this, "Unable to load renderables", Toast.LENGTH_SHORT).show();
                                return null;
                            }

                            try {
                                andyRenderable = andy.get();

                            } catch (InterruptedException | ExecutionException ex) {
                                Toast.makeText(this, "Unable to load renderables", Toast.LENGTH_SHORT).show();
                            }
                            return null;
                        });

        Frame frame= arFragment.getArSceneView().getArFrame();
        arFragment.getArSceneView().getScene().
                addOnUpdateListener(
                        frameTime -> {

                            if (locationScene == null) {
                                Node base = new Node();
                                base.setRenderable(andyRenderable);
                                locationScene = new LocationScene(this,
                                        arFragment.getArSceneView());
                                locationScene.setMinimalRefreshing(true);
                                locationScene.setRefreshAnchorsAsLocationChanges(true);
                                locationScene.setOffsetOverlapping(true);
                                locationScene.mLocationMarkers.add(
                                        new LocationMarker(
                                                75.8317506,
                                                30.9191793,
                                              base));

                                Toast.makeText(this, "Placed POI", Toast.LENGTH_SHORT).show();
                            }
                            if (locationScene != null) {
                                locationScene.processFrame(frame);
                            }

                        });
        ARLocationPermissionHelper.requestPermission(this);
    }

    private void addLineBetweenHits(HitResult hitResult, Plane plane, MotionEvent motionEvent) {
        Anchor anchor = hitResult.createAnchor();
        AnchorNode anchorNode = new AnchorNode(anchor);
        Log.d("RayPoint", "Raypoint: "+anchorNode.getWorldPosition().x+","+
                anchorNode.getWorldPosition().y+","+anchorNode.getWorldPosition().z);
        Log.d("RayPoint", "RayRotate: "+anchorNode.getWorldRotation().x+","+
                anchorNode.getWorldRotation().y+","+anchorNode.getWorldRotation().z+","+
                anchorNode.getWorldRotation().w);
        if (lastAnchorNode != null) {
            anchorNode.setParent(arFragment.getArSceneView().getScene());
            Vector3 point1, point2;
            point1 = lastAnchorNode.getWorldPosition();
            point2 = anchorNode.getWorldPosition();

        /*
            First, find the vector extending between the two points and define a look rotation
            in terms of this Vector.
        */
            final Vector3 difference = Vector3.subtract(point1, point2);
            final Vector3 directionFromTopToBottom = difference.normalized();
            final Quaternion rotationFromAToB =
                    Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
            Texture.Sampler sampler = Texture.Sampler.builder()
                    .setMinFilter(Texture.Sampler.MinFilter.LINEAR_MIPMAP_LINEAR)
                    .setMagFilter(Texture.Sampler.MagFilter.LINEAR)
                   // .setWrapModeR(Texture.Sampler.WrapMode.REPEAT)
                   // .setWrapModeS(Texture.Sampler.WrapMode.REPEAT)
                   // .setWrapModeT(Texture.Sampler.WrapMode.REPEAT)
                    .build();

            // 1. Make a texture
            Texture.builder()
                    .setSource(() ->    getApplicationContext().getAssets().open("arrow_texture.png"))
                    .setSampler(sampler)
                    .build().thenAccept(texture -> {
                                MaterialFactory.makeTransparentWithTexture(getApplicationContext(),texture) //new Color(0, 255, 244))
                                        .thenAccept(
                                                material -> {
/* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
       to extend to the necessary length.  */
                                                    ModelRenderable model = ShapeFactory.makeCube(
                                                            new Vector3(.2f, .008f, difference.length()),
                                                            Vector3.zero(), material);
/* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
       the midpoint between the given points . */
                                                    Node node = new Node();
                                                    node.setParent(anchorNode);
                                                    node.setRenderable(model);
                                                    node.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                                                    node.setWorldRotation(rotationFromAToB);
                                                }
                                        );
                            });
            lastAnchorNode = anchorNode;
        }
    }
        private void drawLine (AnchorNode node1, AnchorNode node2, Point p1, Point p2){
            //Draw a line between two AnchorNodes (adapted from https://stackoverflow.com/a/52816504/334402)
            Log.d(TAG, "drawLine");
            Vector3 point1, point2;
            point1 = node1.getWorldPosition();
            point2 = node2.getWorldPosition();


            //First, find the vector extending between the two points and define a look rotation
            //in terms of this Vector.
            final Vector3 difference = Vector3.subtract(point1, point2);
            final Vector3 directionFromTopToBottom = difference.normalized();
            final Quaternion rotationFromAToB =
                    Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
            MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(0, 255, 244))
                    .thenAccept(
                            material -> {
                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
                                   to extend to the necessary length.  */
                                Log.d(TAG, "drawLine inside .thenAccept");
                                ModelRenderable model = ShapeFactory.makeCube(
                                        new Vector3(.5f, .1f, difference.length()),
                                        Vector3.zero(), material);
                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
                                   the midpoint between the given points . */
                                Anchor lineAnchor = node2.getAnchor();
                                nodeForLine = new Node();
                                nodeForLine.setParent(node1);
                                nodeForLine.setRenderable(model);
                                nodeForLine.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
                                nodeForLine.setWorldRotation(rotationFromAToB);
                                arFragment.getArSceneView().getScene().addChild(nodeForLine);
                            }
                    );

        }


    private Node getAndy() {
        Node base = new Node();
        base.setRenderable(andyRenderable);
        arFragment.getArSceneView().getScene().addChild(base);
        Context c = this;
        base.setOnTapListener((v, event) -> {
            Toast.makeText(
                            c, "Andy touched.", Toast.LENGTH_LONG)
                    .show();
        });
        return base;
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
            //startActivityForResult(installIntent, TTS_CHECK_CODE);
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
            String poiname= bundle.getString("poiName");
            binding.poiText.setText(poiname);
            targetCoordinate = new Coordinate(bundle.getDouble("PoicoordinateX"), bundle.getDouble("PoicoordinateY"));
        }
    }

    private void manageUI() {
        arFragment= (ArFragment) getSupportFragmentManager().findFragmentById(R.id.arFragment);
        if(mSession!=null) {
            arFragment.getArSceneView().setupSession(mSession);
        }
        binding.close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
                overridePendingTransition(R.anim.slide_in_right,R.anim.slide_out_right);
            }
        });

        final boolean[] anchordone = {false};

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    //setModelPlaced();
                    addLineBetweenHits(hitResult, plane, motionEvent);
                });


    }


    public void setupImage(Config config, Session session) {
        Bitmap image= BitmapFactory.decodeResource(getResources(),R.drawable.arrow);
        AugmentedImageDatabase database= new AugmentedImageDatabase(session);
        database.addImage("arrow",image);
        config.setAugmentedImageDatabase(database);
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
                                // change this later
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
            navigationProgress.getNextIndication().getIndicationType().toString();
            Log.d("current indication", navigationProgress.getCurrentIndication().toString());

            if(String.valueOf(navigationProgress.getDistanceToClosestPointInRoute()).toString().length()>3) {
                binding.distanceleftText.setText(String.valueOf(navigationProgress.getDistanceToGoal())
                        .substring(0, 4) + "M");
            }
            else{
                binding.distanceleftText.setText(String.valueOf(navigationProgress.getDistanceToGoal())
                        .substring(0, 3) + "M");
            }

            binding.timeleftText.setText(String.valueOf(navigationProgress.getTimeToGoal()));
           // isModelPlaced=false;
            if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("left")) {
                binding.arrow2d.setRotation(-90f);
                isModelPlaced=false;
               //changeDirection("left");

            }
            else if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("straight")) {
                isModelPlaced=false;
               // changeDirection("straight");
                binding.arrow2d.setRotation(0f);
            }
            else if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("backward")) {
               // binding.indicatorText.setText("");
                isModelPlaced=false;
                //changeDirection("back");
              //  binding.indicatorText.setText("Take a U-turn");
                binding.arrow2d.setRotation(180f);
            }
            else if(navigationProgress.getCurrentIndication().toString().toLowerCase().contains("right")){
                binding.arrow2d.setRotation(90f);
                isModelPlaced=false;
               // changeDirection("right");
            }
            binding.indicText.setText(navigationProgress.getCurrentIndication().getOrientationType().toString());
            new android.os.Handler().postDelayed(new Runnable() {
                @Override
                public void run() {

                    //speakTTs(navigationProgress.getCurrentIndication().toText(ARActivity.this).toString());
                }
            },6000);



//            Log.i(TAG, "Current indication " + navigationProgress.getCurrentIndication().toText(ARActivity.this));
//            Log.i(TAG, "Next indication " + navigationProgress.getNextIndication().toText(ARActivity.this));
//            Log.i(TAG, "");
//            Log.i(TAG, " Distance to goal: " + navigationProgress.getDistanceToGoal());
//            Log.i(TAG, " Time to goal: " + navigationProgress.getTimeToGoal());
//            Log.i(TAG, " Closest location in route: " + navigationProgress.getClosestLocationInRoute());
//            Log.i(TAG, " Distance to closest location in route: " + navigationProgress.getDistanceToClosestPointInRoute());
//            Log.i(TAG, " Remaining segments: ");
            for (RouteSegment segment : navigationProgress.getSegments()) {
                Log.i(TAG, "   Floor Id: " + segment.getFloorIdentifier());
                for (Point point : segment.getPoints()) {
                    if(mSession!=null) {
                        new android.os.Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                float[] position = {(float) point.getCartesianCoordinate().getX(),
                                        (float) point.getCartesianCoordinate().getY(), (float)-2};      //  { x, y, z } position
                                float[] rotation = { 0, 0, 0, 1 };
                                Pose pose= new Pose(position, rotation);
                                //addLineBetweenHits_2(point, pose);
                            }
                        },500);

                    }
                    Log.i(TAG, "    Point: BuildingId " + point.getFloorIdentifier() + " FloorId " + point.getFloorIdentifier() + " Latitude " + point.getCoordinate().getLatitude() + " Longitude " + point.getCoordinate().getLongitude());
                }
                Log.i(TAG, "    ----");
            }
            Log.i(TAG, "--------");

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
            config.setUpdateMode(Config.UpdateMode.LATEST_CAMERA_IMAGE);
            config.setGeospatialMode(Config.GeospatialMode.ENABLED);
            mSession.configure(config);
            VpsAvailabilityFuture future = mSession.checkVpsAvailabilityAsync(targetCoordinate
                    .getLatitude(), targetCoordinate.getLongitude(), new Consumer<VpsAvailability>() {
                @Override
                public void accept(VpsAvailability vpsAvailability) {
                  //  Toast.makeText(ARActivity.this, String.valueOf(vpsAvailability.toString()), Toast.LENGTH_SHORT).show();
                }
            });

   ;
            if (future.getState() == FutureState.DONE) {
                Toast.makeText(this, "Hi", Toast.LENGTH_SHORT).show();
                switch (future.getResult()) {
                    case AVAILABLE:
                        Earth earth= mSession.getEarth();
                       // Toast.makeText(this, "hi", Toast.LENGTH_SHORT).show();
                    //    Toast.makeText(this, earth.getTrackingState().toString(), Toast.LENGTH_SHORT).show();
                        if (earth != null && earth.getTrackingState() == TrackingState.TRACKING) {
                            GeospatialPose cameraGeospatialPose = earth.getCameraGeospatialPose();
                            Anchor anchor =
                                    earth.createAnchor(
                                            /* Locational values */
                                            targetCoordinate.getLatitude(),
                                            targetCoordinate.getLongitude(),
                                            -.3,
                                            /* Rotational pose values */
                                            1f,
                                            1f,
                                            1f,
                                            1f);
                           // sampleAnchor=anchor;
                            Toast.makeText(this, "Tracking!", Toast.LENGTH_SHORT).show();
                        }

                        break;
                    case UNAVAILABLE:
                        Toast.makeText(this, "No Vps available here!", Toast.LENGTH_SHORT).show();
                        // VPS is unavailable at this location.
                        break;
                    case ERROR_NETWORK_CONNECTION:
                  //      Toast.makeText(this, "hi2", Toast.LENGTH_SHORT).show();
                        // The external service could not be reached due to a network connection error.
                        break;
                    case ERROR_NOT_AUTHORIZED:
                       // Toast.makeText(this, "Not Authorized", Toast.LENGTH_SHORT).show();

                    // Handle other error states, e.g. ERROR_RESOURCE_EXHAUSTED, ERROR_INTERNAL, ...
                }
            }
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

        // Do feature-specific operations here, such as enabling depth or turning on
        // support for Augmented Faces.

        // Configure the session.


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
        locationListener=null;
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