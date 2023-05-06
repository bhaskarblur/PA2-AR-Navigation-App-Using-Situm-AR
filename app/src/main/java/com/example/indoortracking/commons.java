package com.example.indoortracking;

import static android.content.ContentValues.TAG;

import android.util.Log;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.Color;
import com.google.ar.sceneform.rendering.MaterialFactory;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ShapeFactory;

import es.situm.sdk.model.cartography.Point;

public class commons {

    private String API_KEY="ef34e0a493c5eb6345a3513a7cb03cf6fb418ee6d10d865f3f7e3b7c050e3b57";
    private String API_EMAIL="nick@zeniamobile.com";

    public String getGoogleMapApi() {
        return googleMapApi;
    }

    //this is my own, need to replace.
    private String googleMapApi="AIzaSyBS4QH7KbXakdfJP8v1qErqT0iF9GRJbII";

    public String getAPI_KEY() {
        return API_KEY;
    }

    public String getAPI_EMAIL() {
        return API_EMAIL;
    }

//    private void drawLine (AnchorNode node1, AnchorNode node2, Point p1, Point p2){
//        Draw a line between two AnchorNodes (adapted from https://stackoverflow.com/a/52816504/334402)
//        Log.d(TAG, "drawLine");
//        Vector3 point1, point2;
//        point1 = node1.getWorldPosition();
//        point2 = node2.getWorldPosition();
//
//
//        First, find the vector extending between the two points and define a look rotation
//        in terms of this Vector.
//        final Vector3 difference = Vector3.subtract(point1, point2);
//        final Vector3 directionFromTopToBottom = difference.normalized();
//        final Quaternion rotationFromAToB =
//                Quaternion.lookRotation(directionFromTopToBottom, Vector3.up());
//        MaterialFactory.makeOpaqueWithColor(getApplicationContext(), new Color(0, 255, 244))
//                .thenAccept(
//                        material -> {
//                            /* Then, create a rectangular prism, using ShapeFactory.makeCube() and use the difference vector
//                                   to extend to the necessary length.  */
//                            Log.d(TAG, "drawLine inside .thenAccept");
//                            ModelRenderable model = ShapeFactory.makeCube(
//                                    new Vector3(.5f, .1f, difference.length()),
//                                    Vector3.zero(), material);
//                            /* Last, set the world rotation of the node to the rotation calculated earlier and set the world position to
//                                   the midpoint between the given points . */
//                            Anchor lineAnchor = node2.getAnchor();
//                            nodeForLine = new Node();
//                            nodeForLine.setParent(node1);
//                            nodeForLine.setRenderable(model);
//                            nodeForLine.setWorldPosition(Vector3.add(point1, point2).scaled(.5f));
//                            nodeForLine.setWorldRotation(rotationFromAToB);
//                            arFragment.getArSceneView().getScene().addChild(nodeForLine);
//                        }
//                );



}
