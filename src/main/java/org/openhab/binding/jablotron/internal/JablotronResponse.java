package org.openhab.binding.jablotron.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;

/**
 * Created by cen26597 on 23.3.2017.
 */
public class JablotronResponse {
    private Exception exception = null;
    private int responseCode = 0;
    private JsonObject json = null;
    private String response = null;
    private String cookie = null;

    private static final Logger logger =
            LoggerFactory.getLogger(JablotronResponse.class);

    //Gson parser
    private JsonParser parser = new JsonParser();

    public JablotronResponse(Exception exception) {
        this.exception = exception;
    }

    public JablotronResponse(HttpsURLConnection connection) {
        try {
            this.responseCode = connection.getResponseCode();
            this.response = readResponse(connection);
            this.cookie = getSessionCookie(connection);
            logger.debug(response);
            json = parser.parse(response).getAsJsonObject();
        } catch (Exception ex) {
            this.exception = ex;
        }

    }

    public Exception getException() {
        return exception;
    }

    public int getResponseCode() {
        return responseCode;
    }

    public JsonObject getJson() {
        return json;
    }

    public String getResponse() {
        return response;
    }

    public String getCookie() {
        return cookie;
    }

    public boolean isOKStatus() {
        return (json != null && json.has("status")) ? json.get("status").getAsBoolean() : false;
    }

    public String getErrorStatus() {
        return (json != null && json.has("error_status")) ? json.get("error_status").getAsString() : "";
    }

    public boolean hasReport() {
        return json != null && json.has("vypis") && !json.get("vypis").isJsonNull();
    }

    private String readResponse(HttpsURLConnection connection) throws Exception {
        InputStream stream = connection.getInputStream();
        String line;
        StringBuilder body = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        while ((line = reader.readLine()) != null) {
            body.append(line).append("\n");
        }
        line = body.toString();
        logger.debug(line);
        return line;
    }

    private String getSessionCookie(HttpsURLConnection connection) {

        String headerName;
        for (int i = 1; (headerName = connection.getHeaderFieldKey(i)) != null; i++) {
            if (headerName.equals("Set-Cookie")) {
                if (connection.getHeaderField(i).startsWith("PHPSESSID")) {
                    int semicolon = connection.getHeaderField(i).indexOf(";");
                    String cookie = connection.getHeaderField(i).substring(0, semicolon);
                    return cookie;
                }
            }
        }
        return "";
    }

    public boolean hasSectionStatus() {
        return json != null && json.has("sekce") && json.has("pgm");
    }

    public boolean isControlDisabled() {
        return (json != null && json.has("controlDisabled")) ? json.get("controlDisabled").getAsBoolean() : true;
    }

    private int getState(JsonArray jarray, int pos) {
        if (jarray != null && jarray.size() > pos && jarray.get(pos).isJsonObject() && jarray.get(pos).getAsJsonObject().has("segment_state")) {
            return jarray.get(pos).getAsJsonObject().get("segment_state").getAsString().equals("set") ? 1 : 0;
        } else
            return -1;
    }

    public int getStateByType(int i, String type) {
        if (json.has("data") && json.get("data").getAsJsonObject().has("service_data")) {
            JsonObject jo = json.get("data").getAsJsonObject().get("service_data").getAsJsonArray().get(0).getAsJsonObject();
            JsonArray jarray = jo.get("data").getAsJsonArray();
            for (int j = 0; j < jarray.size(); j++) {
                jo = jarray.get(j).getAsJsonObject();
                if (jo.has("data_type") && jo.get("data_type").getAsString().equals(type)) {
                    JsonArray segments = jo.get("data").getAsJsonObject().get("segments").getAsJsonArray();
                    return getState(segments, i);
                }
            }
            return 0;
        } else
            return 0;
    }

    public int getSectionState(int i) {
        return getStateByType(i, "section");
    }

    public int getPGState(int i) {
        return getStateByType(i, "pgm");
    }

    public long getSectionTime(int i) {
        if (json.has("data") && json.get("data").getAsJsonObject().has("service_data")) {
            JsonObject jo = json.get("data").getAsJsonObject().get("service_data").getAsJsonArray().get(0).getAsJsonObject();
            JsonArray jarray = jo.get("data").getAsJsonArray();
            for (int j = 0; j < jarray.size(); j++) {
                jo = jarray.get(j).getAsJsonObject();
                if (jo.has("data_type") && jo.get("data_type").getAsString().equals("pgm")) {
                    JsonArray segments = jo.get("data").getAsJsonObject().get("segments").getAsJsonArray();
                    return segments.get(0).getAsJsonObject().get("segment_last_change").getAsLong();
                }
            }
            return 0;
        } else
            return 0;
    }

    public Date getLastResponseTime() {
        long lastEventTime = getSectionTime(0);
        return getZonedDateTime(lastEventTime);
    }

    private Date getZonedDateTime(long lastEventTime) {
        Instant dt = Instant.ofEpochSecond(lastEventTime);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(dt, ZoneId.of("Europe/Prague"));
        return Date.from(zdt.toInstant());
    }

    public int getServicesCount() {

        if (json.has("services")) {
            JsonArray array = json.get("services").getAsJsonArray();
            return array.size();
        } else return 0;
    }

    public String getServiceId(int id) {
        if (json.has("services")) {
            JsonArray services = json.get("services").getAsJsonArray();
            JsonObject jo = services.get(id).getAsJsonObject();
            return String.valueOf(jo.get("id").getAsInt());
        }
        return "";
    }

    public String getServiceName(int id) {
        if (json.has("services") && json.get("services").getAsJsonArray().size() > id) {
            JsonArray widget = json.get("services").getAsJsonArray();
            JsonObject jobject = widget.get(id).getAsJsonObject();
            return jobject.has("name") ? jobject.get("name").getAsString().toLowerCase() : "";
        }
        return "";
    }

    public void getReport() {
        if (!hasReport()) {
            return;
        }

        JsonObject jObject = json.get("vypis").getAsJsonObject();
        for (Map.Entry<String, JsonElement> entry : jObject.entrySet()) {
            String key = entry.getKey();
            if (jObject.get(key) instanceof JsonObject) {
                //each day
                JsonObject event = jObject.get(key).getAsJsonObject();
                for (Map.Entry<String, JsonElement> eventEntry : event.entrySet()) {
                    String eventKey = eventEntry.getKey();
                    if (event.get(eventKey) instanceof JsonObject) {
                        JsonObject eventData = event.get(eventKey).getAsJsonObject();
                        logger.info("Time: " + eventKey + " code: " + eventData.get("code").getAsString() + " event: " + eventData.get("event").getAsString());
                    }
                }

            }
        }
    }

}
