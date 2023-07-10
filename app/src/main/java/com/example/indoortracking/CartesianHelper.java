package com.example.indoortracking;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.lang.Math;

class Coordinate {
    double x;
    double y;
    double latitude;
    double longitude;

    Coordinate(double x, double y, double latitude, double longitude) {
        this.x = x;
        this.y = y;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}

class CoordinateCartesianEquivalence {
    double m1 = 111132.92; 
    double m2 = -559.82;
    double m3 = 1.175;
    double m4 = -0.0023;
    double p1 = 111412.84;
    double p2 = -93.5;
    double p3 = 0.118;

    double zeroLat = 0;
    double zeroLong = 0;
    double metersPerDegreeLatitude = 0;
    double metersPerDegreeLongitude = 0;

    CoordinateCartesianEquivalence(double height, double width, Coordinate center) {
        double latitudeRadians = Math.toRadians(center.latitude);
        this.metersPerDegreeLatitude = this.m1 
            + (this.m2 * Math.cos(2 * latitudeRadians)) 
            + (this.m3 * Math.cos(4 * latitudeRadians)) 
            + (this.m4 * Math.cos(6 * latitudeRadians));
        this.metersPerDegreeLongitude = (this.p1 * Math.cos(latitudeRadians)) 
            + (this.p2 * Math.cos(3 * latitudeRadians)) 
            + (this.p3 * Math.cos(5 * latitudeRadians));

        double latDiffDegrees = (height / 2.0) / this.metersPerDegreeLatitude;        
        this.zeroLat = center.latitude - latDiffDegrees;

        double longDiffDegrees = (width / 2.0) / (this.metersPerDegreeLongitude * Math.cos(latDiffDegrees));       
        this.zeroLong = center.longitude - longDiffDegrees;
    }

    Coordinate toCartesianCoordinateNoRotation(Coordinate coordinate) {
        double x = (coordinate.longitude - this.zeroLong) * this.metersPerDegreeLongitude;
        double y = (coordinate.latitude - this.zeroLat) * this.metersPerDegreeLatitude;
        return new Coordinate(x, y, coordinate.latitude, coordinate.longitude);
    }

    Coordinate toCoordinateNoRotation(Coordinate coordinate) {
        double latitude = this.zeroLat + coordinate.y / this.metersPerDegreeLatitude;
        double longitude = this.zeroLong + coordinate.x / this.metersPerDegreeLongitude;
        return new Coordinate(coordinate.x, coordinate.y, latitude, longitude);
    }
}

class CoordinateConverter {
    Coordinate center;
    double height;
    double width;
    double rotation;
    CoordinateCartesianEquivalence earthMetricEq;

    CoordinateConverter(Coordinate center, double width, double height, double rotation) {
        this.width = width;
        this.height = height;
        this.rotation = rotation;
        this.earthMetricEq = new CoordinateCartesianEquivalence(height, width, center);
        this.center = this.earthMetricEq.toCartesianCoordinateNoRotation(center);
    }

    Coordinate toCartesian(double lat, double lng) {
        Coordinate geoCoordinate = new Coordinate(0, 0, lat, lng);
        Coordinate nonRotated = this.earthMetricEq.toCartesianCoordinateNoRotation(geoCoordinate);
        Coordinate rotated = this.rotate(nonRotated, this.rotation);
        return rotated;
    }

    Coordinate toCoordinate(double x, double y) {
        Coordinate coordinate = new Coordinate(x, y, 0, 0);
        Coordinate rotatedPoints = this.rotate(coordinate, -this.rotation);
        return this.earthMetricEq.toCoordinateNoRotation(rotatedPoints);
    }

    Coordinate rotate(Coordinate cartesianCoordinates, double radians) {
        double ox = this.center.x;
        double oy = this.center.y;
        double px = cartesianCoordinates.x;
        double py = cartesianCoordinates.y;

        double qx = ox + Math.cos(radians) * (px - ox) - Math.sin(radians) * (py - oy);
        double qy = oy + Math.sin(radians) * (px - ox) + Math.cos(radians) * (py - oy);

        return new Coordinate(qx, qy, 0, 0);
    }
}

public class CartesianHelper {

    private LatLng center_;
    public CartesianHelper(LatLng center_ ) {
        this.center_=center_;
    }

    public void setCenter_(LatLng center_) {
        this.center_=center_;
    }
    public Coordinate GetLocalCoordinates(LatLng point_, double yaw) {
        Coordinate center = new Coordinate(0, 0,
                center_.latitude, center_.longitude);

        //Use a coordinate converter object to convert coordinates
        // Use the center as the center of the canvas. In case of the AR use case, you can use
        // the center as the inital lat/lng position of the user and set with and heigth to 0.
        // The rotation is the initial orientation of the AR library (maybe the yaw of the user when opening the AR?)
        CoordinateConverter converter = new CoordinateConverter(center, 0, 0, Math.toRadians(yaw));

        // Now, let's convert a point from global coordinates to Cartesian coordinates
        // For instance, let's take a point at (43.843500, -8.579900)

//        Coordinate centerPoint = converter.toCartesian(point_.latitude, point_.longitude);
        Log.d("tag","Center x: " + center_.latitude);
        Log.d("tag","Center y: " +center_.longitude);

        Coordinate mainPoint = converter.toCartesian(point_.latitude, point_.longitude);

        Log.d("tag","Cartesian x: " + mainPoint.x);
        Log.d("tag","Cartesian y: " + mainPoint.y);


        // Now, let's convert a point from cartesian coordinates to Cartesian coordinates
        // For instance, let's take a point at (10, 20)
        Coordinate cartesianPoint2 = converter.toCoordinate(10, 20);

        System.out.println("Coordinate x: " + cartesianPoint2.latitude);
        System.out.println("Coordinate y: " + cartesianPoint2.longitude);

        return mainPoint;
    }

}


