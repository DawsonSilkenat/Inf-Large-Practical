package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class Drone {
    
    private double longitude;
    private double latitude;
    private double moveDistance;
    private double readDistance;
    private double endingDistance;
    private int maxMoves;
    private int angleStepSize= 10;
    
    public Drone(double longitude, double latitude, double moveDistance, double readDistance, double endingDistance, int maxMoves) {
        this.longitude = longitude;
        this.latitude = latitude;
        this.moveDistance = moveDistance;
        this.readDistance = readDistance;
        this.endingDistance = endingDistance;
        this.maxMoves = maxMoves;
    }
    
    
    public void visitSensors(List<Sensor> sensors, List<Polygon> noFlyZones) {
//        var visitOrder = selectVistOrder(sensors);
//        // The ith element of moves is an array containing the angle of each move in order to visit the ith element of visit order
//        var moves = new ArrayList<int[]>();
//        int numberOfMoves = 0;
//        var longitude = this.longitude;
//        var latitude = this.latitude;
//        
//        for (Polygon building : noFlyZones) {
//            collisionDetection(0, 0, 0, 0, building);
//        }
        
//        for (int i = 0; i < visitOrder.size(); i++) {
//            var movesToSensor = findPath(longitude, latitude, visitOrder.get(i).getLongitude(), visitOrder.get(i).getLatitude(), readDistance);
//            moves.add(movesToSensor);
//            longitude += moveDistance * (movesToSensor[1] * Math.cos(movesToSensor[0] * Math.PI / 180) +
//                    movesToSensor[2] * Math.cos((movesToSensor[0] + angleStepSize) * Math.PI / 180));
//            latitude += moveDistance * (movesToSensor[1] * Math.sin(movesToSensor[0] * Math.PI / 180) +
//                    movesToSensor[2] * Math.sin((movesToSensor[0] + angleStepSize) * Math.PI / 180));
//            numberOfMoves += movesToSensor[1] + movesToSensor[2];
//        }
//        
//        var returnToStart = findPath(longitude, latitude, this.longitude, this.latitude, endingDistance);
//        moves.add(returnToStart);
//        numberOfMoves += returnToStart[1] + returnToStart[2];
        
    }
    
    /*
     * The drone cannot necessarily fly along the straight line to its destination, but we can easily construct a triangle
     * one side of which is that straight line and the other two sides are lines the drone can fly along
     * We assume acceptable error is generous enough that the diameter of the circle we want to land in is greater 
     *  than the move distance of the drone
     */
    private void findPath(double startLongitude, double startLatitude, double endLongitude, double endLatitude, double acceptableError) {
        
        
    }
    
    /*
     * Solve the travelling salesperson problem using straight line distance between sensors (plus start point)
     * This visit order becomes our estimate of the optimal order to visit the sensors
     * Going to start with 2-opt method, might change later. 
     */
    public List<Sensor> selectVistOrder(List<Sensor> sensors) {
        var order = new ArrayList<Sensor>();
        // Initialising a distances array so we don't recompute the distance each time it is needed
        // We consider our last location to be the start point of the drone
        var distances = new double[sensors.size() + 1][sensors.size() + 1];
        
        for (int i = 0; i < distances.length - 1; i++) {
            distances[i][distances.length - 1] = distanceBetween(sensors.get(i).getLongitude(), 
                    sensors.get(i).getLatitude(), longitude, latitude);
            distances[distances.length - 1][i] = distances[i][distances.length - 1];
        }
        for (int i = 0; i < distances.length - 2; i++) {
            for (int j = i + 1; j < distances.length - 1; j++) {
                distances[i][j] = distanceBetween(sensors.get(i).getLongitude(), sensors.get(i).getLatitude(), 
                        sensors.get(j).getLongitude(), sensors.get(j).getLatitude());
                distances[j][i] = distances[i][j];
            }
        }
        
        /*
         * We are using the 2-opt method to find a good solution to the travelling salesperson problem
         * It works by taking an initial choice of solution and improving it by reversing segments where doing so reduce 
         * the total length of the solution. It does this until it can't find any more swaps which improve the solution.  
         */
        var indices = new int[distances.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        
        var improvement = true;
        while (improvement) {
            improvement = false;
            for (int i = 0; i < indices.length - 2; i++) {
                for (int j = i + 1; j < indices.length - 1; j++) {
                    if (distances[indices[(i - 1 + indices.length) % indices.length]][indices[j]] + distances[indices[i]][indices[j + 1]] < 
                                  distances[indices[(i - 1 + indices.length) % indices.length]][indices[i]] + distances[indices[j]][indices[j + 1]]) {
                        for (int i2 = i, j2 = j; i2 < j2; i2++, j2--) {
                            var temp = indices[i2];
                            indices[i2] = indices[j2];
                            indices[j2] = temp;
                        }
                        improvement = true;
                    }
                }
            }  
        }
        
        for (int i = 0; i < indices.length - 1; i++) {
            order.add(sensors.get(indices[i]));
        }
        
        return order;
    }
    
    // Checks whether the straight line move from the first pair of coordinates to the second is legal
    private boolean checkMoveLegality(double lng1, double lat1, double lng2, double lat2, List<Polygon> noFlyZones) {
        // TODO Check if within confinement area
        
        
        
        for (Polygon zone : noFlyZones) {
            
            var boundary = zone.outer().coordinates();
            // Easiest way to check if the move enters the zone is to find where it intersects with an edge
            for (int i = 0; i < boundary.size() - 1; i++) {
                // We use equations of straight lines to find the point of intersection 
                // Then check if that point is part of both line segments 
                var lng3 = boundary.get(i).longitude();
                var lat3 = boundary.get(i).latitude();
                var lng4 = boundary.get(i + 1).longitude();
                var lat4 = boundary.get(i).latitude();
                
                // Note that we are using the equation ax + by + c = 0 as either a or b could be 0. x axis is longitude, y latitude 
                var a1 = lat2 - lat1;
                var b1 = lng1 - lng2;
                var c1 = lng1 * (-a1) + lat1 * (-b1); 
                
                var a2 = lat4 - lat3;
                var b2 = lng3 - lng4;
                var c2 = lng3 * (-a2) + lat3 * (-b2);
                
                // Can be shown this is the solution for the point of intersection if one exists
                if (a1 * b2 - a2 * b1 != 0) {
                    var point = new double[2];
                    point[0] = (b1 * c2 - b2 * c1) / (a1 * b2 - a2 * b1);
                    point[1] = (a2 * c1 - a1 * c2) / (a1 * b2 - a2 * b1);
                    
                    // Check if the intersect point lies on both line segments. If so the move isn't legal
                    if (point[0] >= Math.min(lng1, lng2) && point[0] <= Math.max(lng1, lng2) &&
                            point[1] >= Math.min(lat1, lat2) && point[1] <= Math.max(lat1, lat2) &&
                            point[0] >= Math.min(lng3, lng4) && point[0] <= Math.max(lng3, lng4) &&
                            point[1] >= Math.min(lat3, lat4) && point[1] <= Math.max(lat3, lat4)) {
                        return false;
                    }
                } else if (b2 * c1 - b1 * c2 == 0 && a2 * c1 - a1 * c2 == 0) {
                    // Tells us the two line segments are of the same line. We check if the segments overlap 
                    var point = new double[2];
                    if (distanceBetween(lng1, lat1, lng3, lat3) < distanceBetween(lng1, lat1, lng4, lat4)) {
                        point[0] = lng3;
                        point[1] = lat3;
                    } else {
                        point[0] = lng4;
                        point[1] = lat4;
                    }
                    if (point[0] >= Math.min(lng1, lng2) && point[0] <= Math.max(lng1, lng2) &&
                            point[1] >= Math.min(lat1, lat2) && point[1] <= Math.max(lat1, lat2)) {
                        return false;
                    } 
                }  
            }
        }
        // If we haven't found a reason for the move to be illegal, it is considered legal
        return true;
    }
    
    private double distanceBetween(double longitude1, double latitude1, double longitude2, double latitude2) {
        return Math.sqrt(Math.pow(longitude1 - longitude2, 2) + Math.pow(latitude1 - latitude2, 2));
    }
    
}
