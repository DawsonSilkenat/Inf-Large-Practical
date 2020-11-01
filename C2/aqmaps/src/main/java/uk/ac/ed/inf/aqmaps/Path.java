package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;

import com.mapbox.geojson.Point;

public class Path implements Comparable<Path> {
//    private double endLng;
//    private double endLat;
    private double goalLng;
    private double goalLat;
    private List<Integer> moves;
    private List<Point> positions;
    private double cost;
    private double heuristic;
    
    public Path(double startLng, double startLat, double goalLng, double goalLat) {
//        endLng = startLng;
//        endLat = startLat;
        this.goalLng = goalLng;
        this.goalLat = goalLat;
        moves = new ArrayList<Integer>();
        positions = new ArrayList<Point>();
        positions.add(Point.fromLngLat(startLng, startLat));
        
        cost = 0;
        heuristic = findDistance(startLng, startLat, goalLng, goalLat);
    }
    
    private Path(int angle, double distance, Path previous) {
//        endLng = previous.endLng + Math.cos(angle * Math.PI / 180.0) * distance;
//        endLat = previous.endLat + Math.sin(angle * Math.PI / 180.0) * distance;
        var endLng = previous.getEndLng() + Math.cos(angle * Math.PI / 180.0) * distance;
        var endLat = previous.getEndLat() + Math.sin(angle * Math.PI / 180.0) * distance;
        goalLng = previous.goalLng;
        goalLat = previous.goalLat;
        
        moves = new ArrayList<Integer>();
        moves.addAll(previous.moves);
        moves.add(angle);
        
        positions = new ArrayList<Point>();
        positions.addAll(previous.positions);
        positions.add(Point.fromLngLat(endLng, endLat));
        
        cost = previous.cost + distance;
        heuristic = findDistance(endLng, endLat, goalLng, goalLat);
    }
    
    public double getEndLng() {
//        return endLng;
        return positions.get(positions.size() - 1).longitude();
    }
    
    public double getEndLat() {
//        return endLat;
        return positions.get(positions.size() - 1).latitude();
    }
    
    public List<Integer> getMoves() {
        return moves;
    }
    
    public List<Point> getPositions() {
        return positions;
    }

    public double getHeuristic() {
        return heuristic;
    } 
    
    public Path extend(int angle, double distance) {
        return new Path(angle, distance, this);
    }
    
    private double findDistance(double startLng, double startLat, double endLng, double endLat) {
        return Math.sqrt(Math.pow(startLng - endLng, 2) + Math.pow(startLat - endLat, 2));
    }

    @Override
    public int compareTo(Path path) {
        if (this.cost + this.heuristic - (path.cost + path.heuristic)  < 0) {
            return -1;
        } else if (this.cost + this.heuristic - (path.cost + path.heuristic)  > 0) {
            return 1;
        }
        return 0;
    }
    
}
