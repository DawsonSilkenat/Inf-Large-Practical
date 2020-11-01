package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

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
    
    public LineString visitSensors(List<Sensor> sensors, List<Polygon> noFlyZones) {
        var visitOrder = selectVistOrder(sensors);
        var paths = new ArrayList<Path>();
        
        var currentLng = longitude;
        var currentLat = latitude;
        
        for (Sensor sensor : visitOrder) {
            var path = findPath(currentLng, currentLat, sensor.getLongitude(), sensor.getLatitude(), readDistance, 1, noFlyZones);
            currentLng = path.getEndLng();
            currentLat = path.getEndLat();
            paths.add(path);
            sensor.visit();   
        }
        
        paths.add(findPath(currentLng, currentLat, longitude, latitude, endingDistance, 0, noFlyZones));
        
        var asGeoJason = new ArrayList<Point>();
        for (Path path : paths) {
            asGeoJason.addAll(path.getPositions());
        }
        
        return LineString.fromLngLats(asGeoJason);        
    }
    
    
    private Path findPath(double startLng, double startLat, double endLng, double endLat, 
            double acceptableError, int minMoves, List<Polygon> noFlyZones) {
        var searchSpace = new PriorityQueue<Path>();
        searchSpace.add(new Path(startLng, startLat, endLng, endLat));
        
        while (!searchSpace.isEmpty()) {
            var currentPath = searchSpace.poll();
            if (currentPath.getHeuristic() <= acceptableError && currentPath.getMoves().size() > minMoves) {
                return currentPath;
            }
            
            for (int angle = 0; angle < 360; angle += angleStepSize) {
                var extendedPath = currentPath.extend(angle, moveDistance);
                
                if (checkMoveLegality(currentPath.getEndLng(), currentPath.getEndLat(), extendedPath.getEndLng(), extendedPath.getEndLat(), 
                        noFlyZones) && extendedPath.getMoves().size() < maxMoves) {
                    searchSpace.add(extendedPath);
                } 
            }
        }
        
        return null; 
    }
    
    
    /*
     * Solve the travelling salesperson problem using straight line distance between sensors (plus start point)
     * This visit order becomes our estimate of the optimal order to visit the sensors
     * Going to start with 2-opt method, might change later. 
     */
    private List<Sensor> selectVistOrder(List<Sensor> sensors) {
        var order = new ArrayList<Sensor>();
        // Initialising a distances array so we don't recompute the distance each time it is needed
        // We consider our last location to be the start point of the drone
        var distances = new double[sensors.size() + 1][sensors.size() + 1];
        
        for (int i = 0; i < distances.length - 1; i++) {
            distances[i][distances.length - 1] = getDistance(sensors.get(i).getLongitude(), 
                    sensors.get(i).getLatitude(), longitude, latitude);
            distances[distances.length - 1][i] = distances[i][distances.length - 1];
        }
        for (int i = 0; i < distances.length - 2; i++) {
            for (int j = i + 1; j < distances.length - 1; j++) {
                distances[i][j] = getDistance(sensors.get(i).getLongitude(), sensors.get(i).getLatitude(), 
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
    private boolean checkMoveLegality(double startLng, double startLat, double endLng, double endLat, List<Polygon> noFlyZones) {
        // TODO Check if within confinement area
        
        for (Polygon zone : noFlyZones) {
            var boundary = zone.outer().coordinates();
            // Easiest way to check if the move enters the zone is to find where it intersects with an edge
            for (int i = 0; i < boundary.size() - 1; i++) {
                // We use equations of straight lines to find the point of intersection 
                // Then check if that point is part of both line segments 
                var edgeLng1 = boundary.get(i).longitude();
                var edgeLat1 = boundary.get(i).latitude();
                var edgeLng2 = boundary.get(i + 1).longitude();
                var edgeLat2 = boundary.get(i + 1).latitude();
                
                // Note that we are using the equation ax + by + c = 0 as either a or b could be 0. x axis is longitude, y latitude 
                var a1 = endLat - startLat;
                var b1 = startLng - endLng;
                var c1 = -(startLng * a1) - (startLat * b1); 
                
                var a2 = edgeLat2 - edgeLat1;
                var b2 = edgeLng1 - edgeLng2;
                var c2 = -(edgeLng1 * a2) - (edgeLat1 * b2);
                
                // Can be shown this is the solution for the point of intersection if the lines aren't parallel
                if ((a1 * b2) - (a2 * b1) != 0) {
                    var pointLng = ((b1 * c2) - (b2 * c1)) / ((a1 * b2) - (a2 * b1));
                    var pointLat = ((a2 * c1) - (a1 * c2)) / ((a1 * b2) - (a2 * b1));
                    
                    // Check if the intersect point lies on both line segments. If so the move isn't legal
                    if (pointLng >= Math.min(startLng, endLng) && pointLng <= Math.max(startLng, endLng) &&
                            pointLat >= Math.min(startLat, endLat) && pointLat <= Math.max(startLat, endLat) &&
                            pointLng >= Math.min(edgeLng1, edgeLng2) && pointLng <= Math.max(edgeLng1, edgeLng2) &&
                            pointLat >= Math.min(edgeLat1, edgeLat2) && pointLat <= Math.max(edgeLat1, edgeLat2)) {
                        
                        return false;
                    }
                } else if ((b2 * c1) - (b1 * c2) == 0 && (a2 * c1) - (a1 * c2) == 0 &&
                        ((edgeLng1 >= Math.min(startLng, endLng) && edgeLng1 <= Math.max(startLng, endLng) && 
                          edgeLat1 >= Math.min(startLat, endLat) && edgeLat1 <= Math.max(startLat, endLat)) ||
                        ( edgeLng2 >= Math.min(startLng, endLng) && edgeLng2 <= Math.max(startLng, endLng) && 
                          edgeLat2 >= Math.min(startLat, endLat) && edgeLat2 <= Math.max(startLat, endLat)))) {
                    return false;
                }  
            }
        }
        // If we haven't found a reason for the move to be illegal, it is considered legal
        return true;
    }
    
    
    private double getDistance(double startLng, double startLat, double endLng, double endLat) {
        return Math.sqrt(Math.pow(startLng - endLng, 2) + Math.pow(startLat - endLat, 2));
    }
    
//    private double getDistance(Point start, Point end) {
//        return Math.sqrt(Math.pow(start.longitude() - end.longitude(), 2) + Math.pow(start.latitude() - end.latitude(), 2));
//    }
    
}













