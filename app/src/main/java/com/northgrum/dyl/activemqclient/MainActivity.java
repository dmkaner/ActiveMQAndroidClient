package com.northgrum.dyl.activemqclient;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONObject;

import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "ActiveMQ";
    public static final String clientId = "any_client_name";
    public static String serverURI = "tcp://192.168.42.179:1883"; //temp ip
    public static final String publishTopic = "RequestLine";
    public static final String subscribeTopic = "ResponseLine";
    public static final String CHANNEL_ID = "ChannelID";
    public int counter = 0;

    MqttAndroidClient client;

    private Button publishBtn;
    private Button listenBtn;
    private EditText ipEt;
    private TextView incomingText;
    private TextToSpeech t1;
    private ImageView recievedImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        createNotificationChannel();
    }

    private void initViews() {
        publishBtn = (Button) findViewById(R.id.publish);
        listenBtn = (Button) findViewById(R.id.ipbtn);
        ipEt = (EditText) findViewById(R.id.ipinput);
        incomingText = (TextView) findViewById(R.id.IncomingText);
        recievedImage = (ImageView) findViewById(R.id.recievedImage);

        listenBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                serverURI = "tcp://" + ipEt.getText().toString() + ":1883";
                connect();
            }
        });

        publishBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                publishMessage("Description Requested");
            }
        });

        t1 = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(Locale.US);
                }
            }
        });

        publishBtn.setEnabled(false);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Threat";
            String description = "Threat Detected";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void connect() {
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setAutomaticReconnect(true);

        client = new MqttAndroidClient(this, serverURI, clientId);
        try {
            client.connect(connectOptions, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    publishBtn.setEnabled(true);
                    subscribe();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable e) {
                    e.printStackTrace();
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void subscribe() {
        try {
            client.subscribe(subscribeTopic, 0, new IMqttMessageListener() {
                @Override
                public void messageArrived(final String topic, final MqttMessage message) throws Exception {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String caption;
                            String data;
                            try {
                                JSONObject reader = new JSONObject(message.toString());
                                caption = reader.getString("Caption");
                                data = reader.getString("Data");
                            } catch (Exception e) {
                                caption = "Caption Error";
                                data = null;
                                System.out.println("Error with JSON");
                            }

                            incomingText.setText(caption);
                            t1.speak(caption, TextToSpeech.QUEUE_FLUSH, null);

                            byte[] decodedString = Base64.decode(data, Base64.DEFAULT);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            recievedImage.setImageBitmap(decodedByte);


//                            Intent intent = new Intent(getApplicationContext(), AlertDialog.class);
//                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                            PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
//
//                            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
//                                    .setSmallIcon(R.drawable.googleg_standard_color_18)
//                                    .setContentTitle("POSSIBLE THREAT")
//                                    .setContentText("Possible Threat Detected")
//                                    .setPriority(NotificationCompat.PRIORITY_MAX)
//                                    .setContentIntent(pendingIntent)
//                                    .setAutoCancel(true);
//
//                            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
//                            notificationManager.notify(getId(), builder.build());


                        }
                    });
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private int getId() {
        counter ++;
        return counter;
    }


    private void publishMessage(String message) {
        MqttMessage msg = new MqttMessage();
        msg.setPayload(message.getBytes());
        try {
            client.publish(publishTopic, msg);
        } catch (MqttException e) {
            e.printStackTrace();

        }
    }

    public void onPause(){
        if(t1 !=null){
            t1.stop();
            t1.shutdown();
        }

        try {
            client.unsubscribe(subscribeTopic);
        } catch (Exception e) {
            System.out.println("Could not unsubscribe from topic");
        }

        super.onPause();
    }
}

