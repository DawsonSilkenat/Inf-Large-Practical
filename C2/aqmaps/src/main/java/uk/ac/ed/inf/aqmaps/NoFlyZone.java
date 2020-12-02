package uk.ac.ed.inf.aqmaps;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class NoFlyZone {
    
    Polygon boundary;
    
    public NoFlyZone(Polygon boundary) {
        this.boundary = boundary;
    }
    
    // Calls isCrossedByMove with a default float offset, which is used to correct floating point errors
    public boolean isCrossedByMove(Point start, Point end) {
        // Used to deal with floating point errors
        var floatOffset = 1e-10;
        return isCrossedByMove(start, end, floatOffset);
    }
    
    /*
     * Checks if the straight line move from start to end crosses one of the edges of the no fly zone. 
     * Note that this only works for crossing the edges of the no fly zone, in theory a move could start
     * and end in a no fly zone but cross no edges. This is not designed to handle that case. 
     */
    private boolean isCrossedByMove(Point start, Point end, double floatOffset) {
        // Get x,y coordinates of each point 
        var startLng = start.longitude();
        var startLat = start.latitude();
        var endLng = end.longitude();
        var endLat = end.latitude();
        
        // Find the equation of the line through the two given points
        // Note that we are using the equation ax + by + c = 0 as either a or b could be 0. x axis is longitude, y latitude 
        var a1 = startLat - endLat;
        var b1 = endLng - startLng;
        var c1 = -(startLng * a1) - (startLat * b1); 
        
        // List of points that make up the boundary of the polynomial
        // We will make use of the fact that the first and last elements are equal, which is specified by Polygon
        var boundaryPoints = boundary.outer().coordinates();
        for (int i = 0; i < boundaryPoints.size() - 1; i++) {
            
            // These are the x,y coordinates of a pair of points such that the line segment joining them is an edge of 
            // the boundary of the no fly zone. We find the equation of the line joining these two point
            var boundaryLng1 = boundaryPoints.get(i).longitude();
            var boundaryLat1 = boundaryPoints.get(i).latitude();
            var boundaryLng2 = boundaryPoints.get(i + 1).longitude();
            var boundaryLat2 = boundaryPoints.get(i + 1).latitude();

            
            var a2 = boundaryLat1 - boundaryLat2;
            var b2 = boundaryLng2 - boundaryLng1;
            var c2 = -(boundaryLng1 * a2) - (boundaryLat1 * b2);
            
            /*
             * This large block of logic first checks if the lines aren't parallel. If so, then we find the point of
             * intersection and check if it lies on both line segments. This would mean the move crosses the no fly zone.
             * If the lines are parallel, we need to check if they are in fact the same line and the segments overlap. If so
             * the move crosses the no fly zone.
             */
            if ((a1 * b2) - (a2 * b1) != 0) {
                var pointLng = ((b1 * c2) - (b2 * c1)) / ((a1 * b2) - (a2 * b1));
                var pointLat = ((a2 * c1) - (a1 * c2)) / ((a1 * b2) - (a2 * b1));
                
                if (pointLng + floatOffset >= Math.min(startLng, endLng) && pointLng - floatOffset <= Math.max(startLng, endLng) &&
                        pointLat + floatOffset >= Math.min(startLat, endLat) && pointLat - floatOffset <= Math.max(startLat, endLat) &&
                        pointLng + floatOffset >= Math.min(boundaryLng1, boundaryLng2) && pointLng - floatOffset <= Math.max(boundaryLng1, boundaryLng2) &&
                        pointLat + floatOffset >= Math.min(boundaryLat1, boundaryLat2) && pointLat - floatOffset <= Math.max(boundaryLat1, boundaryLat2)) {
                    return true;
                }
            } else if ((b2 * c1) - (b1 * c2) == 0 && (a2 * c1) - (a1 * c2) == 0 &&
                    ((boundaryLng1 + floatOffset >= Math.min(startLng, endLng) && boundaryLng1 - floatOffset <= Math.max(startLng, endLng) && 
                      boundaryLat1 + floatOffset >= Math.min(startLat, endLat) && boundaryLat1 - floatOffset <= Math.max(startLat, endLat)) ||
                    ( boundaryLng2 + floatOffset >= Math.min(startLng, endLng) && boundaryLng2 - floatOffset <= Math.max(startLng, endLng) && 
                      boundaryLat2 + floatOffset >= Math.min(startLat, endLat) && boundaryLat2 - floatOffset <= Math.max(startLat, endLat)))) {
                return true;
            }
        }
        
        // If we have not found that the move crosses one of the edges of the polygon representing the no fly zone, 
        // it doesn't cross the zone at all.
        return false;
    }

}
