package stt.icasa.michael.icasa_stt_commander;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A fragment with a Google +1 button.
 */
public class WatsonTTSFragment extends Fragment implements ISpeechDelegate {
    private static final String TAG = "MainActivity";

    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED
    }

    ConnectionState mState = ConnectionState.IDLE;
    public View mView = null;
    public Context mContext = null;
    private Handler mHandler = null;

    private RestClient rClient = new RestClient();

    public WatsonTTSFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        mView = inflater.inflate(R.layout.fragment_watson_tt, container, false);
        mContext = getActivity().getApplicationContext();
        mHandler = new Handler();

        if (initSTT() == false) {
            Log.i("Error", "no authentication credentials/token available, please enter your authentication information");
            return mView;
        }

        setButtonLabel(R.id.angry_btn, "Start listening");

        Button buttonRecord = (Button)mView.findViewById(R.id.angry_btn);
        buttonRecord.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if (mState == ConnectionState.IDLE) {
                    mState = ConnectionState.CONNECTING;
                    Log.d(TAG, "onClickRecord: IDLE -> CONNECTING");

                    SpeechToText.sharedInstance().setModel("en-US_BroadbandModel");

                    // start recognition
                    new AsyncTask<Void, Void, Void>(){
                        @Override
                        protected Void doInBackground(Void... none) {
                            SpeechToText.sharedInstance().recognize();
                            return null;
                        }
                    }.execute();
                    setButtonLabel(R.id.angry_btn, "Connecting...");
                    setButtonState(true);
                }
                else if (mState == ConnectionState.CONNECTED) {
                    mState = ConnectionState.IDLE;
                    Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");

                    SpeechToText.sharedInstance().stopRecognition();
                    setButtonLabel(R.id.angry_btn, "Start listening");
                    setButtonState(false);
                }
            }
        });


        return mView;
    }

    public void setButtonLabel(final int buttonId, final String label) {
        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                Button button = (Button)mView.findViewById(buttonId);
                button.setText(label);
            }
        };
        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }


    public void setButtonState(final boolean bRecording) {

        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                //int iDrawable = bRecording ? R.drawable.button_record_stop : R.drawable.button_record_start;
                //Button btnRecord = (Button)mView.findViewById(R.id.buttonRecord);
                //btnRecord.setBackground(getResources().getDrawable(iDrawable));
            }
        };
        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }

    private boolean initSTT() {

        // DISCLAIMER: please enter your credentials or token factory in the lines below
        String username = getString(R.string.STTUsername);
        String password = getString(R.string.STTPassword);
        String tokenFactoryURL = getString(R.string.defaultTokenFactory);
        String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";
        SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS);
        SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), getActivity().getApplicationContext(), sConfig);
        // Basic Authentication
        SpeechToText.sharedInstance().setCredentials(username, password);
        SpeechToText.sharedInstance().setModel(getString(R.string.modelDefault));
        SpeechToText.sharedInstance().setDelegate(this);
        return true;
    }

    public URI getHost(String url){
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    // delegages ----------------------------------------------

    public void onOpen() {
        Log.d(TAG, "onOpen");
        setButtonLabel(R.id.angry_btn, "Stop listening");
        mState = ConnectionState.CONNECTED;
    }

    public void onError(String error) {
        Log.e(TAG, error);
        mState = ConnectionState.IDLE;
    }

    public void onClose(int code, String reason, boolean remote) {
        Log.d(TAG, "onClose, code: " + code + " reason: " + reason);

        setButtonLabel(R.id.angry_btn, "Start listening");
        mState = ConnectionState.IDLE;
    }


    public void onMessage(String message) {
        try {
            JSONObject jObj = new JSONObject(message);
            // state message
            if(jObj.has("state")) {
                Log.d(TAG, "Status message: " + jObj.getString("state"));
            }
            // results message
            else if (jObj.has("results")) {
                //if has result
                //Log.d(TAG, "Results message: ");
                JSONArray jArr = jObj.getJSONArray("results");
                for (int i=0; i < jArr.length(); i++) {
                    JSONObject obj = jArr.getJSONObject(i);
                    JSONArray jArr1 = obj.getJSONArray("alternatives");
                    String str = jArr1.getJSONObject(0).getString("transcript");
                    // remove whitespaces if the language requires it

                    String strFormatted = Character.toUpperCase(str.charAt(0)) + str.substring(1);
                    if (obj.getString("final").equals("true")) {

                        String command = strFormatted.substring(0,strFormatted.length()-1) + ". ";
                        Log.i(TAG, command);

                        // Stop listening
                        mState = ConnectionState.IDLE;
                        Log.d(TAG, "onClickRecord: CONNECTED -> IDLE");

                        SpeechToText.sharedInstance().stopRecognition();
                        setButtonLabel(R.id.angry_btn, "Start listening");
                        setButtonState(false);

                        try {
                            String response = rClient.sendPost("http://watson-demo-md.eu-gb.mybluemix.net/watson/command", command);
                            Log.i("RESPONSE", response);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } /*else {
                        Log.e(TAG, mRecognitionResults + strFormatted);
                    }*/
                    break;
                }
            } else {
                Log.e(TAG, "unexpected data coming from stt server: \n" + message);
            }

        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON");
            e.printStackTrace();
        }
    }

    public void onAmplitude(double amplitude, double volume) {
        //Logger.e(TAG, "amplitude=" + amplitude + ", volume=" + volume);
    }

    @Override
    public void onResume() {
        super.onResume();
    }
}
