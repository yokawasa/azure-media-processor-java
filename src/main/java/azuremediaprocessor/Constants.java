package azuremediaprocessor;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;

public class Constants {
    public static final String MEDIA_SERVICE_URI = "https://media.windows.net/API/";
    public static final String OAUTH_URI = "https://wamsprodglobal001acs.accesscontrol.windows.net/v2/OAuth2-13";
    public static final String URN = "urn:WindowsAzureMediaServices";
    public static final Map<String, String> MediaProcessorType_MAP;
        static {
            HashMap<String,String> m = new HashMap<String,String>();
            m.put("10", "Azure Media Indexer");
            m.put("11", "Azure Media Indexer 2 Preview");
            m.put("12", "Azure Media Hyperlapse");
            m.put("13", "Azure Media Face Detector");
            m.put("14", "Azure Media Motion Detector");
            m.put("15", "Azure Media Stabilizer");
            m.put("16", "Azure Media Video Thumbnails");
            MediaProcessorType_MAP = Collections.unmodifiableMap(m); 
        }
    public Constants() { }
}
