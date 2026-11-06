package com.kushal.deskclock;

import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import java.util.List;

/**
 * Notification Listener Service that monitors active media sessions.
 * This allows the app to detect Spotify playback even when the music
 * is being controlled/cast from another device, by reading the
 * MediaSession metadata from Spotify's persistent notification.
 *
 * The service polls active media sessions every 5 seconds and broadcasts
 * track changes to the app via a sticky intent.
 */
public class SpotifyNotificationListener extends NotificationListenerService {

    private static final String TAG = "SpotifyNotifListener";

    private static MetadataCallback metadataCallback;
    private String lastTrack = "";
    private String lastArtist = "";
    public static android.graphics.Bitmap currentAlbumArt = null;

    private MediaSessionManager mediaSessionManager;
    private MediaController activeSpotifyController;
    private android.content.ComponentName componentName;

    // Static listener for direct callback (avoids broadcast overhead)
    public interface MetadataCallback {
        void onMetadataChanged(String track, String artist, String album, boolean isPlaying, android.graphics.Bitmap albumArt);
    }

    public static void setMetadataCallback(MetadataCallback callback) {
        metadataCallback = callback;
    }

    private final MediaController.Callback controllerCallback = new MediaController.Callback() {
        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            handleMetadata(metadata);
        }

        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            if (activeSpotifyController != null) {
                handleMetadata(activeSpotifyController.getMetadata());
            }
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessionListener = 
            controllers -> findAndRegisterSpotifyController(controllers);

    private android.media.browse.MediaBrowser mediaBrowser;
    
    private final android.media.browse.MediaBrowser.ConnectionCallback connectionCallback = 
            new android.media.browse.MediaBrowser.ConnectionCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "MediaBrowser connected to Spotify!");
        }
        @Override
        public void onConnectionSuspended() {
            Log.d(TAG, "MediaBrowser suspended - attempting to reconnect to wake Spotify up...");
            retryBrowserConnection();
        }
        @Override
        public void onConnectionFailed() {
            Log.d(TAG, "MediaBrowser connection failed - will retry later");
            new Handler(Looper.getMainLooper()).postDelayed(() -> retryBrowserConnection(), 10000);
        }
    };
    
    private void retryBrowserConnection() {
        if (mediaBrowser != null) {
            try {
                mediaBrowser.disconnect();
                mediaBrowser.connect();
            } catch (Exception e) {
                Log.e(TAG, "Failed to reconnect MediaBrowser", e);
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSessionManager = (MediaSessionManager) getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
        componentName = new android.content.ComponentName(this, SpotifyNotificationListener.class);
        
        // Wake up Spotify in the background by connecting to its MediaBrowserService
        try {
            android.content.ComponentName spotifyComponent = new android.content.ComponentName(
                    "com.spotify.music", "com.spotify.mobile.android.spotlets.appprotocol.service.AppProtocolAuthenticatorService");
            // Wait, Spotify's MediaBrowserService is usually just exported as MediaBrowserService.
            // A safer intent is to use the standard MediaBrowserService action.
        } catch (Exception e) {}
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "Notification listener connected - registering for session changes");
        
        if (mediaSessionManager != null) {
            try {
                mediaSessionManager.addOnActiveSessionsChangedListener(sessionListener, componentName);
                findAndRegisterSpotifyController(mediaSessionManager.getActiveSessions(componentName));
            } catch (SecurityException e) {
                Log.w(TAG, "No permission to listen to media sessions", e);
            }
        }

        try {
            // Dynamically find Spotify's actual MediaBrowserService
            android.content.Intent intent = new android.content.Intent(android.service.media.MediaBrowserService.SERVICE_INTERFACE);
            intent.setPackage("com.spotify.music");
            java.util.List<android.content.pm.ResolveInfo> resolveInfos = getPackageManager().queryIntentServices(intent, 0);
            
            if (resolveInfos != null && !resolveInfos.isEmpty()) {
                android.content.pm.ServiceInfo serviceInfo = resolveInfos.get(0).serviceInfo;
                android.content.ComponentName spotifyBrowserComponent = new android.content.ComponentName(serviceInfo.packageName, serviceInfo.name);
                
                // Keep Spotify alive using MediaBrowser so it can receive Spotify Connect events in the background!
                mediaBrowser = new android.media.browse.MediaBrowser(this, spotifyBrowserComponent, connectionCallback, null);
                mediaBrowser.connect();
                Log.d(TAG, "Attempting to connect to Spotify MediaBrowserService: " + serviceInfo.name);
            } else {
                Log.w(TAG, "Could not find Spotify MediaBrowserService on this device.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error setting up MediaBrowser", e);
        }
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Notification listener disconnected");
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener);
        }
        unregisterCurrentController();
        if (mediaBrowser != null) {
            mediaBrowser.disconnect();
        }
    }

    private void findAndRegisterSpotifyController(List<MediaController> controllers) {
        if (controllers == null) return;
        
        MediaController foundController = null;
        for (MediaController controller : controllers) {
            String pkg = controller.getPackageName();
            if (pkg != null && pkg.contains("spotify")) {
                foundController = controller;
                break;
            }
        }

        if (foundController != null) {
            if (activeSpotifyController == null || 
                !activeSpotifyController.getSessionToken().equals(foundController.getSessionToken())) {
                
                unregisterCurrentController();
                activeSpotifyController = foundController;
                activeSpotifyController.registerCallback(controllerCallback);
                
                // Trigger an initial update
                handleMetadata(activeSpotifyController.getMetadata());
            }
        } else {
            unregisterCurrentController();
        }
    }

    private void unregisterCurrentController() {
        if (activeSpotifyController != null) {
            activeSpotifyController.unregisterCallback(controllerCallback);
            activeSpotifyController = null;
        }
    }

    private void handleMetadata(MediaMetadata metadata) {
        if (metadata == null) return;

        String track = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
        String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
        String album = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM);
        
        boolean isPlaying = false;
        if (activeSpotifyController != null && activeSpotifyController.getPlaybackState() != null) {
            isPlaying = activeSpotifyController.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;
        }

        if (track == null) track = "";
        if (artist == null) artist = "";
        if (album == null) album = "";

        android.graphics.Bitmap art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (art == null) art = metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
        
        // Check if anything changed to avoid spamming updates
        if (!track.equals(lastTrack) || !artist.equals(lastArtist) || art != currentAlbumArt) {
            lastTrack = track;
            lastArtist = artist;
            currentAlbumArt = art;

            Log.d(TAG, "Media session update: " + track + " by " + artist);

            if (metadataCallback != null) {
                String finalTrack = track;
                String finalArtist = artist;
                String finalAlbum = album;
                boolean finalIsPlaying = isPlaying;
                android.graphics.Bitmap finalArt = art;
                new Handler(Looper.getMainLooper()).post(() ->
                        metadataCallback.onMetadataChanged(
                                finalTrack, finalArtist, finalAlbum, finalIsPlaying, finalArt));
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener);
        }
        unregisterCurrentController();
        if (mediaBrowser != null) {
            mediaBrowser.disconnect();
        }
    }
}
