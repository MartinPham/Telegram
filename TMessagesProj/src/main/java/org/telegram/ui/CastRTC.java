package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Outline;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.util.Consumer;
import androidx.media3.cast.CastPlayer;
import androidx.mediarouter.app.MediaRouteButton;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;

import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CastRTC {
    Context context;
    static final String CUSTOM_CHANNEL = "urn:x-cast:browsercast";
    CastContext castContext;
    SessionManager sessionManager;
    MediaRouteSelector routeSelector;
    CastPlayer castPlayer;
    PeerConnection peerConnection;
    DataChannel dataChannel;
    boolean connected;
    byte[] media;
    String mime = "video/mp4";
    String title = "video";
    int maxMessageSize = 1024;
    Consumer<String> callback;

    public CastRTC(Context ctx, Consumer<String> cb) {
        context = ctx;
        callback = cb;
    }

    public MediaRouteButton createCastButton() {
        MediaRouteButton mediaRouteButton = new MediaRouteButton(context);
        mediaRouteButton.setAlwaysVisible(true);
        routeSelector = new MediaRouteSelector.Builder()
                .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
                .addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO)
                .build();
        mediaRouteButton.setRouteSelector(routeSelector);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(AndroidUtilities.dp(60), AndroidUtilities.dp(60));
        layoutParams.gravity = Gravity.BOTTOM | Gravity.END;
        layoutParams.setMargins(0, 0, -AndroidUtilities.dp(600), -AndroidUtilities.dp(600));
        mediaRouteButton.setLayoutParams(layoutParams);
        mediaRouteButton.setBackgroundColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaRouteButton.setClipToOutline(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaRouteButton.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setOval(0, 0, view.getWidth(), view.getHeight());
                }
            });
        }

        return mediaRouteButton;
    }

    public void decorateCastButton(MediaRouteButton mediaRouteButton) {
        CastButtonFactory.setUpMediaRouteButton(context, mediaRouteButton);
    }

    public void setupCast() {
        castContext = CastContext.getSharedInstance(context);
        castPlayer = new CastPlayer(castContext);
        sessionManager = castContext.getSessionManager();

        sessionManager.addSessionManagerListener(new SessionManagerListener<CastSession>() {
            @Override
            public void onSessionStarted(CastSession session, String sessionId) {
                onSessionReady(session);
            }

            @Override
            public void onSessionStarting(@NonNull CastSession castSession) {

            }

            @Override
            public void onSessionSuspended(@NonNull CastSession castSession, int i) {

            }

            @Override
            public void onSessionEnded(CastSession session, int error) {
            }

            @Override
            public void onSessionEnding(@NonNull CastSession castSession) {

            }

            @Override
            public void onSessionResumeFailed(@NonNull CastSession castSession, int i) {

            }

            @Override
            public void onSessionResumed(CastSession session, boolean wasSuspended) {
            }

            @Override
            public void onSessionResuming(@NonNull CastSession castSession, @NonNull String s) {

            }

            @Override
            public void onSessionStartFailed(@NonNull CastSession castSession, int i) {

            }
        }, CastSession.class);
    }

    void onSessionReady(CastSession session) {
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions());
        PeerConnectionFactory factory = PeerConnectionFactory.builder().createPeerConnectionFactory();

        PeerConnection.RTCConfiguration config = new PeerConnection.RTCConfiguration(List.of(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:global.stun.twilio.com:3478").createIceServer()
        ));

        peerConnection = factory.createPeerConnection(config, new PeerConnection.Observer() {
            @Override
            public void onRenegotiationNeeded() {
                peerConnection.createOffer(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription offer) {
                        peerConnection.setLocalDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription offerDescription) {
                            }

                            @Override
                            public void onSetSuccess() {
                                try {
                                    JSONObject offerJson = new JSONObject();

                                    offerJson.put("type", offer.type.canonicalForm());
                                    offerJson.put("sdp", offer.description);

                                    sendMessage(offerJson.toString());
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onCreateFailure(String s) {

                            }

                            @Override
                            public void onSetFailure(String s) {

                            }
                        }, offer);
                    }

                    @Override
                    public void onCreateFailure(String error) {
                    }

                    @Override
                    public void onSetSuccess() {
                    }

                    @Override
                    public void onSetFailure(String error) {
                    }
                }, new MediaConstraints());
            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

            }

            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            @Override
            public void onIceCandidate(IceCandidate candidate) {
                try {
                    JSONObject candidateJson = new JSONObject();

                    candidateJson.put("candidate", candidate.sdp);
                    candidateJson.put("sdpMLineIndex", candidate.sdpMLineIndex);

                    String[] parts = candidate.sdp.split(" ");
                    for (int i = 0; i < parts.length; i++) {
                        if (parts[i].equals("ufrag")) {
                            candidateJson.put("usernameFragment", parts[i + 1]);
                        }
                    }

                    sendMessage(candidateJson.toString());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {
                handleDataChannel(dataChannel);
            }
        });

        if (peerConnection != null) {
            dataChannel = peerConnection.createDataChannel(CUSTOM_CHANNEL, new DataChannel.Init());
            handleDataChannel(dataChannel);
        } else {
        }

        try {
            session.setMessageReceivedCallbacks(
                    CUSTOM_CHANNEL,
                    new Cast.MessageReceivedCallback() {
                        @Override
                        public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
                            try {
                                JSONObject messageJson = new JSONObject((new JSONObject("{\"data\":" + message + "}")).getString("data"));

                                if (messageJson.has("candidate")) {
                                    peerConnection.addIceCandidate(new IceCandidate(
                                            messageJson.getString("sdpMid"),
                                            messageJson.getInt("sdpMLineIndex"),
                                            messageJson.getString("candidate")
                                    ));
                                } else {
                                    String type = messageJson.getString("type");
                                    String sdp = messageJson.getString("sdp");

                                    SessionDescription description = new SessionDescription(
                                            SessionDescription.Type.fromCanonicalForm(type),
                                            sdp
                                    );

                                    String[] parts = sdp.split("\r\n");
                                    for (int i = 0; i < parts.length; i++) {
                                        if (parts[i].contains("a=max-message-size:")) {
                                            String[] sizeParts = parts[i].split("a=max-message-size:");
                                            if (sizeParts.length > 1) {
                                                maxMessageSize = Integer.parseInt(sizeParts[1]);
                                            }
                                        }
                                    }

                                    peerConnection.setRemoteDescription(new SdpObserver() {
                                        @Override
                                        public void onCreateSuccess(SessionDescription sessionDescription) {
                                        }

                                        @Override
                                        public void onSetSuccess() {

                                            if (type.equals("offer")) {
                                                peerConnection.createAnswer(new SdpObserver() {
                                                    @Override
                                                    public void onCreateSuccess(SessionDescription answer) {

                                                        peerConnection.setLocalDescription(new SdpObserver() {
                                                            @Override
                                                            public void onCreateSuccess(SessionDescription answerDescription) {

                                                            }

                                                            @Override
                                                            public void onSetSuccess() {

                                                                try {
                                                                    JSONObject offerJson = new JSONObject();

                                                                    offerJson.put("type", answer.type.canonicalForm());
                                                                    offerJson.put("sdp", answer.description);

                                                                    sendMessage(offerJson.toString());
                                                                } catch (JSONException e) {
                                                                    e.printStackTrace();
                                                                }
                                                            }

                                                            @Override
                                                            public void onCreateFailure(String s) {

                                                            }

                                                            @Override
                                                            public void onSetFailure(String s) {

                                                            }
                                                        }, answer);
                                                    }

                                                    @Override
                                                    public void onSetSuccess() {

                                                    }

                                                    @Override
                                                    public void onCreateFailure(String s) {

                                                    }

                                                    @Override
                                                    public void onSetFailure(String s) {

                                                    }
                                                }, new MediaConstraints());
                                            }
                                        }

                                        @Override
                                        public void onCreateFailure(String s) {
                                        }

                                        @Override
                                        public void onSetFailure(String s) {
                                        }
                                    }, description);
                                }

                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void handleDataChannel(DataChannel channel) {
        dataChannel = channel;
        dataChannel.registerObserver(new DataChannel.Observer() {
            @Override
            public void onBufferedAmountChange(long l) {

            }

            @Override
            public void onStateChange() {
                connected = dataChannel.state() == DataChannel.State.OPEN;
                if (connected) {
                    callback.accept("OK");
                } else {
                    castContext.getSessionManager().endCurrentSession(true);
                }
            }

            @Override
            public void onMessage(DataChannel.Buffer buffer) {

                ByteBuffer byteBuffer = buffer.data;
                byte[] bytes = new byte[byteBuffer.remaining()];
                byteBuffer.get(bytes);

                String message = new String(bytes);

                try {
                    JSONObject messageJson = new JSONObject(message);

                    int msg = messageJson.getInt("msg");
                    int id = messageJson.getInt("id");

                    sendRange(msg, id);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    byte[] getRange(int start) {
        int end = Math.min(start + maxMessageSize, media.length);
        byte[] range = new byte[end - start];
        System.arraycopy(media, start, range, 0, end - start);
        return range;
    }

    void sendRange(int start, int id) {
        byte[] range = getRange(start);

        dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(String.valueOf(id).getBytes(StandardCharsets.UTF_8)), false));
        dataChannel.send(new DataChannel.Buffer(ByteBuffer.wrap(range), true));
    }


    public void playMedia(byte[] file, String fileMime, String fileTitle) {
        media = file;
        mime = fileMime;
        title = fileTitle;

        String path = "MEDIA_FILE?size=" + media.length;
        int type = MediaMetadata.MEDIA_TYPE_MOVIE;



        MediaMetadata metadata = new MediaMetadata(type);
        metadata.putString(MediaMetadata.KEY_TITLE, title);

        JSONObject customData = new JSONObject();
        try {
            JSONObject customMediaData = new JSONObject();
            customMediaData.put("uri", path);
            customMediaData.put("mediaId", path);
            customData.put("mediaItem", customMediaData);

        } catch (JSONException e) {
            e.printStackTrace();
        }

        MediaInfo mediaInfo = new MediaInfo.Builder(path)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setContentType(mime)
                .setMetadata(metadata)
                .setCustomData(customData)
                .build();

        MediaLoadRequestData loadRequestData = new MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .build();

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                CastSession session = castContext.getSessionManager().getCurrentCastSession();

                if (session != null) {
                    if (session.isConnected()) {
                        RemoteMediaClient client = session.getRemoteMediaClient();

                        if (client != null) {
                            client.load(loadRequestData);
                        } else {
                        }
                    } else {
                    }
                } else {
                }
            }
        });
    }

    void sendMessage(String message) {

        Handler mainHandler = new Handler(Looper.getMainLooper());
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                CastSession session = castContext.getSessionManager().getCurrentCastSession();

                if (session != null) {
                    session.sendMessage(CUSTOM_CHANNEL, message);
                } else {
                }
            }
        });
    }
}