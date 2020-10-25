package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.Point;

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
    
    
    public void visitSensors(List<Sensor> sensors, List<Feature> noFlyZones) {
        var visitOrder = this.selectVistOrder(sensors);
        // The ith element of moves is an array containing the angle of each move in order to visit the ith element of visit order
        var moves = new ArrayList<int[]>();
        
        double longitude = this.longitude;
        double latitude = this.latitude;
        
        int[] test = findPath(longitude, latitude, longitude + 3 * this.moveDistance, latitude);
        System.out.println(test[0] + " " + test[1] + " " + test[2]);
    }
    
    
    private int[] findPath(double startLongitude, double startLatitude, double endLongitude, double endLatitude) {
        double changeInLng = endLongitude - startLongitude;
        double changeInLat = endLatitude - endLatitude;
        // TODO Probably should add the distanceBetween function to drone and just call that here
        double distance = Math.sqrt(Math.pow(changeInLat, 2) + Math.pow(changeInLat, 2));
        
        // atan2 calculates the angle for us, but in radians including negative angles 
        // We first convert to degrees, then convert negative angles to the correct (for our purposes) positive angle.
        double angle = Math.atan2(changeInLat, changeInLng) * 180 / Math.PI;
        if (angle < 0) {
            angle += 360;
        }
        
        
        // TODO Should work when the drone can't fly along the specified angle, not sure what happens when it can
        
        // The drone cannot necessarily fly along the straight line to its destination, but we can easily construct a triangle
        // one side of which is that straight line and the other two sides are lines the drone can fly along
//        int angle1 = ((int) angle / this.angleStepSize) * this.angleStepSize;
        
        // The return array, where the first element is the smaller angle being travelled along, 
        // second the number of moves along this angle, and third the number of moves along the larger angle
        int[] returnValue = new int[3];
        returnValue[0] = ((int) angle / this.angleStepSize) * this.angleStepSize;
        
        returnValue[1] = (int) ((distance * Math.sin((angle - (double) returnValue[0]) 
                * Math.PI / 180) / Math.sin(170 * Math.PI / 180)) / this.moveDistance);
        
        returnValue[2] = (int) ((distance * Math.sin((10 + angle - (double) returnValue[0]) 
                * Math.PI / 180) / Math.sin(170 * Math.PI / 180)) / this.moveDistance);
        
        return returnValue;
    }
    
    /*
     * Solve the travelling salesperson problem using straight line distance between sensors (plus start point)
     * This visit order becomes our estimate of the optimal order to visit the sensors
     * Going to start with 2-opt method, might change later. 
     */
    public List<Sensor> selectVistOrder(List<Sensor> sensors) {
        List<Sensor> order = new ArrayList<Sensor>();
        // Initialising a distances array so we don't recompute the distance each time it is needed
        // We consider our last location to be the start point of the drone
        var distances = new double[sensors.size() + 1][sensors.size() + 1];
        
        for (int i = 0; i < distances.length - 1; i++) {
            distances[i][distances.length - 1] = sensors.get(i).getDistanceTo(this.longitude, this.latitude);
            distances[distances.length - 1][i] = distances[i][distances.length - 1];
        }
        for (int i = 0; i < distances.length - 2; i++) {
            for (int j = i + 1; j < distances.length - 1; j++) {
                distances[i][j] = sensors.get(i).getDistanceTo(
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
                            int temp = indices[i2];
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
    
    
    
    
    
}
