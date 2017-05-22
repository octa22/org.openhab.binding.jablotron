package org.openhab.binding.jablotron.internal;

/**
 * Created by Ondřej Pečta on 28. 3. 2017.
 */
public enum JablotronCode {
    //1138 Nepotvrzeny poplach
    //1130 Alarm v okamzite smycce
    //1301 Vypadek sitoveho napajeni
    //1401 Odjisteno
    //3301 Obnoveni sitoveho napajeni
    //3401 Zajisteno cele kodem
    //3402 Zajisteno castecne kodem
    //3408 Zajisteno plne klavesnici (bez kodu)
    UNCONFIRMED_ALARM(1138),
    IMMEDIATE_ALARM(1130),
    POWER_FAILURE(1301),
    SERVICE_ENTER(1306),
    DISARMED(1401),
    TIME_RESET(1625),
    POWER_RESTORATION(3301),
    SERVICE_LEAVE(3306),
    ARM_FULL(3401),
    ARM_PARTIAL(3402),
    ARM_FULL_KEYBOARD(3408);

    private int code;

    JablotronCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
