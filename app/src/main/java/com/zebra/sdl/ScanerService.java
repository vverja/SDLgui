package com.zebra.sdl;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;

import android.os.Handler;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

import android.view.accessibility.AccessibilityEvent;


import com.zebra.adc.decoder.BarCodeReader;
import com.zebra.utils.BeepUtil;

public class ScanerService extends AccessibilityService implements
        BarCodeReader.DecodeCallback, BarCodeReader.ErrorCallback {
    private String decodeDataString;
    // BarCodeReader specifics
    private BarCodeReader bcr = null;

    static final int STATE_IDLE = 0;
    static final int STATE_DECODE = 1;
    static final int STATE_HANDSFREE = 2;
    static final int STATE_PREVIEW = 3; // snapshot preview mode
    static final int STATE_SNAPSHOT = 4;
    static final int STATE_VIDEO = 5;

    private int state = STATE_IDLE;

    static {
        System.loadLibrary("IAL");
        System.loadLibrary("SDL");

        if (android.os.Build.VERSION.SDK_INT >= 19)
            System.loadLibrary("barcodereader44"); // Android 4.4
        else if (android.os.Build.VERSION.SDK_INT >= 18)
            System.loadLibrary("barcodereader43"); // Android 4.3
        else
            System.loadLibrary("barcodereader"); // Android 2.3 - Android 4.2
    }
    final String LOG_TAG = "mLog";
    //Имя события, которое будет передаваться в 1С
    public static final String SEND_BARCODE = "ru.dewersia.barcodeDLL.TRUSTCONNECT";
    public Handler mHandler;
    public KeyCharacterMap chMap;
    private int trigMode = BarCodeReader.ParamVal.LEVEL;
    private static int decCount = 0;
    private int decodes = 0;
    static private boolean sigcapImage = true; // true = display signature
    private boolean beepMode = true; // decode beep enable

    @Override
    public void onCreate() {
        state = STATE_IDLE;

        try {

            if (android.os.Build.VERSION.SDK_INT >= 18)
                bcr = BarCodeReader.open(1, getApplicationContext()); // Android
                // 4.3 and
                // above
            else
                bcr = BarCodeReader.open(0); // Android 2.3

            if (bcr == null) {
                return;
            }

            bcr.setDecodeCallback(this);

            bcr.setErrorCallback(this);

            // Set parameter - Uncomment for QC/MTK platforms
            // bcr.setParameter(765, 0); // For QC/MTK platforms

            // Sample of how to setup OCR Related String Parameters
            // OCR Parameters
            // Enable OCR-B
            // bcr.setParameter(681, 1);

            // Set OCR templates
            // String OCRSubSetString = "01234567890"; // Only numeric
            // characters
            // String OCRSubSetString =
            // "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ!%"; // Only numeric
            // characters
            // Parameter # 686 - OCR Subset
            // bcr.setParameter(686, OCRSubSetString);

            // String OCRTemplate = "54R"; // The D ignores all characters after
            // the template
            // Parameter # 547 - OCR Template
            // bcr.setParameter(547, OCRTemplate);
            // Parameter # 689 - OCR Minimum characters
            // bcr.setParameter(689, 13);
            // Parameter # 690 - OCR Maximum characters
            // bcr.setParameter(690, 13);

            // Set Orientation
            // bcr.setParameter(687, 4); // 4 - omnidirectional

            // Sets OCR lines to decide
            // bcr.setParameter(691, 2); // 2 - OCR 2 lines

            // End of OCR Parameter Sample

            bcr.setParameter(765, 0); // MTK must be set
            bcr.setParameter(137, 0); // 可对同一码进行扫描
        } catch (Exception e) {
        }
    }

    @Override
    public void onServiceConnected() {

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {

    }

    @Override
    public void onInterrupt() {

    }

    public void onDecodeComplete(int symbology, int length, byte[] data,
                                 BarCodeReader reader) {
        if (state == STATE_DECODE)
            state = STATE_IDLE;

        // Get the decode count
        if (length == BarCodeReader.DECODE_STATUS_MULTI_DEC_COUNT)
            decCount = symbology;

        if (length > 0) {
                bcr.stopDecode();

            ++decodes;

           if (symbology == 0x99) // type 99?
                {
                    symbology = data[0];
                    int n = data[1];
                    int s = 2;
                    int d = 0;
                    int len = 0;
                    byte d99[] = new byte[data.length];
                    for (int i = 0; i < n; ++i) {
                        s += 2;
                        len = data[s++];
                        System.arraycopy(data, s, d99, d, len);
                        s += len;
                        d += len;
                    }
                    d99[d] = 0;
                    data = d99;
                }

                decodeDataString += new String(data);


                if (decCount > 1) // Add the next line only if multiple decode
                {

                    decodeDataString += new String(" ; ");
                } else {
                    decodeDataString = new String("");

                }
            }


                if (beepMode)
                    beep();
            }



        public void onEvent(int event, int info, byte[] data, BarCodeReader reader) {
        switch (event) {
            case BarCodeReader.BCRDR_EVENT_SCAN_MODE_CHANGED:

                break;

            case BarCodeReader.BCRDR_EVENT_MOTION_DETECTED:

                break;

            case BarCodeReader.BCRDR_EVENT_SCANNER_RESET:

                break;

            default:
                // process any other events here
                break;
        }
    }

    private int setIdle() {
        int prevState = state;
        int ret = prevState; // for states taking time to chg/end

        state = STATE_IDLE;
        switch (prevState) {
            case STATE_HANDSFREE:
                resetTrigger();
                // fall thru
            case STATE_DECODE:

                bcr.stopDecode();
                break;

            case STATE_VIDEO:
                bcr.stopPreview();
                break;

            case STATE_SNAPSHOT:
                ret = STATE_IDLE;
                break;

            default:
                ret = STATE_IDLE;
        }
        return ret;
    }

    void resetTrigger() {
        doSetParam(BarCodeReader.ParamNum.PRIM_TRIG_MODE,
                BarCodeReader.ParamVal.LEVEL);
        trigMode = BarCodeReader.ParamVal.LEVEL;
    }

    private int doSetParam(int num, int val) {
        String s = "";
        int ret = bcr.setParameter(num, val);
        if (ret != BarCodeReader.BCR_ERROR) {
            if (num == BarCodeReader.ParamNum.PRIM_TRIG_MODE) {
                trigMode = val;
                if (val == BarCodeReader.ParamVal.HANDSFREE) {
                    s = "HandsFree";
                } else if (val == BarCodeReader.ParamVal.AUTO_AIM) {
                    s = "AutoAim";
                    ret = bcr
                            .startHandsFreeDecode(BarCodeReader.ParamVal.AUTO_AIM);
                    if (ret != BarCodeReader.BCR_SUCCESS) {

                    }
                } else if (val == BarCodeReader.ParamVal.LEVEL) {
                    s = "Level";
                }
            }
        } else
            s = " FAILED (" + ret + ")";


        return ret;
    }

    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_F1
                || event.getKeyCode() == KeyEvent.KEYCODE_F2
                || event.getKeyCode() == KeyEvent.KEYCODE_F6) {
            if (event.getRepeatCount() == 0
                    && event.getAction() == KeyEvent.ACTION_DOWN) {

                Intent intent = new Intent();
                intent.setAction("com.zebra.sdl.ScanerService.TRUSTCONNECT");
                intent.putExtra("text", decodeDataString); //Основной текст сообщения
                intent.putExtra("base", "");
                intent.putExtra("title", "barcode");
                sendBroadcast(intent);
            }


        }
        return super.onKeyEvent(event);
    }


    private void doDecode() {
            if (setIdle() != STATE_IDLE)
                return;

            state = STATE_DECODE;
            decCount = 0;
            decodeDataString = new String("");

            int status = bcr
                    .getNumProperty(BarCodeReader.PropertyNum.ENGINE_STATUS);
            try {
                bcr.startDecode(); // start decode (callback gets results)
            } catch (Exception e) {

            }

        }

    public void onError(int error, BarCodeReader reader) {

    }

    private void beep() {
        // if (tg != null)
        // tg.startTone(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING);

        BeepUtil.getInstance(this).beep();
    }
}


