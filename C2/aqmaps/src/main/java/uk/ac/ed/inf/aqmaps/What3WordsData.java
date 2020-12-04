package uk.ac.ed.inf.aqmaps;

public class What3WordsData {
    Coordinates coordinates;
    private static class Coordinates {
        private double lng;
        private double lat;
        
        public double getLng() {
            return lng;
        }

        public double getLat() {
            return lat;
        }
    }
    
    public double getLng() {
        return coordinates.getLng();
    }
    
    public double getLat() {
        return coordinates.getLat();
    }
}
