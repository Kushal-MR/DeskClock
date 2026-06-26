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
        void onMetadataChanged(String track, String artist, String album, boolean isPlaying);
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

    @Override
    public void onCreate() {
        super.onCreate();
        mediaSessionManager = (MediaSessionManager) getSystemService(android.content.Context.MEDIA_SESSION_SERVICE);
        componentName = new android.content.ComponentName(this, SpotifyNotificationListener.class);
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
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        Log.d(TAG, "Notification listener disconnected");
        if (mediaSessionManager != null) {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionListener);
        }
        unregisterCurrentController();
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
                new Handler(Looper.getMainLooper()).post(() ->
                        metadataCallback.onMetadataChanged(
                                finalTrack, finalArtist, finalAlbum, finalIsPlaying));
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
    }
}
