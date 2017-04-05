/**
 * Copyright (c) 2010-2015, openHAB.org and others.
 * <p>
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.jablotron.internal;

import org.apache.commons.lang.StringUtils;
import org.openhab.binding.jablotron.JablotronBindingProvider;
import org.openhab.core.binding.AbstractActiveBinding;
import org.openhab.core.items.ItemNotFoundException;
import org.openhab.core.items.ItemRegistry;
import org.openhab.core.library.types.DateTimeType;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.types.Command;
import org.openhab.core.types.State;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;


/**
 * Implement this class if you are going create an actively polling service
 * like querying a Website/Device.
 *
 * @author Ondrej Pecta
 * @since 1.9.0
 */
public class JablotronBinding extends AbstractActiveBinding<JablotronBindingProvider> {

    private static final Logger logger =
            LoggerFactory.getLogger(JablotronBinding.class);

    private final String JABLOTRON_URL = "https://api.jablonet.net/api/1.4/";

    private String email = "";
    private String password = "";
    private String session;
    private String serviceId;
    private String serviceName;
    private boolean loggedIn = false;
    private ArrayList<String> cookies = new ArrayList<>();

    //Section codes
    private String armACode = "";
    private String armBCode = "";
    private String armABCCode = "";
    private String disarmCode = "";

    //Section states
    private int stavA = 0;
    private int stavB = 0;
    private int stavABC = 0;
    private int stavPGX = 0;
    private int stavPGY = 0;
    private boolean controlDisabled = true;

    /**
     * The BundleContext. This is only valid when the bundle is ACTIVE. It is set in the activate()
     * method and must not be accessed anymore once the deactivate() method was called or before activate()
     * was called.
     */
    private BundleContext bundleContext;
    private ItemRegistry itemRegistry;

    /**
     * the refresh interval which is used to poll values from the Jablotron
     * server (optional, defaults to 60000ms)
     */
    private long refreshInterval = 8000;

    public JablotronBinding() {
    }


    /**
     * Called by the SCR to activate the component with its configuration read from CAS
     *
     * @param bundleContext BundleContext of the Bundle that defines this component
     * @param configuration Configuration properties for this component obtained from the ConfigAdmin serviceId
     */
    public void activate(final BundleContext bundleContext, final Map<String, Object> configuration) {
        this.bundleContext = bundleContext;

        // the configuration is guaranteed not to be null, because the component definition has the
        // configuration-policy set to require. If set to 'optional' then the configuration may be null

        readConfiguration(configuration);
        setProperlyConfigured(true);
    }

    private void readConfiguration(Map<String, Object> configuration) {
        String refreshIntervalString = (String) configuration.get("refresh");
        if (StringUtils.isNotBlank(refreshIntervalString)) {
            refreshInterval = Long.parseLong(refreshIntervalString);
        }

        String emailString = (String) configuration.get("email");
        if (StringUtils.isNotBlank(emailString)) {
            email = emailString;
        }

        String passwordString = (String) configuration.get("password");
        if (StringUtils.isNotBlank(passwordString)) {
            password = passwordString;
        }

        String armACodeString = (String) configuration.get("armACode");
        if (StringUtils.isNotBlank(armACodeString)) {
            armACode = armACodeString;
        }

        String armBCodeString = (String) configuration.get("armBCode");
        if (StringUtils.isNotBlank(armBCodeString)) {
            armBCode = armBCodeString;
        }

        String armABCCodeString = (String) configuration.get("armABCCode");
        if (StringUtils.isNotBlank(armABCCodeString)) {
            armABCCode = armABCCodeString;
        }

        String disarmCodeString = (String) configuration.get("disarmCode");
        if (StringUtils.isNotBlank(disarmCodeString)) {
            disarmCode = disarmCodeString;
        }
    }

    /**
     * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin serviceId.
     *
     * @param configuration Updated configuration properties
     */
    public void modified(final Map<String, Object> configuration) {
        // update the internal configuration accordingly
        readConfiguration(configuration);
    }

    /**
     * Called by the SCR to deactivate the component when either the configuration is removed or
     * mandatory references are no longer satisfied or the component has simply been stopped.
     *
     * @param reason Reason code for the deactivation:<br>
     *               <ul>
     *               <li> 0 – Unspecified
     *               <li> 1 – The component was disabled
     *               <li> 2 – A reference became unsatisfied
     *               <li> 3 – A configuration was changed
     *               <li> 4 – A configuration was deleted
     *               <li> 5 – The component was disposed
     *               <li> 6 – The bundle was stopped
     *               </ul>
     */
    public void deactivate(final int reason) {
        this.bundleContext = null;
        // deallocate resources here that are no longer needed and
        // should be reset when activating this binding again
        if (loggedIn) {
            logout();
        }
    }

    private void logout() {

        String url = JABLOTRON_URL + "logout.json";
        try {
            URL cookieUrl = new URL(url);

            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Cookie", session);
            setConnectionDefaults(connection);

            JablotronResponse response = new JablotronResponse(connection);
        } catch (Exception e) {
            //Silence
            //logger.error(e.toString());
        } finally {
            loggedIn = false;
            controlDisabled = true;
        }
        return;
    }

    public void setItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = itemRegistry;
    }

    public void unsetItemRegistry(ItemRegistry itemRegistry) {
        this.itemRegistry = null;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected long getRefreshInterval() {
        return refreshInterval;
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected String getName() {
        return "Jablotron Refresh Service";
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void execute() {
        // the frequently executed code (polling) goes here ...
        logger.debug("execute() method is called!");

        if (!bindingsExist()) {
            return;
        }

        try {
            if (!loggedIn) {
                login();
            }
            if (loggedIn) {
                JablotronResponse response = sendGetStatusRequest();
                if (response.getException() != null) {
                    logger.error(response.getException().toString());
                    loggedIn = false;
                    return;
                }
                logger.debug(response.getResponse());

                if (response.getResponseCode() != 200) {
                    logger.error("Cannot get alarm status, invalid response code: " + response.getResponseCode());
                    return;
                }

                if (response.isOKStatus()) {
                    readAlarmStatus(response);
                } else {
                    logger.error("Cannot get alarm status!");
                    loggedIn = false;
                }
            }
        } catch (Exception ex) {
            logger.error(ex.toString());
            loggedIn = false;
        }
    }

    private void readAlarmStatus(JablotronResponse response) {
        controlDisabled = false;//response.isControlDisabled();

        stavA = response.getSectionState(2);
        stavB = response.getSectionState(1);
        stavABC = response.getSectionState(0);

        stavPGX = response.getPGState(1);
        stavPGY = response.getPGState(0);

        for (final JablotronBindingProvider provider : providers) {
            for (final String itemName : provider.getItemNames()) {
                String type = getItemSection(itemName);
                State oldState;
                State newState;

                try {
                    oldState = itemRegistry.getItem(itemName).getState();
                } catch (ItemNotFoundException e) {
                    logger.error(e.toString());
                    oldState = null;
                }
                newState = oldState;
                switch (type) {
                    case "A":
                        newState = (stavA == 1) ? OnOffType.ON : OnOffType.OFF;
                        break;
                    case "B":
                        newState = (stavB == 1) ? OnOffType.ON : OnOffType.OFF;
                        break;
                    case "ABC":
                        newState = (stavABC == 1) ? OnOffType.ON : OnOffType.OFF;
                        break;
                    case "PGX":
                        newState = (stavPGX == 1) ? OnOffType.ON : OnOffType.OFF;
                        break;
                    case "PGY":
                        newState = (stavPGY == 1) ? OnOffType.ON : OnOffType.OFF;
                        break;
                    case "lasteventtime":
                        Date lastEvent = response.getLastResponseTime();
                        if (lastEvent != null) {
                            Calendar cal = Calendar.getInstance();
                            cal.setTime(lastEvent);
                            newState = new DateTimeType(cal);
                        }
                        break;
                }
                if (!newState.equals(oldState)) {
                    eventPublisher.postUpdate(itemName, newState);
                }
            }
        }
    }

    private JablotronResponse sendGetStatusRequest() {

        String url = JABLOTRON_URL + "dataUpdate.json";
        try {
            URL cookieUrl = new URL(url);
            String urlParameters = "data=[{ \"filter_data\":[{\"data_type\":\"section\"},{\"data_type\":\"pgm\"}],\"service_type\":\"" + serviceName + "\",\"service_id\":" + serviceId + ",\"data_group\":\"serviceData\"},{\"checksum\":\"1\",\"data_group\":\"peripheryData\"}]";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Cookie", session);
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
            setConnectionDefaults(connection);

            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }

            return new JablotronResponse(connection);

        } catch (Exception e) {
            logger.error(e.toString());
            return new JablotronResponse(e);
        }
    }

    private int sendUserCode(String code) {
        String url = null;

        try {
            url = JABLOTRON_URL + "controlSegment.json";
            String urlParameters = "service_type=" + serviceName + "&serviceId=" + serviceId + "&segmentId=STATE&segmentKey=&expected_status=partialSet&control_time=" + getControlTime() + "&control_code=" + code + "&system=Android&client_id=null";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            URL cookieUrl = new URL(url);
            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Cookie", session);
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
            setConnectionDefaults(connection);
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }

            JablotronResponse response = new JablotronResponse(connection);
            logger.debug("Response: " + response.getResponse());
            if (response.getException() != null) {
                logger.error(response.getException().toString());
                return 0;
            }
            if( !response.isOKStatus() ) {
                logger.error(response.getErrorStatus());
                return 800;
            }

            return response.getResponseCode();
        } catch (Exception ex) {
            logger.error(ex.toString());
        }
        return 0;
    }

    private String getControlTime() {
        return String.valueOf(System.currentTimeMillis() / 1000);
    }

    private void login() {
        String url = null;

        try {
            //login
            stavA = 0;
            stavB = 0;
            stavABC = 0;
            stavPGX = 0;
            stavPGY = 0;

            url = JABLOTRON_URL + "login.json";
            String urlParameters = "login=" + email + "&password=" + password + "&version=3.2.3&selected_lang=cs&selected_country=cz&system=Android";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            URL cookieUrl = new URL(url);
            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));

            setConnectionDefaults(connection);
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }

            JablotronResponse response = new JablotronResponse(connection);
            if (response.getException() != null) {
                logger.error(response.getException().toString());
                return;
            }

            if (!response.isOKStatus())
                return;

            //get cookie
            session = response.getCookie();

            logger.info("Getting serviceId info...");
            //serviceId info
            url = JABLOTRON_URL + "getServiceList.json";
            urlParameters = "visibility=default&list_type=extended&system=Android";
            postData = urlParameters.getBytes(StandardCharsets.UTF_8);
            cookieUrl = new URL(url);
            connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Cookie", session);

            setConnectionDefaults(connection);
            try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                wr.write(postData);
            }

            //line = readResponse(connection);
            response = new JablotronResponse(connection);

            if (response.getException() != null) {
                logger.error(response.getException().toString());
                return;
            }

            if (response.getResponseCode() != 200 || !response.isOKStatus()) {
                return;
            }

            if (response.getServicesCount() == 0) {
                logger.error("Cannot found any jablotron device");
                return;
            }

            serviceId = response.getServiceId(0);
            serviceName = response.getServiceName(0);

            loggedIn = true;
            logger.info("Found Jablotron serviceId: " + serviceName + " id: " + serviceId);
            logger.info("Successfully logged to Jablotron cloud!");

        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot get Jablotron login cookie: " + e.toString());
        }
    }

    private void setConnectionDefaults(HttpsURLConnection connection) {
        connection.setInstanceFollowRedirects(false);
        connection.setUseCaches(false);
    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveCommand(String itemName, Command command) {
        // the code being executed when a command was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        logger.info("internalReceiveCommand({},{}) is called!", itemName, command);
        if (!(command instanceof OnOffType)) {
            return;
        }

        String section = getItemSection(itemName);
        if (section.startsWith("PG")) {
            logger.error("Controlling of PGX/Y outputs is not supported!");
            return;
        }
        int status = 0;
        try {
            if (command.equals(OnOffType.ON)) {

                while (controlDisabled) {
                    logger.info("Waiting for control enabling...");
                    Thread.sleep(1000);
                }

                switch (section) {
                    case "A":
                        status = sendUserCode(armACode);
                        break;
                    case "B":
                        status = sendUserCode(armBCode);
                        break;
                    case "ABC":
                        status = sendUserCode(armABCCode);
                        break;
                    default:
                        logger.error("Received command for unknown section: " + section);
                }
            }

            if (command.equals(OnOffType.OFF)) {
                status = sendUserCode(disarmCode);
            }
            handleHttpRequestStatus(status);
        } catch (Exception e) {
            logger.error(e.toString());
            logout();
        }

    }

    private void handleHttpRequestStatus(int status) {
        switch (status) {
            case 0:
                logout();
                break;
            case 201:
                logout();
                break;
            case 300:
                logger.error("Redirect not supported");
                break;
            case 800:
                login();
                break;
            case 200:
                break;
            default:
                logger.error("Unknown status code received: " + status);
        }
    }

    private String getItemSection(String itemName) {
        for (final JablotronBindingProvider provider : providers) {
            if (provider.getItemNames().contains(itemName)) {
                return provider.getSection(itemName);
            }
        }
        return "";

    }

    /**
     * @{inheritDoc}
     */
    @Override
    protected void internalReceiveUpdate(String itemName, State newState) {
        // the code being executed when a state was sent on the openHAB
        // event bus goes here. This method is only called if one of the
        // BindingProviders provide a binding for the given 'itemName'.
        logger.debug("internalReceiveUpdate({},{}) is called!", itemName, newState);
    }

}
