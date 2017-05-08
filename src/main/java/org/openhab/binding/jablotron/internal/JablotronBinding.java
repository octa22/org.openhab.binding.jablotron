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
import org.openhab.core.library.types.OpenClosedType;
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

    private final String JABLOTRON_URL = "https://www.jablonet.net/";
    private final String SERVICE_URL = "app/oasis?service=";
    private final String AGENT = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/54.0.2840.59 Safari/537.36";
    //private final int MAX_SESSION_CYCLE = 500;

    private String email = "";
    private String password = "";
    private String session = "";
    private String service;
    private boolean loggedIn = false;
    private ArrayList<String> cookies = new ArrayList<>();

    ArrayList<String> services = new ArrayList<>();

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
    private boolean inService = false;

    //cycle
    //private int cycle = randomWithRange(0, MAX_SESSION_CYCLE - 1);

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
    private long refreshInterval = 900000;

    public JablotronBinding() {
    }


    /**
     * Called by the SCR to activate the component with its configuration read from CAS
     *
     * @param bundleContext BundleContext of the Bundle that defines this component
     * @param configuration Configuration properties for this component obtained from the ConfigAdmin service
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
     * Called by the SCR when the configuration of a binding has been changed through the ConfigAdmin service.
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
        services.clear();
    }

    private void logout() {

        String url = JABLOTRON_URL + "logout";
        try {
            URL cookieUrl = new URL(url);

            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Referer", JABLOTRON_URL + SERVICE_URL + service);
            connection.setRequestProperty("Cookie", session);
            connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
            setConnectionDefaults(connection);

            JablotronResponse response = new JablotronResponse(connection);
        } catch (Exception e) {
            //Silence
            //logger.error(e.toString());
        } finally {
            loggedIn = false;
            controlDisabled = true;
            inService = false;
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
                updateAlarmStatus();
            }
        } catch (Exception ex) {
            logger.error(ex.toString());
        } finally {
            logout();
        }
    }

    private void updateAlarmStatus() {
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

        if (response.isNoSessionStatus()) {
            loggedIn = false;
            controlDisabled = true;
            inService = false;
            login();
            response = sendGetStatusRequest();
        }
        if (response.isBusyStatus()) {
            logger.warn("OASIS is busy...giving up");
            logout();
            return;
        }
        if (response.hasReport()) {
            response.getReport();
        }

        inService = response.inService();

        if (inService) {
            logger.warn("Alarm is in service mode...");
        }

        if (response.isOKStatus() && response.hasSectionStatus()) {
            readAlarmStatus(response);
        } else {
            logger.error("Cannot get alarm status!");
            logger.error(response.getResponse());
            loggedIn = false;
        }
    }

    private void readAlarmStatus(JablotronResponse response) {
        controlDisabled = response.isControlDisabled();

        stavA = response.getSectionState(0);
        stavB = response.getSectionState(1);
        stavABC = response.getSectionState(2);

        stavPGX = response.getPGState(0);
        stavPGY = response.getPGState(1);

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
                    case "alarm":
                        newState = (response.isAlarm()) ? OpenClosedType.OPEN : OpenClosedType.CLOSED;
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

    /*
    private int randomWithRange(int min, int max) {
        int range = (max - min) + 1;
        return (int) (Math.random() * range) + min;
    }*/


    private JablotronResponse sendGetStatusRequest() {

        String url = JABLOTRON_URL + "app/oasis/ajax/stav.php?" + getBrowserTimestamp();
        try {
            URL cookieUrl = new URL(url);

            synchronized (session) {
                HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Referer", JABLOTRON_URL + SERVICE_URL + service);
                connection.setRequestProperty("Cookie", session);
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                setConnectionDefaults(connection);

                return new JablotronResponse(connection);
            }

        } catch (Exception e) {
            logger.error(e.toString());
            return new JablotronResponse(e);
        }
    }

    private int sendUserCode(String code) {
        String url = null;

        try {
            url = JABLOTRON_URL + "app/oasis/ajax/ovladani.php";
            String urlParameters = "section=STATE&status=" + ((stavA == 1) ? "1" : "") + "&code=" + code;
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            URL cookieUrl = new URL(url);
            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();
            JablotronResponse response;

            synchronized (session) {
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Referer", JABLOTRON_URL + SERVICE_URL + service);
                connection.setRequestProperty("Cookie", session);
                connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                setConnectionDefaults(connection);
                try (DataOutputStream wr = new DataOutputStream(connection.getOutputStream())) {
                    wr.write(postData);
                }
                response = new JablotronResponse(connection);
            }
            logger.debug("Response: " + response.getResponse());
            int result = response.getJablotronResult();
            if (result != 1) {
                logger.error("Received error result: " + result);
                logger.error(response.getJson().toString());
                return 0;
            }
            return response.getJablotronStatusCode();
        } catch (Exception ex) {
            logger.error(ex.toString());
        }
        return 0;
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

            url = JABLOTRON_URL + "ajax/login.php";
            String urlParameters = "login=" + email + "&heslo=" + password + "&aStatus=200&loginType=Login";
            byte[] postData = urlParameters.getBytes(StandardCharsets.UTF_8);

            URL cookieUrl = new URL(url);
            HttpsURLConnection connection = (HttpsURLConnection) cookieUrl.openConnection();

            synchronized (session) {
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Referer", JABLOTRON_URL);
                connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");

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

                //cloud request

                url = JABLOTRON_URL + "ajax/widget-new.php?" + getBrowserTimestamp();
                ;
                cookieUrl = new URL(url);
                connection = (HttpsURLConnection) cookieUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Referer", JABLOTRON_URL + "cloud");
                connection.setRequestProperty("Cookie", session);
                connection.setRequestProperty("X-Requested-With", "XMLHttpRequest");
                setConnectionDefaults(connection);

                //line = readResponse(connection);
                response = new JablotronResponse(connection);

                if (response.getException() != null) {
                    logger.error(response.getException().toString());
                    return;
                }

                if (response.getResponseCode() != 200 || !response.isOKStatus()) {
                    return;
                }

                if (response.getWidgetsCount() == 0) {
                    logger.error("Cannot found any jablotron device");
                    return;
                }
                service = response.getServiceId(0);

                //service request
                url = response.getServiceUrl(0);
                if (!services.contains(service)) {
                    services.add(service);
                    logger.info("Found Jablotron service: " + response.getServiceName(0) + " id: " + service);
                }
                cookieUrl = new URL(url);
                connection = (HttpsURLConnection) cookieUrl.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Referer", JABLOTRON_URL);
                connection.setRequestProperty("Cookie", session);
                connection.setRequestProperty("Upgrade-Insecure-Requests", "1");
                setConnectionDefaults(connection);

                loggedIn = (connection.getResponseCode() == 200);
                if (loggedIn) {
                    logger.debug("Successfully logged to Jablotron cloud!");
                } else {
                    logger.error("Cannot log in to Jablotron cloud!");
                }
            }

        } catch (MalformedURLException e) {
            logger.error("The URL '" + url + "' is malformed: " + e.toString());
        } catch (Exception e) {
            logger.error("Cannot get Jablotron login cookie: " + e.toString());
        }
    }

    private String getBrowserTimestamp() {
        return "_=" + System.currentTimeMillis();
    }

    private void setConnectionDefaults(HttpsURLConnection connection) {
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", AGENT);
        connection.setRequestProperty("Accept-Language", "cs-CZ");
        connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
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
        logger.debug("internalReceiveCommand({},{}) is called!", itemName, command);
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
            login();
            if (command.equals(OnOffType.ON)) {

                if (inService) {
                    logger.error("Alarm is in service mode, cannot send user code!");
                    return;
                }
                while (controlDisabled) {
                    logger.debug("Waiting for control enabling...");
                    Thread.sleep(1000);
                    updateAlarmStatus();
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
        } finally {
            logout();
        }

    }

    private void handleHttpRequestStatus(int status) throws InterruptedException {
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
                Thread.sleep(5000);
                updateAlarmStatus();
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
