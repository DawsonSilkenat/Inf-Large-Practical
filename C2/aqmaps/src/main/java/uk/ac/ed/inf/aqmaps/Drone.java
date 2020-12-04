package uk.ac.ed.inf.aqmaps;

import java.util.List;
import java.util.ArrayList;
import java.util.PriorityQueue;
import java.util.Comparator;

import com.mapbox.geojson.Point;

public class Drone {
    
    // It is assumed this value will always be divide 360. 
    // Changing it may result in some methods not functioning as expected
    private final int angleStepSize = 10;
    
    private List<Path> pathsList;
    private List<Sensor> visitedSensorsList;
    private Point startPosition;
    private double moveDistance;
    private double readDistance;
    private double endingDistance;
    private int maxMoves;
    private double minLongitude;
    private double maxLongitude;
    private double minLatitude;
    private double maxLatitude;
    
    public Drone(Point startPosition, double moveDistance, double readDistance, double endingDistance, int maxMoves, 
            double minLongitude, double maxLongitude, double minLatitude, double maxLatitude) {
        pathsList = new ArrayList<>();
        visitedSensorsList = new ArrayList<>();
        
        this.startPosition = startPosition;
        this.moveDistance = moveDistance;
        this.readDistance = readDistance;
        this.endingDistance = endingDistance;
        this.maxMoves = maxMoves;
        this.minLongitude = minLongitude;
        this.maxLongitude = maxLongitude;
        this.minLatitude = minLatitude;
        this.maxLatitude = maxLatitude;
    }
    
    public Point getStartPosition() {
        return startPosition;
    }

    public List<Path> getPathsList() {
        return pathsList;
    }

    public List<Sensor> getVisitedSensorsList() {
        return visitedSensorsList;
    }
    
    // Changes the state of the sensors the drone would visit on its current flight path
    public void updateSensors() {
        for (Sensor sensor : visitedSensorsList) {
            sensor.visit();
        }
    }

    public void findFlightPath(List<Sensor> sensors, List<NoFlyZone> noFlyZones) {
        // Initially we attempt to visit all sensors. If this takes us over the move limit, we later remove sensors
        visitedSensorsList = selectVistOrder(sensors, noFlyZones);
        pathsList = new ArrayList<Path>();
          
        var updatedOrder = true;
        while (updatedOrder) {
            updatedOrder = false;
            
            var currentPosition = startPosition;
            if (pathsList.size() > 0) {
                currentPosition = pathsList.get(pathsList.size() - 1).getEndPosition();
            }
            
            // Need this logic for when we are recomputing part of the flight path due to removing a sensor
            var startIndex = pathsList.size();
            for (int i = startIndex; i < visitedSensorsList.size(); i++) {
                var sensor = visitedSensorsList.get(i);
                var path = findPath(currentPosition, sensor.getPosition(), readDistance, 1, noFlyZones);
                
                if (path != null) {
                    // If we found a path, update the position of the drone and add the path to our list
                    currentPosition = path.getEndPosition();
                    pathsList.add(path);
                } else {
                    // Indicates the sensor is practically inaccessible and shouldn't be considered
                    visitedSensorsList.remove(i);
                    i--;
                }
            }   
            // Always needs to be possible to return to start, so no need to check for null
            pathsList.add(findPath(currentPosition, startPosition, endingDistance, 0, noFlyZones));
            
            var numberOfMoves = 0;
            for (Path path : pathsList) {
                numberOfMoves += path.getPositions().size();
            }
            
            // We remove sensors from our visit list one at a time in a greedy manner to reduce the number of moves below maximum
            if (numberOfMoves > maxMoves) {
                updatedOrder = true;
                var bestDropIndex = findVisitedSensorsReduction(noFlyZones);
                
                // Remove the selected sensor. We only need to recompute the paths from that sensor onward, all paths before are still valid 
                visitedSensorsList.remove(bestDropIndex);
                pathsList.subList(bestDropIndex, pathsList.size()).clear();
            }
        }
    }
    
    private List<Sensor> selectVistOrder(List<Sensor> sensors, List<NoFlyZone> noFlyZones) { 
        var moveEstimates = getMoveEstimates(sensors, noFlyZones);
        var visitOrder = new ArrayList<Sensor>();
        var orderedIndices = twoOpt(moveEstimates);
        
        // We want to reorder orderedIndices according to the location of the drone
        int droneIndex = 0;
        for (int i = 0; i < orderedIndices.length; i++) {
            if (orderedIndices[i] == moveEstimates.length - 1) {
                droneIndex = i;
                break;
            }
        }
        
        for (int i = 1; i < orderedIndices.length; i++) {
            visitOrder.add(sensors.get(orderedIndices[(droneIndex + i) % orderedIndices.length]));
        }
        
        return visitOrder;
    } 

    private int[][] getMoveEstimates(List<Sensor> sensors, List<NoFlyZone> noFlyZones) { 
        // Table of estimated number of moves to get from sensor i to sensor j, with the last row and column representing the drone
        var estimates = new int[sensors.size() + 1][sensors.size() + 1];
        
        for (int i = 0; i < estimates.length - 2; i++) {
            for (int j = i + 1; j < estimates.length - 1; j++) {
                var path = findPath(sensors.get(i).getPosition(), sensors.get(j).getPosition(), readDistance, 1, noFlyZones);
                
                if (path == null) {
                    // Divide by 10 since this may be used in sums and we want to avoid overflow
                    estimates[i][j] = Integer.MAX_VALUE / 10;
                } else {
                    estimates[i][j] = path.getPositions().size();
                }
                
                // Assumed paths are symmetric 
                estimates[j][i] = estimates[i][j];
            }
        }
        
        for (int i = 0; i < estimates.length - 1; i++) {
            // Use readDistance rather than endingDistance since this could be either going to the sensor or returning from
            var path = findPath(startPosition, sensors.get(i).getPosition(), readDistance, 1, noFlyZones);
            
            // Logic similar to above
            if (path == null) {
                estimates[i][estimates.length - 1] = Integer.MAX_VALUE / 10;
            } else {
                estimates[i][estimates.length - 1] = path.getPositions().size();
            }
            estimates[estimates.length - 1][i] = estimates[i][estimates.length - 1];
        }
        
        return estimates;
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
                    // Tells us reversing the segment from i to j reduces the length of the round trip
                    if (distances[order[(i - 1 + order.length) % order.length]][order[j]] + 
                            distances[order[i]][order[(j + 1) % order.length]] < 
                            distances[order[(i - 1 + order.length) % order.length]][order[i]] + 
                            distances[order[j]][order[(j + 1) % order.length]]) {
                        
                        improvement = true;
                        // Reverses segment from i to j
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
    
    // Calls findPath with default argument for beamwidth
    private Path findPath(Point start, Point goal, double acceptableError, int minMoves, List<NoFlyZone> noFlyZones) {
     // How wide a beam we use in the beam search. Larger values can improve the path found, but also reduce performance
     int beamWidth = 300;
     return findPath(start, goal, acceptableError, beamWidth, minMoves, noFlyZones);
    }
      
    /*
     * In order to find a path between two points, we would like to use A*. A heuristic is easy to find for this,
     * however we have a very high branching factor and due to the limits on the angles at which the drone can move
     * most moves will result in slight inefficiency so many short paths will be expanded. Over large distances
     * this can cause memory problems, so we decide to use beam search instead.
     */
    private Path findPath(Point start, Point goal, double acceptableError, int beamWidth, int minMoves, List<NoFlyZone> noFlyZones) {
        //The comparator used to determine which of two paths is better, a required argument for 
        //priority queue since path doesn't have a default comparator. 
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
            
            // If we have found a path which is better than all other paths in the search space which reaches the goal we are done
            if (getDistance(currentPath.getEndPosition(), goal) < acceptableError && currentPath.getPositions().size() >= minMoves) {  
                return currentPath;
            }
            
            // Otherwise update the search space
            for (int angle = 0; angle < 360; angle += angleStepSize) {
                var extendedPath = currentPath.extend(angle, moveDistance);
                
                if (checkMoveLegality(currentPath.getEndPosition(), extendedPath.getEndPosition(), noFlyZones) && 
                        extendedPath.getPositions().size() < maxMoves) {
                    searchSpace.add(extendedPath);
                } 
            }
            
            // This is a beam search so limit the search space to the best paths found so far
            var beam = new PriorityQueue<Path>(comparator);
            for (int i = 0; i < beamWidth && !searchSpace.isEmpty(); i++) {
                beam.add(searchSpace.poll());
            }
            searchSpace = beam;
        }
        
        // This shouldn't happen unless there isn't a path to the goal within the acceptable error, 
        // or such a path is too long to be worth considering
        return null; 
    } 
    
    /* 
     * Checks whether the straight line move from the first pair of position to the second is legal 
     * Assumes the starting point is legal.
     */
    private boolean checkMoveLegality(Point start, Point end, List<NoFlyZone> noFlyZones) {
        var endLng = end.longitude();
        var endLat = end.latitude();
        
        // Exits our confinement area, illegal 
        if (endLng < minLongitude || endLng > maxLongitude || endLat < minLatitude || endLat > maxLatitude) {
            return false;
        }
        
        // Cross a no fly zone, illegal 
        for (NoFlyZone noFlyZone : noFlyZones) {
            if (noFlyZone.isCrossedByMove(start, end)) {
                return false;
            }
        }
        
        // If we haven't found a reason for the move to be illegal, it is considered legal
        return true;
    }

    private double getDistance(Point start, Point end) {     
        return Math.sqrt(Math.pow(start.longitude() - end.longitude(), 2) + Math.pow(start.latitude() - end.latitude(), 2));
    }

    /*
     * Reduces the number of visited sensors by one, selecting the choice which is believe to reduce
     * the number of required moves for the flight path by the most. Removes the paths which are no 
     * longer valid due to not visiting that sensor.
     * Does NOT compute the newly required paths
     */
    private int findVisitedSensorsReduction(List<NoFlyZone> noFlyZones) {
        if (visitedSensorsList.size() <= 1) {
            return 0;
        } else {
            // Index of the sensor we will no longer visit
            var bestDropIndex = 0;
            // The estimated cost of the new path that will be required
            var replacementPathCost = findPath(startPosition, visitedSensorsList.get(0).getPosition(), readDistance, 1, noFlyZones).getPositions().size();
            // How many moves we expect to save if we were to not visit the sensor at bestDropIndex
            var bestDropSavings = pathsList.get(0).getPositions().size() + pathsList.get(1).getPositions().size()- replacementPathCost;
                    
            
            // Iterate over all sensors currently being visited to find the one which not visiting maximised the number of moves saved
            for (int i = 1; i < visitedSensorsList.size() - 1; i++) {
                replacementPathCost = findPath(visitedSensorsList.get(i - 1).getPosition(), visitedSensorsList.get(i + 1).getPosition(), readDistance, 1, noFlyZones).getPositions().size();
                var thisDropSavings = pathsList.get(i).getPositions().size() + pathsList.get(i + 1).getPositions().size() - replacementPathCost;
                
                if (thisDropSavings >= bestDropSavings) {
                    bestDropSavings = thisDropSavings;
                    bestDropIndex = i;
                }
            }
            
            // Last sensor needs to be treated differently due to the next location being the drones starting location rather than a sensor
            replacementPathCost = findPath(visitedSensorsList.get(visitedSensorsList.size() - 1).getPosition(), startPosition, endingDistance, 0, noFlyZones).getPositions().size();
            var thisDropSavings = pathsList.get(pathsList.size() - 2).getPositions().size() + pathsList.get(pathsList.size() - 1).getPositions().size() - replacementPathCost;
            
            if (thisDropSavings >= bestDropSavings) {
                bestDropIndex = pathsList.size() - 2;
            }
            
            return bestDropIndex;
        }
    }   
}

