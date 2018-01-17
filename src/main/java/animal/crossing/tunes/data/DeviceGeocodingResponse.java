package animal.crossing.tunes.data;

import java.util.List;

public class DeviceGeocodingResponse implements GoogleData {
    public List<Result> results;
    public String status;
    public String error_message;

    public static class Result {
        public List<String> types;
        public String formatted_address;
        public List<AddressComponents> address_components;
        public List<String> postcode_localities;
        public Geometry geometry;
        public String partial_match;
        public String place_id;
    }

    public static class AddressComponents {
        public String long_name;
        public String short_name;
        public List<String> types;
    }

    public static class Geometry {
        public Location location;
        public String location_type;
        public ViewPort viewport;
        public Bounds bounds;
    }

    public static class Location {
        public String lat;
        public String lng;
    }

    public static class ViewPort {
        public NorthEast northeast;
        public SouthWest southwest;
    }

    public static class Bounds {
        public NorthEast northeast;
        public SouthWest southwest;
    }

    public static class NorthEast {
        public String lat;
        public String lng;
    }

    public static class SouthWest {
        public String lat;
        public String lng;
    }
}




