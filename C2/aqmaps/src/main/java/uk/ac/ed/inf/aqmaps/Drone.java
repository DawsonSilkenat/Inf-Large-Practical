package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class Drone {
    
//    private double longitude;
//    private double latitude;
    private Point startPosition;
    private double moveDistance;
    private double readDistance;
    private double endingDistance;
    private int maxMoves;
    private int angleStepSize = 10;
    
    public Drone(Point startPosition, double moveDistance, double readDistance, double endingDistance, int maxMoves) {
        this.startPosition = startPosition;
        this.moveDistance = moveDistance;
        this.readDistance = readDistance;
        this.endingDistance = endingDistance;
        this.maxMoves = maxMoves;
    }
    
    public LineString visitSensors(List<Sensor> sensors, List<Polygon> noFlyZones) {
        var visitOrder = selectVistOrder(sensors, noFlyZones);
        
//        var paths = new ArrayList<Path>();
        var paths = new ArrayList<Point[]>();
        var currentPosition = startPosition;
        
        for (Sensor sensor : visitOrder) {
            var path = findPath(currentPosition, sensor.getPosition(), readDistance, 1, noFlyZones);
//            currentPosition = path.getEndPosition();
            currentPosition = path[path.length - 1];
            paths.add(path);
            sensor.visit();   
        }
        paths.add(findPath(currentPosition, startPosition, endingDistance, 0, noFlyZones));
        
        var asGeoJason = new ArrayList<Point>();
        
        asGeoJason.add(startPosition);
        for (Point[] path : paths) {
            for (int i = 1; i < path.length; i++) {
                asGeoJason.add(path[i]);
            }
        }
//        for (Path path : paths) {
//            asGeoJason.addAll(path.getAllPositions());
//        }
        
        return LineString.fromLngLats(asGeoJason);        
    }
    
    
//    private Path findPath(Point start, Point goal, double acceptableError, int minMoves, List<Polygon> noFlyZones) {
//        var searchSpace = new PriorityQueue<Path>();
//        searchSpace.add(new Path(start, goal));
//        
//        while (!searchSpace.isEmpty()) {
//            var currentPath = searchSpace.poll();
//            if (getDistance(currentPath.getEndPosition(), goal) <= acceptableError && currentPath.getAllPositions().size() >= minMoves) {
//                searchSpace.clear();
//                return currentPath;
//            }
//            
//            for (int angle = 0; angle < 360; angle += angleStepSize) {
//                var extendedPath = currentPath.extend(angle, moveDistance);
//                
//                if (checkMoveLegality(currentPath.getEndPosition(), extendedPath.getEndPosition(), noFlyZones) && 
//                        extendedPath.getAllPositions().size() < maxMoves) {
//                    searchSpace.add(extendedPath);
//                } 
//            }
//        }
//        
//        return null; 
//    }
    
    private Point[] findPath(Point start, Point goal, double acceptableError, int minMoves, List<Polygon> noFlyZones) {
        var spaceLimit = 100;
        
        var arrayCompare = new Comparator<Point[]>() {
            @Override
            // Doesn't cover the empty array, but shouldn't matter for this use
            public int compare(Point[] path1, Point[] path2) {
                var estimate_1 = path1.length * moveDistance + getDistance(path1[path1.length - 1], goal);
                var estimate_2 = path2.length * moveDistance + getDistance(path2[path2.length - 1], goal);
                if (estimate_1 < estimate_2) {
                    return -1;
                } else if(estimate_1 > estimate_2) {
                    return 1;
                }
                return 0;
            }};
        
        var searchSpace = new PriorityQueue<>(arrayCompare);
        
        searchSpace.add(new Point[] {start});
        
        while (!searchSpace.isEmpty()) {
            var currrentPath = searchSpace.poll();
            
            if (getDistance(currrentPath[currrentPath.length - 1], goal) <= acceptableError && currrentPath.length > minMoves) {
                return currrentPath;
            } else {
                var pathCopy = new Point[currrentPath.length + 1];
                for (int i = 0; i < currrentPath.length; i++) {
                    pathCopy[i] = currrentPath[i];
                }
                
                var moveSpace = new PriorityQueue<>(arrayCompare);
                
                for (int angle = 0; angle < 360; angle += angleStepSize) {
                    // Shallow copy is fine here
                    var extendedPath = pathCopy.clone();
                    extendedPath[extendedPath.length - 1] = Point.fromLngLat(
                            currrentPath[currrentPath.length - 1].longitude() + Math.cos(angle * Math.PI / 180.0) * moveDistance, 
                            currrentPath[currrentPath.length - 1].latitude() + Math.sin(angle * Math.PI / 180.0) * moveDistance);
                    
                    if (checkMoveLegality(extendedPath[extendedPath.length - 2], extendedPath[extendedPath.length - 1], noFlyZones)) {
                        moveSpace.add(extendedPath);
                    }
  
                }
                
                var spaceLimiting = new PriorityQueue<>(arrayCompare);
                
                for (int i = 0; i < spaceLimit; i++) {
                    if (searchSpace.isEmpty() && moveSpace.isEmpty()) {
                        break;
                    } else if (!searchSpace.isEmpty() && (moveSpace.isEmpty() || arrayCompare.compare(searchSpace.peek(), moveSpace.peek()) < 0)) {
                        spaceLimiting.add(searchSpace.poll());
                    } else {
                        spaceLimiting.add(moveSpace.poll());
                    }  
                }
                
                searchSpace = spaceLimiting;
            }
        }
        
        return null;
    }
    
    
    /*
     * Solve the travelling salesperson problem using straight line distance between sensors (plus start point)
     * This visit order becomes our estimate of the optimal order to visit the sensors
     * Going to start with 2-opt method, might change later. 
     */
    private List<Sensor> selectVistOrder(List<Sensor> sensors, List<Polygon> noFlyZones) { 
        var order = new ArrayList<Sensor>();
//        var distances = new double[sensors.size() + 1][sensors.size() + 1];
//        
//        for (int i = 0; i < distances.length - 1; i++) {
//            distances[i][distances.length - 1] = getDistance(sensors.get(i).getPosition(), startPosition);
//            distances[distances.length - 1][i] = distances[i][distances.length - 1];
//        }
//        for (int i = 0; i < distances.length - 2; i++) {
//            for (int j = i + 1; j < distances.length - 1; j++) {
//                distances[i][j] = getDistance(sensors.get(i).getPosition(), sensors.get(j).getPosition());
//                distances[j][i] = distances[i][j];
//            }
//        }
        var distances = new int[sensors.size() + 1][sensors.size() + 1];
        
        for (int i = 0; i < distances.length - 1; i++) {
            distances[i][distances.length - 1] = findPath(startPosition, sensors.get(i).getPosition(), readDistance, 1, noFlyZones).length;
            distances[distances.length - 1][i] = distances[i][distances.length - 1];
        }
        for (int i = 0; i < distances.length - 2; i++) {
            for (int j = i + 1; j < distances.length - 1; j++) {
                distances[i][j] = findPath(sensors.get(i).getPosition(), sensors.get(j).getPosition(), readDistance, 1, noFlyZones).length;
                distances[j][i] = distances[i][j];
            }
        }
        
        var indices = twoOpt(distances);
        int droneIndex = 0;
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] == distances.length - 1) {
                droneIndex = i;
            }
        }
        
        for (int i = 1; i < indices.length; i++) {
            order.add(sensors.get(indices[(droneIndex + i) % indices.length]));
        }
        
        return order;
    }   
    
    /*
     * We are using the 2-opt method to find a good solution to the travelling salesperson problem
     * It works by taking an initial choice of solution and improving it by reversing segments where doing so reduce 
     * the total length of the solution. It does this until it can't find any more swaps which improve the solution.  
     */
    private int[] twoOpt(int[][] distances) {
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
                        
                        improvement = true;
                        for (int i2 = i, j2 = j; i2 < j2; i2++, j2--) {
                            var temp = indices[i2];
                            indices[i2] = indices[j2];
                            indices[j2] = temp;
                        }
                    }
                }
            }  
        }
        
        return indices;
    }
    
    
    // Checks whether the straight line move from the first pair of coordinates to the second is legal
    // TODO debug
    private boolean checkMoveLegality(Point start, Point end, List<Polygon> noFlyZones) {
        var startLng = start.longitude();
        var startLat = start.latitude();
        var endLng = end.longitude();
        var endLat = end.latitude();
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
    
    private double getDistance(Point start, Point end) {     
        return Math.sqrt(Math.pow(start.longitude() - end.longitude(), 2) + Math.pow(start.latitude() - end.latitude(), 2));
    }
    
}













