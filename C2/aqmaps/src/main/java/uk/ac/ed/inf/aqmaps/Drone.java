package uk.ac.ed.inf.aqmaps;

import java.util.List;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class Drone {
    
    private Point startPosition;
    private double moveDistance;
    private double readDistance;
    private double endingDistance;
    private int maxMoves;
    private int angleStepSize = 10;
    private List<Path> visitPaths;
    private List<Sensor> visitOrder;
    
    public Drone(Point startPosition, double moveDistance, double readDistance, double endingDistance, int maxMoves) {
        this.startPosition = startPosition;
        this.moveDistance = moveDistance;
        this.readDistance = readDistance;
        this.endingDistance = endingDistance;
        this.maxMoves = maxMoves;
        visitPaths = new ArrayList<>();
        visitOrder = new ArrayList<>();
    }
    
    public void visitSensors(List<Sensor> sensors, List<Polygon> noFlyZones) {
        visitOrder = selectVistOrder(sensors, noFlyZones);
        visitPaths = new ArrayList<Path>();
        var currentPosition = startPosition;
        
        for (Sensor sensor : visitOrder) {
            var path = findPath(currentPosition, sensor.getPosition(), readDistance, 1, noFlyZones);
            if (path != null) {
                currentPosition = path.getEndPosition();
                visitPaths.add(path);
                sensor.visit();  
            } 
        }
        visitPaths.add(findPath(currentPosition, startPosition, endingDistance, 0, noFlyZones));      
    }
    
    public Point getStartPosition() {
        return startPosition;
    }

    public List<Path> getVisitPaths() {
        return visitPaths;
    }

    public List<Sensor> getVisitOrder() {
        return visitOrder;
    }
      
    private Path findPath(Point start, Point goal, double acceptableError, int minMoves, List<Polygon> noFlyZones) {
        var beamWidth = 50;
        var comparator = new Comparator<Path>() {
            @Override
            public int compare(Path path1, Path path2) {
                var cost1 = path1.getPositions().size();
                var heuristic1 = (int) Math.ceil((getDistance(path1.getEndPosition(), goal) - acceptableError) / moveDistance);
                var cost2 = path2.getPositions().size();
                var heuristic2 = (int) Math.ceil((getDistance(path2.getEndPosition(), goal) - acceptableError) / moveDistance);
                
                return (cost1 + heuristic1) - (cost2 + heuristic2);
            }
        };
        
        var searchSpace = new PriorityQueue<Path>(comparator);
        searchSpace.add(new Path(start));
        
        while (!searchSpace.isEmpty()) {
            var currentPath = searchSpace.poll();
            if (getDistance(currentPath.getEndPosition(), goal) <= acceptableError && 
                    currentPath.getPositions().size() >= minMoves) {
                return currentPath;
            }
            
            for (int angle = 0; angle < 360; angle += angleStepSize) {
                var extendedPath = currentPath.extend(angle, moveDistance);
                
                if (checkMoveLegality(currentPath.getEndPosition(), extendedPath.getEndPosition(), noFlyZones) && 
                        extendedPath.getPositions().size() < maxMoves) {
                    searchSpace.add(extendedPath);
                } 
            }
            
            var beam = new PriorityQueue<Path>(comparator);
            for (int i = 0; i < beamWidth && !searchSpace.isEmpty(); i++) {
                beam.add(searchSpace.poll());
            }
            searchSpace = beam;
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
        var distances = new int[sensors.size() + 1][sensors.size() + 1];
        
        for (int i = 0; i < distances.length - 1; i++) {
            distances[i][distances.length - 1] = findPath(startPosition, sensors.get(i).getPosition(), 
                    readDistance, 1, noFlyZones).getPositions().size();
            distances[distances.length - 1][i] = distances[i][distances.length - 1];
        }
        for (int i = 0; i < distances.length - 2; i++) {
            for (int j = i + 1; j < distances.length - 1; j++) {
                distances[i][j] = findPath(sensors.get(i).getPosition(), sensors.get(j).getPosition(), 
                        readDistance, 1, noFlyZones).getPositions().size();
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
     * the total length of the solution. It does this until it can't find any more which improve the solution.  
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
                    if (distances[indices[(i - 1 + indices.length) % indices.length]][indices[j]] + 
                            distances[indices[i]][indices[(j + 1) % indices.length]] < 
                            distances[indices[(i - 1 + indices.length) % indices.length]][indices[i]] + 
                            distances[indices[j]][indices[(j + 1) % indices.length]]) {
                        
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
    
    public void testing(List<Polygon> noFlyZones) {
//        var start = Point.fromLngLat(-3.187204557674096, 55.944452094453304);
//        var end = Point.fromLngLat(-3.186904557674096,55.944452094453304);
//        System.out.println(checkMoveLegality(start, end, noFlyZones));
//        System.exit(0);
    }
    
    // Checks whether the straight line move from the first pair of coordinates to the second is legal
    private boolean checkMoveLegality(Point start, Point end, List<Polygon> noFlyZones) {
        var startLng = start.longitude();
        var startLat = start.latitude();
        var endLng = end.longitude();
        var endLat = end.latitude();
        // TODO Check if within confinement area
        
        
        
        // Note that we are using the equation ax + by + c = 0 as either a or b could be 0. x axis is longitude, y latitude 
        var a1 = startLat - endLat;
        var b1 = endLng - startLng;
        var c1 = -(startLng * a1) - (startLat * b1); 
        
//        System.out.println(a1 + " " + b1 + " " + c1);
        
        for (Polygon zone : noFlyZones) {
            var floatOffset = 1e-10;
            var boundary = zone.outer().coordinates();
            // Easiest way to check if the move enters the zone is to find where it intersects with an edge
            for (int i = 0; i < boundary.size() - 1; i++) {
                // We use equations of straight lines to find the point of intersection 
                // Then check if that point is part of both line segments 
                var edgeLng1 = boundary.get(i).longitude();
                var edgeLat1 = boundary.get(i).latitude();
                var edgeLng2 = boundary.get(i + 1).longitude();
                var edgeLat2 = boundary.get(i + 1).latitude();

                
                var a2 = edgeLat1 - edgeLat2;
                var b2 = edgeLng2 - edgeLng1;
                var c2 = -(edgeLng1 * a2) - (edgeLat1 * b2);
                
//                System.out.println(a2 + " " + b2 + " " + c2);
                
                // Can be shown this is the solution for the point of intersection if the lines aren't parallel
                if ((a1 * b2) - (a2 * b1) != 0) {
                    var pointLng = ((b1 * c2) - (b2 * c1)) / ((a1 * b2) - (a2 * b1));
                    var pointLat = ((a2 * c1) - (a1 * c2)) / ((a1 * b2) - (a2 * b1));
                    
//                    System.out.println("This case!");
//                    System.out.println(pointLng + " " + pointLat);
//                    System.out.println(Point.fromLngLat(pointLng, pointLat).toJson());
                    
//                    System.out.println(pointLng >= Math.min(startLng, endLng) && pointLng <= Math.max(startLng, endLng));
//                    System.out.println(pointLat >= Math.min(startLat, endLat) && pointLat <= Math.max(startLat, endLat));
//                    System.out.println(pointLat >= Math.min(startLat, endLat));
//                    System.out.println(pointLat + " " + Math.min(startLat, endLat) + " " + (pointLat - Math.min(startLat, endLat)));
//                    System.out.println(pointLat <= Math.max(startLat, endLat));
//                    System.out.println(pointLng >= Math.min(edgeLng1, edgeLng2) && pointLng <= Math.max(edgeLng1, edgeLng2));
//                    System.out.println(pointLat >= Math.min(edgeLat1, edgeLat2) && pointLat <= Math.max(edgeLat1, edgeLat2));
                    
                    // Check if the intersect point lies on both line segments. If so the move isn't legal
                    if (pointLng + floatOffset >= Math.min(startLng, endLng) && pointLng - floatOffset <= Math.max(startLng, endLng) &&
                            pointLat + floatOffset >= Math.min(startLat, endLat) && pointLat - floatOffset <= Math.max(startLat, endLat) &&
                            pointLng + floatOffset >= Math.min(edgeLng1, edgeLng2) && pointLng - floatOffset <= Math.max(edgeLng1, edgeLng2) &&
                            pointLat + floatOffset >= Math.min(edgeLat1, edgeLat2) && pointLat - floatOffset <= Math.max(edgeLat1, edgeLat2)) {
                        return false;
                    }
                } else if ((b2 * c1) - (b1 * c2) == 0 && (a2 * c1) - (a1 * c2) == 0 &&
                        ((edgeLng1 + floatOffset >= Math.min(startLng, endLng) && edgeLng1 - floatOffset <= Math.max(startLng, endLng) && 
                          edgeLat1 + floatOffset >= Math.min(startLat, endLat) && edgeLat1 - floatOffset <= Math.max(startLat, endLat)) ||
                        ( edgeLng2 + floatOffset >= Math.min(startLng, endLng) && edgeLng2 - floatOffset <= Math.max(startLng, endLng) && 
                          edgeLat2 + floatOffset >= Math.min(startLat, endLat) && edgeLat2 - floatOffset <= Math.max(startLat, endLat)))) {
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













