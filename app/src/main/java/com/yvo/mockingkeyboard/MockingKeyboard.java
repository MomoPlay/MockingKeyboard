package com.yvo.mockingkeyboard;

import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.net.Uri;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.core.content.FileProvider;
import androidx.core.view.inputmethod.InputConnectionCompat;
import androidx.core.view.inputmethod.InputContentInfoCompat;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Policy;
import java.util.Random;

import static android.content.ContentValues.TAG;

public class MockingKeyboard extends InputMethodService implements CustomKeyboardView.OnKeyboardActionListener {

    private CustomKeyboardView kv;
    private Keyboard keyboard;
    private File bobFile;
    private SharedPreferences settings;

    private boolean isCaps = false;

    @Override
    public View onCreateInputView() {
        //InputMethodManager.setAdditionalInputMethodSubtype()

        //Import keyboard layout
        InputMethodManager imeManager = (InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);
        InputMethodSubtype subtype = null;
        if (imeManager != null) {
            subtype = imeManager.getCurrentInputMethodSubtype();
            switch(subtype.getLocale()) {
                case "fr_FR":
                    //link Keys Layout
                    kv = (CustomKeyboardViewFr)getLayoutInflater().inflate(R.layout.keyboard_fr, null);
                    keyboard = new Keyboard(this, R.xml.azerty);
                    break;
                default:
                    kv = (CustomKeyboardView)getLayoutInflater().inflate(R.layout.keyboard, null);
                    //link Keys Layout
                    keyboard = new Keyboard(this, R.xml.qwerty);
                    break;
            }
            kv.setKeyboard(keyboard);
        }

        kv.setOnKeyboardActionListener(this);

        settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        return kv;
    }

    @Override
    public void onPress(int primaryCode) {
        if (settings.getBoolean("keyPress", true))
            vibrateClick(primaryCode);
    }

    @Override
    public void onRelease(int primaryCode) {

    }

    @Override
    public void onKey(int primaryCode, int[] keyCodes) {

        //Mocking system (basically poorly written code)

        InputConnection ic = getCurrentInputConnection();
        switch (settings.getString("mockingMode", "alternating"))
        {
            case "alternating":
                if (ic.getTextBeforeCursor(1, 0).length() != 0)
                {
                    if (Character.isUpperCase(ic.getTextBeforeCursor(1, 0).charAt(0)))
                    {
                        isCaps = true;
                        endToMocking();
                        isCaps = false;
                    }
                    else if (Character.isLowerCase(ic.getTextBeforeCursor(1, 0).charAt(0)))
                    {
                        isCaps = false;
                        endToMocking();
                        isCaps = true;
                    }
                }
                else if (ic.getTextAfterCursor(1, 0).length() != 0)
                {
                    if (Character.isUpperCase(ic.getTextAfterCursor(1, 0).charAt(0)))
                    {
                        isCaps = false;
                    }
                    else if (Character.isLowerCase(ic.getTextAfterCursor(1, 0).charAt(0)))
                    {
                        isCaps = true;
                    }
                }

                keyboard.setShifted(!isCaps);
                break;
            case "random":
                Random rd = new Random();
                isCaps = rd.nextBoolean();
                break;
        }

        keyboard.setShifted(!isCaps);
        kv.invalidateAllKeys();

        switch (primaryCode)
        {
            case Keyboard.KEYCODE_DELETE:
                ic.deleteSurroundingText(1,0);
                break;
            case Keyboard.KEYCODE_SHIFT:
                isCaps = !isCaps;
                keyboard.setShifted(isCaps);
                kv.invalidateAllKeys();
                break;
            case Keyboard.KEYCODE_DONE:
                ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
                break;
            case 47:
                //Send the bob image TODO Put in it's own method
                Uri bob = FileProvider.getUriForFile(this, "com.yvo.mockingkeyboard.fileprovider", bobFile);
                InputConnection inputConnection = getCurrentInputConnection();
                EditorInfo editorInfo = getCurrentInputEditorInfo();
                final int flag;
                if (Build.VERSION.SDK_INT >= 25) {
                    flag = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION;
                } else {
                    flag = 0;
                    try {
                        grantUriPermission(
                                editorInfo.packageName, bob, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    } catch (Exception e){
                        Log.e(TAG, "grantUriPermission failed packageName=" + editorInfo.packageName
                                + " contentUri=" + bob, e);
                    }
                }
                InputContentInfoCompat inputContentInfo = new InputContentInfoCompat(
                        bob,
                        new ClipDescription("mockingbob", new String[]{"image/png"}),
                        null
                );
                InputConnectionCompat.commitContent(
                        inputConnection, editorInfo, inputContentInfo, flag, null);
                break;
            case 35:
                //Button to switch android keyboard
                InputMethodManager imeManager = (InputMethodManager) getApplicationContext().getSystemService(INPUT_METHOD_SERVICE);
                if (imeManager != null) {
                    imeManager.showInputMethodPicker();
                }
                break;
            case 18:
                //Entrer dans les options de l'app
                Intent paramIntent = new Intent(this, PreferencesActivity.class);
                //Entrer dans les options de l'app dans les settings androidandroid
                //Intent paramIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                //Uri uri = Uri.fromParts("package", getPackageName(), null);
                //paramIntent.setData(uri);
                paramIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(paramIntent);
//                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//                    switchToNextInputMethod(true);
//                }
                break;
            case 301:
                //Generate from Clipboard
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (clipboard != null) {
                    ic.commitText(toMocking(clipboard.getPrimaryClip().getItemAt(0).getText().toString()), 1);
                }
                break;
            default:
                char code = (char)primaryCode;
                if(Character.isLetter(code) && isCaps)
                    code = Character.toUpperCase(code);
                ic.commitText(String.valueOf(code),1);
        }
    }

    private void endToMocking() {

        InputConnection ic = getCurrentInputConnection();
        CharSequence afterCursor = ic.getTextAfterCursor(100, 0);
        String newAfterCursor;

        newAfterCursor = toMocking(afterCursor.toString());

        ic.deleteSurroundingText(0, newAfterCursor.length());
        ic.commitText(newAfterCursor,1);
        int cursorPlace = ic.getTextBeforeCursor(100, 0).length()-newAfterCursor.length();
        ic.setSelection(cursorPlace, cursorPlace);
    }

    private void vibrateClick(int i) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        // Vibrate for 500 milliseconds
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (v != null) {
                v.vibrate(VibrationEffect.createOneShot(40, 1));
            }
        } else {
            //deprecated in API 26
            if (v != null) {
                v.vibrate(40);
            }
        }
    }

    private String toMocking(String textToMock)
    {
        StringBuilder mockedText =  new StringBuilder();

        for (char c : textToMock.toCharArray())
        {
            if (isCaps)
                mockedText.append(Character.toUpperCase(c));
            else
                mockedText.append(Character.toLowerCase(c));

            isCaps = !isCaps;
        }

        return mockedText.toString();
    }

    @Override
    public void onText(CharSequence text) {

    }

    @Override
    public void swipeLeft() {

    }

    @Override
    public void swipeRight() {

    }

    @Override
    public void swipeDown() {

    }

    @Override
    public void swipeUp() {

    }

    //Creating a content provider to be able to get a local image as a content scheme in the URI

    @Override
    public void onCreate() {
        super.onCreate();

        final File imagesDir = new File(getFilesDir(), "images/");
        imagesDir.mkdirs();
        bobFile = getFileForResource(this, R.raw.mockingbob, imagesDir, "mockingbob.png");
    }

    private static File getFileForResource(
            @NonNull Context context, @RawRes int res, @NonNull File outputDir,
            @NonNull String filename) {
        final File outputFile = new File(outputDir, filename);
        final byte[] buffer = new byte[4096];
        InputStream resourceReader = null;
        try {
            try {
                resourceReader = context.getResources().openRawResource(res);
                OutputStream dataWriter = null;
                try {
                    dataWriter = new FileOutputStream(outputFile);
                    while (true) {
                        final int numRead = resourceReader.read(buffer);
                        if (numRead <= 0) {
                            break;
                        }
                        dataWriter.write(buffer, 0, numRead);
                    }
                    return outputFile;
                } finally {
                    if (dataWriter != null) {
                        dataWriter.flush();
                        dataWriter.close();
                    }
                }
            } finally {
                if (resourceReader != null) {
                    resourceReader.close();
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    //Changing the keyboard

    @Override
    protected void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) {
        super.onCurrentInputMethodSubtypeChanged(newSubtype);
        setInputView(onCreateInputView());
    }
}
