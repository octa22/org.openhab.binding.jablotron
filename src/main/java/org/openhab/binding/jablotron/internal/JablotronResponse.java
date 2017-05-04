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

    public int getJablotronStatusCode() {
        return (json != null && json.has("status")) ? json.get("status").getAsInt() : 0;
    }

    public boolean isOKStatus() {
        return getJablotronStatusCode() == 200;
    }

    public boolean isNoSessionStatus() {
        return getJablotronStatusCode() == 800;
    }

    public boolean isBusyStatus() {
        return getJablotronStatusCode() == 201;
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

    public boolean inService() {
        return (json != null && json.has("service")) ? json.get("service").getAsInt() == 1 : false;
    }

    public boolean isAlarm() {
        return (json != null && json.has("isAlarm")) ? json.get("isAlarm").getAsInt() == 1 : false;
    }

    private int getState(JsonArray jarray, int pos) {
        if (jarray != null && jarray.size() > pos && jarray.get(pos).isJsonObject() && jarray.get(pos).getAsJsonObject().has("stav")) {
            return jarray.get(pos).getAsJsonObject().get("stav").getAsInt();
        } else
            return -1;
    }

    public int getSectionState(int i) {
        if (json.has("sekce")) {
            JsonArray jarray = json.get("sekce").getAsJsonArray();
            return getState(jarray, i);
        } else
            return 0;
    }

    public int getPGState(int i) {
        if (json.has("pgm")) {
            JsonArray jarray = json.get("pgm").getAsJsonArray();
            return getState(jarray, i);
        } else
            return 0;
    }

    public Date getLastResponseTime() {
        if (json.has("last_entry")) {
            long lastEventTime = json.get("last_entry").getAsJsonObject().get("cid").getAsJsonObject().get("time").getAsLong();
            return getZonedDateTime(lastEventTime);
        } else
            return null;
    }

    private Date getZonedDateTime(long lastEventTime) {
        Instant dt = Instant.ofEpochSecond(lastEventTime);
        ZonedDateTime zdt = ZonedDateTime.ofInstant(dt, ZoneId.of("Europe/Prague"));
        return Date.from(zdt.toInstant());
    }

    public int getWidgetsCount() {
        return json.has("cnt-widgets") ? json.get("cnt-widgets").getAsInt() : 0;
    }

    public String getServiceId(int id) {
        if (json.has("widgets")) {
            JsonArray widgets = json.get("widgets").getAsJsonArray();
            return String.valueOf(widgets.get(id).getAsInt());
        }
        return "";
    }

    public String getServiceUrl(int id) {
        if (json.has("widget") && json.get("widget").getAsJsonArray().size() > id) {
            JsonArray widget = json.get("widget").getAsJsonArray();
            JsonObject jobject = widget.get(id).getAsJsonObject();
            return jobject.has("url") ? jobject.get("url").getAsString() : "";
        }
        return "";
    }

    public String getServiceName(int id) {
        if (json.has("widget") && json.get("widget").getAsJsonArray().size() > id) {
            JsonArray widget = json.get("widget").getAsJsonArray();
            JsonObject jobject = widget.get(id).getAsJsonObject();
            return jobject.has("name") ? jobject.get("name").getAsString() : "";
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

    public int getJablotronResult() {
        return (responseCode == 200 && json != null && json.has("vysledek") && !json.get("vysledek").isJsonNull()) ? json.get("vysledek").getAsInt() : -1;
    }
}
