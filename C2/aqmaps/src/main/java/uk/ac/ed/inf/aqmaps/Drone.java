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
    private double minLongitude;
    private double maxLongitude;
    private double minLatitude;
    private double maxLatitude;
    
    public Drone(Point startPosition, double moveDistance, double readDistance, double endingDistance, int maxMoves,
            double minLongitude, double maxLongitude, double minLatitude, double maxLatitude) {
        this.startPosition = startPosition;
        this.moveDistance = moveDistance;
        this.readDistance = readDistance;
        this.endingDistance = endingDistance;
        this.maxMoves = maxMoves;
        visitPaths = new ArrayList<>();
        visitOrder = new ArrayList<>();
        this.minLongitude = minLongitude;
        this.maxLongitude = maxLongitude;
        this.minLatitude = minLatitude;
        this.maxLatitude = maxLatitude;
    }
    
    public void visitSensors(List<Sensor> sensors, List<Polygon> noFlyZones) {
        var moveEstimates = getMoveEstimates(sensors, noFlyZones);
        visitOrder = selectVistOrder(sensors, moveEstimates);
        visitPaths = new ArrayList<Path>();
        
        var updatedOrder = true;
        while (updatedOrder) {
            updatedOrder = false;
            
            var currentPosition = startPosition;
            if (visitPaths.size() > 0) {
                currentPosition = visitPaths.get(visitPaths.size() - 1).getEndPosition();
            }
            
            
            // TODO update this to work when the visit order doesn't start from 0
            var startIndex = visitPaths.size();
            for (int i = startIndex; i < visitOrder.size(); i++) {
                var sensor = visitOrder.get(i);
                var path = findPath(currentPosition, sensor.getPosition(), readDistance, 1, noFlyZones);
                if (path != null) {
                    currentPosition = path.getEndPosition();
                    visitPaths.add(path);
                } else {
                    visitOrder.remove(i);
                    i--;
                }
            }   
            // Always possible to return to start, so no need to check for null
            visitPaths.add(findPath(currentPosition, startPosition, endingDistance, 0, noFlyZones));
            
            var numberOfMoves = 0;
            for (Path path : visitPaths) {
                numberOfMoves += path.getPositions().size();
            }
            
            // We remove sensors from our visit list in a greedy manner to reduce the number of moves below maximum
            if (numberOfMoves > maxMoves) {
                updatedOrder = true;
                
                if (visitOrder.size() <= 1) {
                    visitOrder = new ArrayList<>();
                } else {
                    var droneIndex = moveEstimates.length - 1;
                    var bestDropIndex = 0;
                    var bestDropGain = visitPaths.get(0).getPositions().size() + visitPaths.get(1).getPositions().size() 
                            - moveEstimates[droneIndex][sensors.indexOf(visitOrder.get(1))];
                    
                    for (int i = 1; i < visitOrder.size() - 1; i++) {
                        var thisDropGain = visitPaths.get(i).getPositions().size() + visitPaths.get(i + 1).getPositions().size() 
                                - moveEstimates[sensors.indexOf(visitOrder.get(i - 1))][sensors.indexOf(visitOrder.get(i + 1))];
                        
                        if (thisDropGain >= bestDropGain) {
                            bestDropGain = thisDropGain;
                            bestDropIndex = i;
                        }
                    }
                    
                    var thisDropGain = visitPaths.get(visitPaths.size() - 2).getPositions().size() +  visitPaths.get(visitPaths.size() - 1).getPositions().size() 
                            - moveEstimates[sensors.indexOf(visitOrder.get(visitOrder.size() - 2))][droneIndex];

                    if (thisDropGain > bestDropGain) {
                        bestDropIndex = visitPaths.size() - 2;
                    }
                    
                    visitOrder.remove(bestDropIndex);
                    visitPaths.subList(bestDropIndex, visitPaths.size()).clear();
                    
                    System.out.println(visitPaths.size() + " " + bestDropIndex + " " + bestDropGain);
                }

            }
        }
        
        for (Sensor sensor : visitOrder) {
            sensor.visit();
        }
    }

//    private int findBestReduction(List<Polygon> noFlyZones) {
//        // Only way to reduce is to visit no sensors
//        if (visitOrder.size() <= 1) {
//            return -1;
//        }
//        
//        var removeMoves = new int[visitOrder.size()];
//        removeMoves[0] = visitPaths.get(0).getPositions().size() + visitPaths.get(1).getPositions().size();
//        var alternativePath = findPath(startPosition, visitOrder.get(1).getPosition(), readDistance, 1, noFlyZones);
//        
//        if (alternativePath != null) {
//            removeMoves[0] -= alternativePath.getPositions().size();
//        } else {
//            removeMoves[0] = Integer.MIN_VALUE;
//        }
//        
//        for (int i = 1; i < removeMoves.length - 1; i++) {
//            removeMoves[i] = visitPaths.get(i).getPositions().size() + visitPaths.get(i + 1).getPositions().size();
//            alternativePath = findPath(visitOrder.get(i - 1).getPosition(), visitOrder.get(i + 1).getPosition(), readDistance, 1, noFlyZones);
//            if (alternativePath != null) {
//                removeMoves[i] -= alternativePath.getPositions().size();
//            } else {
//                removeMoves[i] = Integer.MIN_VALUE;
//            }                
//        }
//        
//        removeMoves[removeMoves.length - 1] = visitPaths.get(removeMoves.length - 1).getPositions().size() + 
//                visitPaths.get(removeMoves.length).getPositions().size();
//        alternativePath = findPath(visitOrder.get(removeMoves.length - 1).getPosition(), startPosition, endingDistance, 0, noFlyZones);
//        if (alternativePath != null) {
//            removeMoves[removeMoves.length - 1] -= alternativePath.getPositions().size();
//        } else {
//            removeMoves[removeMoves.length - 1] = Integer.MIN_VALUE;
//        }
//        
//        int bestRemove = 0;
//        for (int i = 1; i < removeMoves.length; i++) {
//            if (removeMoves[bestRemove] < removeMoves[i]) {
//                bestRemove = i;
//            }
//        }
//        
//        return bestRemove;
//    }
    
    private int[][] getMoveEstimates(List<Sensor> sensors, List<Polygon> noFlyZones) { 
        var estimates = new int[sensors.size() + 1][sensors.size() + 1];
        
        for (int i = 0; i < estimates.length - 2; i++) {
            for (int j = i + 1; j < estimates.length - 1; j++) {
                var path = findPath(sensors.get(i).getPosition(), sensors.get(j).getPosition(), readDistance, 1, noFlyZones);
                if (path == null) {
                    estimates[i][j] = Integer.MAX_VALUE / 10;
                } else {
                    estimates[i][j] = path.getPositions().size();
                }
                estimates[j][i] = estimates[i][j];
            }
        }
        
        for (int i = 0; i < estimates.length - 1; i++) {
            var path = findPath(startPosition, sensors.get(i).getPosition(), readDistance, 1, noFlyZones);
            if (path == null) {
                estimates[i][estimates.length - 1] = Integer.MAX_VALUE / 10;
            } else {
                estimates[i][estimates.length - 1] = path.getPositions().size();
            }
            estimates[estimates.length - 1][i] = estimates[i][estimates.length - 1];
        }
        
        return estimates;
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
      
    /*
     * Solve the travelling salesperson problem using straight line distance between sensors (plus start point)
     * This visit order becomes our estimate of the optimal order to visit the sensors
     * Going to start with 2-opt method, might change later. 
     */
    private List<Sensor> selectVistOrder(List<Sensor> sensors, int[][] distanceEstimates) { 
        var order = new ArrayList<Sensor>();
//        var distances = new int[sensors.size() + 1][sensors.size() + 1];
//        
//        for (int i = 0; i < distances.length - 2; i++) {
//            for (int j = i + 1; j < distances.length - 1; j++) {
//                var path = findPath(sensors.get(i).getPosition(), sensors.get(j).getPosition(), readDistance, 1, noFlyZones);
//                if (path == null) {
//                    distances[i][j] = Integer.MAX_VALUE / 10;
//                } else {
//                    distances[i][j] = path.getPositions().size();
//                }
//                distances[j][i] = distances[i][j];
//            }
//        }
//        
//        for (int i = 0; i < distances.length - 1; i++) {
//            var path = findPath(startPosition, sensors.get(i).getPosition(), readDistance, 1, noFlyZones);
//            if (path == null) {
//                distances[i][distances.length - 1] = Integer.MAX_VALUE / 10;
//            } else {
//                distances[i][distances.length - 1] = path.getPositions().size();
//            }
//            distances[distances.length - 1][i] = distances[i][distances.length - 1];
//        }
        
        var indices = twoOpt(distanceEstimates);
        int droneIndex = 0;
        for (int i = 0; i < indices.length; i++) {
            if (indices[i] == distanceEstimates.length - 1) {
                droneIndex = i;
                break;
            }
        }
        
        for (int i = 1; i < indices.length; i++) {
            order.add(sensors.get(indices[(droneIndex + i) % indices.length]));
        }
        
        return order;
    }
    
    

    private Path findPath(Point start, Point goal, double acceptableError, int minMoves, List<Polygon> noFlyZones) {
        var beamWidth = 250;
        var comparator = new Comparator<Path>() {
            @Override
            public int compare(Path path1, Path path2) {
                var cost1 = path1.getPositions().size();
                var distance1 = getDistance(path1.getEndPosition(), goal) - acceptableError;
                var heuristic1 = (int) Math.ceil(distance1 / moveDistance);
                
                var cost2 = path2.getPositions().size();
                var distance2 = getDistance(path2.getEndPosition(), goal) - acceptableError;
                var heuristic2 = (int) Math.ceil(distance2 / moveDistance);
                
                if (cost1 + heuristic1 < cost2 + heuristic2) {
                    return -1;
                } else if (cost1 + heuristic1 < cost2 + heuristic2) {
                    return 1;
                } else if (distance1 < distance2) {
                    return -1;
                } else if (distance1 > distance2) {
                    return 1;
                }
                return 0;
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
    
    // Checks whether the straight line move from the first pair of coordinates to the second is legal
    private boolean checkMoveLegality(Point start, Point end, List<Polygon> noFlyZones) {
        var startLng = start.longitude();
        var startLat = start.latitude();
        var endLng = end.longitude();
        var endLat = end.latitude();
        
        if (endLng < minLongitude || endLng > maxLongitude || endLat < minLatitude || endLat > maxLatitude) {
            return false;
        }
        
        // Note that we are using the equation ax + by + c = 0 as either a or b could be 0. x axis is longitude, y latitude 
        var a1 = startLat - endLat;
        var b1 = endLng - startLng;
        var c1 = -(startLng * a1) - (startLat * b1); 
        
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
                
                
                // Can be shown this is the solution for the point of intersection if the lines aren't parallel
                if ((a1 * b2) - (a2 * b1) != 0) {
                    var pointLng = ((b1 * c2) - (b2 * c1)) / ((a1 * b2) - (a2 * b1));
                    var pointLat = ((a2 * c1) - (a1 * c2)) / ((a1 * b2) - (a2 * b1));
                    
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

    /*
     * We are using the 2-opt method to find a good solution to the travelling salesperson problem
     * It works by taking an initial choice of solution and improving it by reversing segments where doing so reduce 
     * the total length of the solution. It does this until it can't find any more which improve the solution.  
     */
    private int[] twoOpt(int[][] distances) {
        var order = new int[distances.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        
        var improvement = true;
        while (improvement) {
            improvement = false;
            for (int i = 0; i < order.length - 2; i++) {
                for (int j = i + 1; j < order.length - 1; j++) {
                    if (distances[order[(i - 1 + order.length) % order.length]][order[j]] + 
                            distances[order[i]][order[(j + 1) % order.length]] < 
                            distances[order[(i - 1 + order.length) % order.length]][order[i]] + 
                            distances[order[j]][order[(j + 1) % order.length]]) {
                        
                        improvement = true;
                        for (int i2 = i, j2 = j; i2 < j2; i2++, j2--) {
                            var temp = order[i2];
                            order[i2] = order[j2];
                            order[j2] = temp;
                        }
                    }
                }
            }  
        }
        
        return order;
    }
    
    private double getDistance(Point start, Point end) {     
        return Math.sqrt(Math.pow(start.longitude() - end.longitude(), 2) + Math.pow(start.latitude() - end.latitude(), 2));
    }   
}













